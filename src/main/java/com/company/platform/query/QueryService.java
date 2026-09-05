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
        SqlSafetyChecker.CheckResult check = safetyChecker.check(sql);
        if (!check.safe()) throw new BadRequestException(check.message());
        if (dataSourceId != null) return executeJdbc(UUID.randomUUID().toString(), sql, selected, dataSourceId, databaseName);
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
            try { results.put(executionId, executeJdbc(executionId, sql, selected, dataSourceId, databaseName)); }
            catch (RuntimeException ignored) { /* status and audit record are persisted by executeJdbc */ }
        });
        return new QueryHandle(executionId, "RUNNING");
    }

    public QueryResult status(String executionId) {
        QueryResult result = results.get(executionId);
        if (result != null) return result;
        String status = executions.get(executionId);
        if (status == null) throw new BadRequestException("查询任务不存在：" + executionId);
        return new QueryResult(executionId, status, List.of(), List.of(), 0, false, null, properties.getQuery().getDefaultMaxRows(), null);
    }

    public List<QueryHistoryView> history() { return store.queryHistory(); }

    private QueryResult executeJdbc(String executionId, String sql, boolean selected, long dataSourceId, String databaseName) {
        LocalDateTime startedAt = LocalDateTime.now();
        executions.put(executionId, "RUNNING");
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.STARROCKS) {
            throw new BadRequestException("数据探查 SQL 仅允许在 StarRocks 数据源执行");
        }
        try (Connection connection = connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
             Statement statement = connection.createStatement()) {
            if (databaseName != null && !databaseName.isBlank()) connection.setCatalog(databaseName);
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
            String status = "CANCELED".equals(executions.get(executionId)) ? "CANCELED" : "SUCCESS";
            executions.put(executionId, status);
            QueryResult result = new QueryResult(executionId, status, columns, rows, rows.size(), selected,
                    LocalDateTime.now(), properties.getQuery().getDefaultMaxRows(), null);
            results.put(executionId, result);
            store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, result.status(), startedAt, result.finishedAt(),
                    java.time.Duration.between(startedAt, result.finishedAt()).toMillis(), null);
            return result;
        } catch (SQLException ex) {
            String status = "CANCELED".equals(executions.get(executionId)) ? "CANCELED" : "FAILED";
            executions.put(executionId, status);
            QueryResult terminal = new QueryResult(executionId, status, List.of(), List.of(), 0, selected,
                    LocalDateTime.now(), properties.getQuery().getDefaultMaxRows(), ex.getMessage());
            results.put(executionId, terminal);
            store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, status, startedAt, LocalDateTime.now(),
                    java.time.Duration.between(startedAt, LocalDateTime.now()).toMillis(), ex.getMessage());
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
    @PreDestroy
    public void shutdown() { queryExecutor.shutdownNow(); }
    public record QueryHandle(String executionId, String status) { }
    public record QueryResult(String executionId, String status, List<String> columns, List<Map<String, Object>> rows,
                              int rowCount, boolean selectedOnly, LocalDateTime finishedAt, int maxRows,
                              String errorMessage) { }
}
