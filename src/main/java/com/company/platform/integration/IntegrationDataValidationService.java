package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Post-run consistency check. Engine FINISHED is not accepted as data SUCCESS until this check passes. */
@Service
public class IntegrationDataValidationService {
    private final PlatformStore store;
    private final ObjectMapper mapper;
    private final PasswordCipher cipher;
    private final IntegrationRuntimeRepository runtime;
    private final IntegrationStagingService staging;
    private final JdbcTemplate jdbc;

    public IntegrationDataValidationService(PlatformStore store, ObjectMapper mapper, PasswordCipher cipher,
                                            IntegrationRuntimeRepository runtime, IntegrationStagingService staging, JdbcTemplate jdbc) {
        this.store = store; this.mapper = mapper; this.cipher = cipher; this.runtime = runtime; this.staging = staging; this.jdbc = jdbc;
    }

    public Result validate(long taskId, String executionId) {
        IntegrationTaskView task = store.integrationTasks.get(taskId);
        if (task == null || task.sourceConfigJson() == null || task.sourceConfigJson().isBlank()) {
            return new Result(false, "任务缺少结构化配置，无法执行数据一致性校验", List.of());
        }
        try {
            IntegrationRequests.Endpoint sourceStored = mapper.readValue(task.sourceConfigJson(), IntegrationRequests.Endpoint.class);
            IntegrationRequests.Endpoint targetStored = mapper.readValue(task.targetConfigJson(), IntegrationRequests.Endpoint.class);
            IntegrationRequests.Endpoint source = withPassword(sourceStored, cipher.decrypt(sourceStored.password()));
            IntegrationRequests.Endpoint target = withPassword(targetStored, cipher.decrypt(targetStored.password()));
            JsonNode transform = mapper.readTree(task.transformConfigJson() == null ? "{}" : task.transformConfigJson());
            Map<String,Object> options = mapper.convertValue(transform.path("options"), new TypeReference<Map<String,Object>>(){});
            List<IntegrationRequests.TableRequest> tables = task.tables().isEmpty()
                    ? mapper.convertValue(transform.path("tables"), new TypeReference<List<IntegrationRequests.TableRequest>>() {})
                    : task.tables().stream().map(t -> new IntegrationRequests.TableRequest(t.sourceDatabase(), t.sourceTable(), t.targetDatabase(), t.targetTable(), t.partitionColumn())).toList();
            if (tables == null || tables.isEmpty()) return new Result(false, "任务没有表映射，无法执行数据一致性校验", List.of());
            String where = effectiveWhere(executionId, options);
            String sourceTimezone = string(options.get("sourceTimezone"), "Asia/Shanghai");
            String targetTimezone = string(options.get("targetTimezone"), "Asia/Shanghai");
            Long batchId = executionId == null ? null : runtime.batchIdForExecution(executionId);
            if (batchId != null) tables = validationTables(tables, runtime.getBatch(batchId).parametersJson());
            List<TableResult> results = new ArrayList<>();
            try (Connection sourceConnection = DriverManager.getConnection(jdbcUrl(source, sourceTimezone), source.username(), source.password());
                 Connection targetConnection = DriverManager.getConnection(jdbcUrl(target, targetTimezone), target.username(), target.password())) {
                for (IntegrationRequests.TableRequest table : tables) {
                    long sourceCount = count(sourceConnection, table.sourceDatabase(), table.sourceTable(), where);
                    long targetCount = count(targetConnection, table.targetDatabase(), table.targetTable(), where);
                    boolean passed = sourceCount == targetCount;
                    String detail = passed ? "源端与目标端行数一致" : "源端=" + sourceCount + "，目标端=" + targetCount;
                    persist(batchId, taskId, executionId, table, sourceCount, targetCount, passed ? "PASSED" : "FAILED", detail);
                    results.add(new TableResult(table.sourceDatabase(), table.sourceTable(), table.targetDatabase(), table.targetTable(), sourceCount, targetCount, passed, detail));
                }
            }
            boolean passed = results.stream().allMatch(TableResult::passed);
            return new Result(passed, passed ? "数据一致性校验通过" : "数据一致性校验失败：源端与目标端行数不一致", results);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new Result(false, "数据一致性校验执行失败：" + message, List.of());
        }
    }


