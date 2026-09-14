package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Implements safe full replacement: staging -> validate -> atomic publish. */
@Service
public class IntegrationStagingService {
    private final PlatformStore store;
    private final ObjectMapper mapper;
    private final PasswordCipher cipher;

    public IntegrationStagingService(PlatformStore store, ObjectMapper mapper, PasswordCipher cipher) {
        this.store = store; this.mapper = mapper; this.cipher = cipher;
    }

    public Prepared prepare(IntegrationTask task) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<IntegrationRequests.TableRequest> stagedTables = new ArrayList<>();
        List<StageTable> stageMappings = new ArrayList<>();
        for (IntegrationRequests.TableRequest table : task.tables()) {
            String stage = stageName(table.targetTable(), token);
            stagedTables.add(new IntegrationRequests.TableRequest(table.sourceDatabase(), table.sourceTable(),
                    table.targetDatabase(), stage, table.partitionColumn()));
            stageMappings.add(new StageTable(table.sourceDatabase(), table.sourceTable(), table.targetDatabase(),
                    stage, table.targetTable()));
        }
        Map<String,Object> options = new HashMap<>(task.options() == null ? Map.of() : task.options());
        options.put("schemaSaveMode", "CREATE_SCHEMA_WHEN_NOT_EXIST");
        options.put("dataSaveMode", "DROP_DATA");
        options.put("stagingRun", true);
        IntegrationTask stagedTask = new IntegrationTask(task.name(), task.sourceType(), task.targetType(), task.syncMode(),
                task.source(), task.target(), task.mappings(), options, stagedTables);
        return new Prepared(stagedTask, stageMappings);
    }

    public boolean requiresStaging(IntegrationTask task) {
        Map<String,Object> options = task.options() == null ? Map.of() : task.options();
        String targetPolicy = string(options.get("targetPolicy"), "").toUpperCase(Locale.ROOT);
        String dataMode = string(options.get("dataSaveMode"), "APPEND_DATA").toUpperCase(Locale.ROOT);
        String schemaMode = string(options.get("schemaSaveMode"), "CREATE_SCHEMA_WHEN_NOT_EXIST").toUpperCase(Locale.ROOT);
        return "FULL_OVERWRITE".equals(targetPolicy) || "RECREATE".equals(targetPolicy)
                || "DROP_DATA".equals(dataMode) || "RECREATE_SCHEMA".equals(schemaMode);
    }

    public String parametersJson(List<StageTable> mappings, String reason) {
        try {
            Map<String,Object> data = new HashMap<>();
            data.put("staging", true);
            data.put("reason", reason == null ? "FULL_OVERWRITE" : reason);
            data.put("stagingTables", mappings);
            return mapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new BadRequestException("无法保存 staging 运行参数");
        }
    }

    public PublishResult publish(long taskId, String parametersJson) {
        List<StageTable> mappings = stageTables(parametersJson);
        if (mappings.isEmpty()) return new PublishResult(false, "不是 staging 批次", List.of());
        IntegrationTaskView task = store.integrationTasks.get(taskId);
        if (task == null) throw new BadRequestException("离线同步任务不存在：" + taskId);
        try {
            IntegrationRequests.Endpoint targetStored = mapper.readValue(task.targetConfigJson(), IntegrationRequests.Endpoint.class);
            IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint(targetStored.host(), targetStored.port(), targetStored.database(),
                    targetStored.username(), cipher.decrypt(targetStored.password()), targetStored.table());
            JsonNode transform = mapper.readTree(task.transformConfigJson() == null ? "{}" : task.transformConfigJson());
            Map<String,Object> options = mapper.convertValue(transform.path("options"), Map.class);
            String timezone = string(options.get("targetTimezone"), "Asia/Shanghai");
            List<String> published = new ArrayList<>();
            try (Connection connection = DriverManager.getConnection(jdbcUrl(target, timezone), target.username(), target.password())) {
                for (StageTable mapping : mappings) {
                    publishOne(connection, mapping);
                    published.add(mapping.targetDatabase() + "." + mapping.officialTable());
                }
            }
            return new PublishResult(true, "staging 校验通过并已原子发布", published);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("staging 数据已完成但原子发布失败：" + safe(ex));
        }
    }

    public List<StageTable> stageTables(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) return List.of();
        try {
            JsonNode root = mapper.readTree(parametersJson);
            if (!root.path("staging").asBoolean(false) || !root.path("stagingTables").isArray()) return List.of();
            List<StageTable> result = new ArrayList<>();
            for (JsonNode node : root.path("stagingTables")) result.add(mapper.treeToValue(node, StageTable.class));
            return result;
        } catch (Exception ex) { return List.of(); }
    }

    private void publishOne(Connection connection, StageTable mapping) throws Exception {
        if (!tableExists(connection, mapping.targetDatabase(), mapping.stagingTable())) {
            throw new BadRequestException("staging 表不存在，不能发布：" + mapping.targetDatabase() + "." + mapping.stagingTable());
        }
        try (Statement statement = connection.createStatement()) {
            if (tableExists(connection, mapping.targetDatabase(), mapping.officialTable())) {
                statement.execute("ALTER TABLE " + id(mapping.targetDatabase()) + "." + id(mapping.officialTable())
                        + " SWAP WITH " + id(mapping.stagingTable()));
                try { statement.execute("DROP TABLE " + id(mapping.targetDatabase()) + "." + id(mapping.stagingTable())); }
                catch (Exception ignored) { /* old data can be cleaned later; official table is already safe */ }
            } else {
                statement.execute("ALTER TABLE " + id(mapping.targetDatabase()) + "." + id(mapping.stagingTable())
                        + " RENAME " + id(mapping.officialTable()));
            }
        }
    }

    private boolean tableExists(Connection connection, String database, String table) {
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("SHOW FULL COLUMNS FROM " + id(database) + "." + id(table))) { return true; }
        catch (Exception ex) { return false; }
    }

    private String stageName(String target, String token) {
        String base = target == null ? "table" : target.replaceAll("[^A-Za-z0-9_]", "_");
        int maxBase = Math.max(1, 60 - token.length());
        if (base.length() > maxBase) base = base.substring(0, maxBase);
        return base + "__ds_stage_" + token;
    }
    private String jdbcUrl(IntegrationRequests.Endpoint endpoint, String timezone) {
        String host = endpoint.host().split(",")[0].trim();
        return "jdbc:mysql://" + host + ":" + endpoint.port() + "/?useUnicode=true&characterEncoding=UTF-8&serverTimezone=" + timezone
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }
    private String id(String value) { return "`" + String.valueOf(value).replace("`", "``") + "`"; }
    private String string(Object value,String fallback){String result=value==null?"":String.valueOf(value).trim();return result.isBlank()?fallback:result;}
    private String safe(Throwable ex){String message=ex.getMessage();return message==null?ex.getClass().getSimpleName():message.replaceAll("(?i)(password=)[^&\\s]+","$1***");}

    public record StageTable(String sourceDatabase,String sourceTable,String targetDatabase,String stagingTable,String officialTable){}
    public record Prepared(IntegrationTask task,List<StageTable> mappings){}
    public record PublishResult(boolean published,String message,List<String> tables){}
}
