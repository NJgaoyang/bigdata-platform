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

@Service
public class QueryService {
    private final SqlSafetyChecker safetyChecker;
    private final PlatformProperties properties;
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;
    private final RunningStatementRegistry statementRegistry;
    private final PlatformStore store;
    private final Map<String, String> executions = new ConcurrentHashMap<>();
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
        if (dataSourceId != null) return executeJdbc(sql, selected, dataSourceId, databaseName);
        String executionId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        executions.put(executionId, "SUCCESS");
        QueryResult result = new QueryResult(executionId, "SUCCESS", List.of("order_date", "total_amount"),
                List.of(Map.of("order_date", "2026-09-03", "total_amount", 12880.50)),
                1, selected, LocalDateTime.now(), properties.getQuery().getDefaultMaxRows());
        store.persistQueryExecution(executionId, null, null, sql, result.status(), startedAt, result.finishedAt(), 1, null);
        return result;
    }

    private QueryResult executeJdbc(String sql, boolean selected, long dataSourceId, String databaseName) {
        String executionId = UUID.randomUUID().toString();
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
                    LocalDateTime.now(), properties.getQuery().getDefaultMaxRows());
            store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, result.status(), startedAt, result.finishedAt(),
                    java.time.Duration.between(startedAt, result.finishedAt()).toMillis(), null);
            return result;
        } catch (SQLException ex) {
            executions.put(executionId, "FAILED");
            store.persistQueryExecution(executionId, dataSourceId, databaseName, sql, "FAILED", startedAt, LocalDateTime.now(),
                    java.time.Duration.between(startedAt, LocalDateTime.now()).toMillis(), ex.getMessage());
            throw new BadRequestException("SQL 执行失败：" + ex.getMessage());
        } finally {
            statementRegistry.remove(executionId);
        }
    }

    public void cancel(String id) {
        executions.put(id, "CANCELED");
        statementRegistry.cancel(id);
    }
    public record QueryResult(String executionId, String status, List<String> columns, List<Map<String, Object>> rows,
                              int rowCount, boolean selectedOnly, LocalDateTime finishedAt, int maxRows) { }
}
