package com.company.platform.query;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.ForbiddenException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DynamicDataSourceManager;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryService {
    private static final int MAX_IN_MEMORY_RESULTS = 1000;
    private final SqlSafetyChecker safetyChecker;
    private final PlatformProperties properties;
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;
    private final RunningStatementRegistry statementRegistry;
    private final PlatformStore store;
    private final Map<String, String> executions = new ConcurrentHashMap<>();
    private final Map<String, String> owners = new ConcurrentHashMap<>();
    private final Map<String, QueryResult> results = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> resultOrder = new ConcurrentLinkedDeque<>();
    private final ThreadPoolExecutor queryExecutor;

    public QueryService(SqlSafetyChecker safetyChecker, PlatformProperties properties, DataSourceService dataSources,
                        DynamicDataSourceManager connectionManager, RunningStatementRegistry statementRegistry, PlatformStore store) {
        this.safetyChecker = safetyChecker; this.properties = properties; this.dataSources = dataSources;
        this.connectionManager = connectionManager; this.statementRegistry = statementRegistry; this.store = store;
        int concurrency = Math.max(1, Math.min(64, properties.getQuery().getMaxConcurrentQueries()));
        this.queryExecutor = new ThreadPoolExecutor(concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(8, concurrency * 4)), Thread.ofPlatform().name("platform-query-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public QueryResult execute(String sql, boolean selected) { return execute(sql, selected, null, null); }
    public QueryResult execute(String sql, boolean selected, Long dataSourceId, String databaseName) {
        return execute(sql, selected, dataSourceId, databaseName, "admin");
    }
    public QueryResult execute(String sql, boolean selected, Long dataSourceId, String databaseName, String operator) {
        SqlSafetyChecker.CheckResult check = safetyChecker.check(sql);
        if (!check.safe()) throw new BadRequestException(check.message());
        if (dataSourceId != null) return executeJdbc(UUID.randomUUID().toString(), sql, selected, dataSourceId, databaseName, operator);
        throw new BadRequestException("请选择 StarRocks 数据源后执行 SQL");
    }

    public QueryHandle submit(String sql, boolean selected, Long dataSourceId, String databaseName) {
        return submit(sql, selected, dataSourceId, databaseName, "admin");
    }
    public QueryHandle submit(String sql, boolean selected, Long dataSourceId, String databaseName, String operator) {
        SqlSafetyChecker.CheckResult check = safetyChecker.check(sql);
        if (!check.safe()) throw new BadRequestException(check.message());
        if (dataSourceId == null) throw new BadRequestException("请选择 StarRocks 数据源后执行 SQL");
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.STARROCKS) throw new BadRequestException("数据探查 SQL 仅允许在 StarRocks 数据源执行");
        String executionId = UUID.randomUUID().toString();
        String owner = normalizeOperator(operator);
        executions.put(executionId, "RUNNING");
        owners.put(executionId, owner);
        try {
            queryExecutor.execute(() -> {
                try { executeJdbc(executionId, sql, selected, dataSourceId, databaseName, owner); }
                catch (RuntimeException ignored) { }
            });
        } catch (RejectedExecutionException ex) {
            executions.remove(executionId); owners.remove(executionId);
            throw new BadRequestException("当前并发查询较多，请稍后重试");
        }
        return new QueryHandle(executionId, "RUNNING");
    }

    public QueryResult status(String executionId) { return status(executionId, "admin", true); }
    public QueryResult status(String executionId, String operator, boolean administrator) {
        requireOwnership(executionId, operator, administrator);
        QueryResult result = results.get(executionId);
        if (result != null) return result;
        String status = executions.get(executionId);
        if (status == null) throw new BadRequestException("查询任务不存在或结果已过期：" + executionId);
        return new QueryResult(executionId, status, List.of(), List.of(), 0, false, null, 0L,
                properties.getQuery().getDefaultMaxRows(), null, Map.of());
    }

    public List<QueryHistoryView> history() { return store.queryHistory(); }
    public List<QueryHistoryView> history(String operator, boolean administrator) {
        List<QueryHistoryView> history = store.queryHistory();
        if (administrator) return history;
        String owner = normalizeOperator(operator);
        return history.stream().filter(item -> owner.equalsIgnoreCase(item.username())).toList();
    }

    private QueryResult executeJdbc(String executionId, String sql, boolean selected, long dataSourceId,
                                    String databaseName, String operator) {
        LocalDateTime startedAt = LocalDateTime.now();
        executions.put(executionId, "RUNNING");
        try {
            DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
            if (info.type() != DataSourceType.STARROCKS) throw new BadRequestException("数据探查 SQL 仅允许在 StarRocks 数据源执行");
            try (Connection connection = connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
                 Statement statement = connection.createStatement()) {
                if (databaseName != null && !databaseName.isBlank()) {
                    if (!databaseName.matches("[A-Za-z0-9_]+")) throw new BadRequestException("数据库名称格式不合法");
                    connection.setCatalog(databaseName);
                    statement.execute("USE `" + databaseName + "`");
                }
                statement.setMaxRows(properties.getQuery().getDefaultMaxRows());
                statement.setQueryTimeout(properties.getQuery().getTimeoutSeconds());
                statementRegistry.register(executionId, statement);
                boolean hasResult = statement.execute(sql);
                List<String> columns = new ArrayList<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                if (hasResult) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        ResultSetMetaData meta = resultSet.getMetaData();
                        for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i));
                        while (resultSet.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= meta.getColumnCount(); i++) row.put(columns.get(i - 1), resultSet.getObject(i));
                            rows.add(row);
                        }
                    }
                }
                Map<String, String> columnComments = loadColumnComments(connection, databaseName, sql, columns);
                String status = "CANCELED".equals(executions.get(executionId)) ? "CANCELED" : "SUCCESS";
                LocalDateTime finishedAt = LocalDateTime.now();
                long elapsedMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
                QueryResult result = new QueryResult(executionId, status, columns, rows, rows.size(), selected, finishedAt, elapsedMs,
                        properties.getQuery().getDefaultMaxRows(), null, columnComments);
                storeResult(result);
                store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, status, operator, startedAt, finishedAt, elapsedMs, null);
                return result;
            }
        } catch (SQLException ex) {
            return handleSqlFailure(executionId, sql, selected, dataSourceId, databaseName, operator, startedAt, ex);
        } catch (RuntimeException ex) {
            if (!results.containsKey(executionId)) recordRuntimeFailure(executionId, sql, selected, dataSourceId, databaseName, operator, startedAt, ex);
            throw ex;
        } finally {
            executions.remove(executionId);
            statementRegistry.remove(executionId);
        }
    }

    private QueryResult handleSqlFailure(String executionId, String sql, boolean selected, long dataSourceId, String databaseName,
                                         String operator, LocalDateTime startedAt, SQLException ex) {
        String status = "CANCELED".equals(executions.get(executionId)) ? "CANCELED" : "FAILED";
        LocalDateTime finishedAt = LocalDateTime.now();
        long elapsedMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        QueryResult terminal = new QueryResult(executionId, status, List.of(), List.of(), 0, selected, finishedAt, elapsedMs,
                properties.getQuery().getDefaultMaxRows(), ex.getMessage(), Map.of());
        storeResult(terminal);
        store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, status, operator, startedAt, finishedAt, elapsedMs, ex.getMessage());
        if ("CANCELED".equals(status)) return terminal;
        throw new BadRequestException("SQL 执行失败：" + ex.getMessage());
    }

    private void recordRuntimeFailure(String executionId, String sql, boolean selected, long dataSourceId, String databaseName,
                                      String operator, LocalDateTime startedAt, RuntimeException ex) {
        LocalDateTime finishedAt = LocalDateTime.now();
        long elapsedMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        String message = ex.getMessage() == null ? "查询执行失败" : ex.getMessage();
        QueryResult terminal = new QueryResult(executionId, "FAILED", List.of(), List.of(), 0, selected, finishedAt, elapsedMs,
                properties.getQuery().getDefaultMaxRows(), message, Map.of());
        storeResult(terminal);
        store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, "FAILED", operator, startedAt, finishedAt, elapsedMs, message);
    }

    private void storeResult(QueryResult result) {
        results.put(result.executionId(), result);
        resultOrder.remove(result.executionId());
        resultOrder.addLast(result.executionId());
        while (resultOrder.size() > MAX_IN_MEMORY_RESULTS) {
            String expired = resultOrder.pollFirst();
            if (expired != null) { results.remove(expired); owners.remove(expired); }
        }
    }

    public void cancel(String id) { cancel(id, "admin", true); }
    public void cancel(String id, String operator, boolean administrator) {
        requireOwnership(id, operator, administrator);
        if (!executions.containsKey(id) && !results.containsKey(id)) throw new BadRequestException("查询任务不存在：" + id);
        if (results.containsKey(id)) throw new BadRequestException("查询任务已结束，不能再次停止");
        executions.put(id, "CANCELED");
        if (!statementRegistry.cancel(id)) throw new BadRequestException("查询停止请求发送失败");
    }

    private void requireOwnership(String executionId, String operator, boolean administrator) {
        if (administrator) return;
        String owner = owners.get(executionId);
        if (owner == null && !executions.containsKey(executionId) && !results.containsKey(executionId)) return;
        if (owner == null || !owner.equalsIgnoreCase(normalizeOperator(operator))) throw new ForbiddenException("无权访问其他用户的查询任务");
    }

    private Map<String, String> loadColumnComments(Connection connection, String databaseName, String sql, List<String> columns) {
        String table = extractTableName(sql);
        if (table == null || columns.isEmpty()) return Map.of();
        Map<String, String> comments = new LinkedHashMap<>();
        try (ResultSet metadata = connection.getMetaData().getColumns(databaseName, null, table, "%")) {
            while (metadata.next()) {
                String name = metadata.getString("COLUMN_NAME"); String remarks = metadata.getString("REMARKS");
                if (name != null && remarks != null && !remarks.isBlank()) comments.put(name, remarks);
            }
        } catch (SQLException ignored) { return Map.of(); }
        return comments;
    }

    private String extractTableName(String sql) {
        Matcher matcher = Pattern.compile("(?i)\\bfrom\\s+([a-zA-Z0-9_$.`]+)").matcher(sql == null ? "" : sql);
        if (!matcher.find()) return null;
        String raw = matcher.group(1).replace("`", "");
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }
    private String normalizeOperator(String operator) { return operator == null || operator.isBlank() ? "admin" : operator.trim(); }

    @PreDestroy public void shutdown() { queryExecutor.shutdownNow(); }

    public record QueryHandle(String executionId, String status) { }
    public record QueryResult(String executionId, String status, List<String> columns, List<Map<String, Object>> rows,
                              int rowCount, boolean selectedOnly, LocalDateTime finishedAt, long elapsedMs, int maxRows,
                              String errorMessage, Map<String, String> columnComments) { }
}
