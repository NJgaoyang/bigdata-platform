package com.company.platform.query;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DynamicDataSourceManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PreDestroy;

@Service
public class QueryService {
    private final SqlSafetyChecker safetyChecker;
    private final PlatformProperties properties;
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;
    private final RunningStatementRegistry statementRegistry;
    private final PlatformStore store;
    private final Map<String, String> executions = new ConcurrentHashMap<>();
    private final Map<String, QueryResult> results = new ConcurrentHashMap<>();
    private final ExecutorService queryExecutor = Executors.newCachedThreadPool();
    public QueryService(SqlSafetyChecker safetyChecker, PlatformProperties properties,
                        DataSourceService dataSources, DynamicDataSourceManager connectionManager,
                        RunningStatementRegistry statementRegistry, PlatformStore store) {
        this.safetyChecker = safetyChecker; this.properties = properties;
        this.dataSources = dataSources; this.connectionManager = connectionManager;
        this.statementRegistry = statementRegistry; this.store = store;
    }
    public QueryResult execute(String sql, boolean selected) {
        return execute(sql, selected, null, null);
    }

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
        SqlSafetyChecker.CheckResult check = safetyChecker.check(sql);
        if (!check.safe()) throw new BadRequestException(check.message());
        if (dataSourceId == null) {
            QueryResult result = execute(sql, selected, null, null);
            results.put(result.executionId(), result);
            return new QueryHandle(result.executionId(), result.status());
        }
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.STARROCKS) {
            throw new BadRequestException("数据探查 SQL 仅允许在 StarRocks 数据源执行");
        }
        String executionId = UUID.randomUUID().toString();
        executions.put(executionId, "RUNNING");
        queryExecutor.submit(() -> {
            try { results.put(executionId, executeJdbc(executionId, sql, selected, dataSourceId, databaseName, "admin")); }
            catch (RuntimeException ignored) { /* status and audit record are persisted by executeJdbc */ }
        });
        return new QueryHandle(executionId, "RUNNING");
    }

    public QueryResult status(String executionId) {
        QueryResult result = results.get(executionId);
        if (result != null) return result;
        String status = executions.get(executionId);
        if (status == null) throw new BadRequestException("查询任务不存在：" + executionId);
        return new QueryResult(executionId, status, List.of(), List.of(), 0, false, null, 0L, properties.getQuery().getDefaultMaxRows(), null, Map.of());
    }

    public List<QueryHistoryView> history() { return store.queryHistory(); }

    private QueryResult executeJdbc(String executionId, String sql, boolean selected, long dataSourceId, String databaseName, String operator) {
        LocalDateTime startedAt = LocalDateTime.now();
        executions.put(executionId, "RUNNING");
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.STARROCKS) {
            throw new BadRequestException("数据探查 SQL 仅允许在 StarRocks 数据源执行");
        }
        String jdbcUrl = info.jdbcUrl();
        if (databaseName != null && !databaseName.isBlank() && (jdbcUrl.endsWith("/") || jdbcUrl.contains("/" + "?"))) {
            int queryStart = jdbcUrl.indexOf('?');
            String base = queryStart >= 0 ? jdbcUrl.substring(0, queryStart) : jdbcUrl;
            String query = queryStart >= 0 ? jdbcUrl.substring(queryStart) : "";
            if (base.endsWith("/")) jdbcUrl = base + databaseName + query;
        }
        try (Connection connection = connectionManager.getConnection(info.id(), jdbcUrl, info.username(), info.password());
             Statement statement = connection.createStatement()) {
            if (databaseName != null && !databaseName.isBlank()) {
                if (!databaseName.matches("[A-Za-z0-9_]+")) throw new BadRequestException("数据库名称格式不合法");
                connection.setCatalog(databaseName);
                // StarRocks' MySQL driver may ignore setCatalog; issue an explicit
                // USE so real queries never depend on a session's default schema.
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
            executions.put(executionId, status);
            LocalDateTime finishedAt = LocalDateTime.now();
            long elapsedMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
            QueryResult result = new QueryResult(executionId, status, columns, rows, rows.size(), selected,
                    finishedAt, elapsedMs, properties.getQuery().getDefaultMaxRows(), null, columnComments);
            results.put(executionId, result);
            store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, result.status(), operator, startedAt, finishedAt,
                    elapsedMs, null);
            return result;
        } catch (SQLException ex) {
            String status = "CANCELED".equals(executions.get(executionId)) ? "CANCELED" : "FAILED";
            executions.put(executionId, status);
            LocalDateTime finishedAt = LocalDateTime.now();
            long elapsedMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
            QueryResult terminal = new QueryResult(executionId, status, List.of(), List.of(), 0, selected,
                    finishedAt, elapsedMs, properties.getQuery().getDefaultMaxRows(), ex.getMessage(), Map.of());
            results.put(executionId, terminal);
            store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, status, operator, startedAt, finishedAt,
                    elapsedMs, ex.getMessage());
            if ("CANCELED".equals(status)) return terminal;
            throw new BadRequestException("SQL 执行失败：" + ex.getMessage());
        } finally {
            statementRegistry.remove(executionId);
        }
    }

    public void cancel(String id) {
        executions.put(id, "CANCELED");
        statementRegistry.cancel(id);
    }

    private Map<String, String> loadColumnComments(Connection connection, String databaseName, String sql, List<String> columns) {
        String table = extractTableName(sql);
        if (table == null || columns.isEmpty()) return Map.of();
        Map<String, String> comments = new LinkedHashMap<>();
        try (ResultSet metadata = connection.getMetaData().getColumns(databaseName, null, table, "%")) {
            while (metadata.next()) {
                String name = metadata.getString("COLUMN_NAME");
                String remarks = metadata.getString("REMARKS");
                if (name != null && remarks != null && !remarks.isBlank()) comments.put(name, remarks);
            }
        } catch (SQLException ignored) {
            return Map.of();
        }
        return comments;
    }

    private String extractTableName(String sql) {
        Matcher matcher = Pattern.compile("(?i)\\bfrom\\s+([a-zA-Z0-9_$.`]+)").matcher(sql == null ? "" : sql);
        if (!matcher.find()) return null;
        String raw = matcher.group(1).replace("`", "");
        int dot = raw.lastIndexOf('.');
        return dot >= 0 ? raw.substring(dot + 1) : raw;
    }
    @PreDestroy
    public void shutdown() { queryExecutor.shutdownNow(); }
    public record QueryHandle(String executionId, String status) { }
    public record QueryResult(String executionId, String status, List<String> columns, List<Map<String, Object>> rows,
                              int rowCount, boolean selectedOnly, LocalDateTime finishedAt, long elapsedMs, int maxRows,
                              String errorMessage, Map<String, String> columnComments) { }
}