    private List<IntegrationRequests.TableRequest> validationTables(List<IntegrationRequests.TableRequest> original, String parametersJson) {
        List<IntegrationStagingService.StageTable> staged = staging.stageTables(parametersJson);
        if (staged.isEmpty()) return original;
        List<IntegrationRequests.TableRequest> result = new ArrayList<>();
        for (IntegrationRequests.TableRequest table : original) {
            IntegrationStagingService.StageTable match = staged.stream()
                    .filter(item -> item.sourceDatabase().equals(table.sourceDatabase()) && item.sourceTable().equals(table.sourceTable())
                            && item.targetDatabase().equals(table.targetDatabase()) && item.officialTable().equals(table.targetTable()))
                    .findFirst().orElse(null);
            result.add(match == null ? table : new IntegrationRequests.TableRequest(table.sourceDatabase(), table.sourceTable(),
                    table.targetDatabase(), match.stagingTable(), table.partitionColumn()));
        }
        return result;
    }

    private String effectiveWhere(String executionId, Map<String,Object> options) {
        String configured = string(options.get("where"), "");
        if (executionId == null || executionId.isBlank()) return configured;
        Long batchId = runtime.batchIdForExecution(executionId);
        if (batchId == null) return configured;
        try {
            IntegrationBatchView batch = runtime.getBatch(batchId);
            if (batch.parametersJson() == null || batch.parametersJson().isBlank()) return configured;
            JsonNode parameters = mapper.readTree(batch.parametersJson());
            String override = parameters.path("where").asText("").trim();
            return override.isBlank() ? configured : override;
        } catch (Exception ignored) { return configured; }
    }

    private long count(Connection connection, String database, String table, String where) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + id(database) + "." + id(table);
        if (where != null && !where.isBlank()) sql += " WHERE " + where;
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) throw new BadRequestException("COUNT 查询没有返回结果：" + database + "." + table);
            return rs.getLong(1);
        }
    }

    private void persist(Long batchId, long taskId, String executionId, IntegrationRequests.TableRequest table,
                         long sourceCount, long targetCount, String status, String detail) {
        jdbc.update("INSERT INTO integration_validation_result(batch_id,task_id,execution_id,source_database,source_table,target_database,target_table,source_count,target_count,status,detail) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                batchId, taskId, executionId, table.sourceDatabase(), table.sourceTable(), table.targetDatabase(), table.targetTable(), sourceCount, targetCount, status, detail);
    }

    private IntegrationRequests.Endpoint withPassword(IntegrationRequests.Endpoint endpoint, String password) {
        return new IntegrationRequests.Endpoint(endpoint.host(), endpoint.port(), endpoint.database(), endpoint.username(), password, endpoint.table());
    }
    private String jdbcUrl(IntegrationRequests.Endpoint endpoint, String timezone) {
        String host = endpoint.host().split(",")[0].trim();
        return "jdbc:mysql://" + host + ":" + endpoint.port() + "/?useUnicode=true&characterEncoding=UTF-8&serverTimezone=" + timezone + "&useSSL=false&tinyInt1isBit=false&allowPublicKeyRetrieval=true";
    }
    private String id(String value) { return "`" + String.valueOf(value).replace("`", "``") + "`"; }
    private String string(Object value, String fallback) { String v=value==null?"":String.valueOf(value).trim(); return v.isBlank()?fallback:v; }

    public record TableResult(String sourceDatabase, String sourceTable, String targetDatabase, String targetTable,
                              long sourceCount, long targetCount, boolean passed, String detail) { }
    public record Result(boolean passed, String message, List<TableResult> tables) { }
}
