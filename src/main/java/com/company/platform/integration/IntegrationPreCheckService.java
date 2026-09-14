package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Production precheck for offline MySQL -> StarRocks synchronization. */
@Service
public class IntegrationPreCheckService {
    private final MySqlToStarRocksTypeMapper typeMapper;
    private final StarRocksSchemaService schemaService;

    public IntegrationPreCheckService(MySqlToStarRocksTypeMapper typeMapper, StarRocksSchemaService schemaService) {
        this.typeMapper = typeMapper;
        this.schemaService = schemaService;
    }

    public Report check(IntegrationTask task) {
        List<Item> items = new ArrayList<>();
        if (task == null || task.source() == null || task.target() == null) {
            return new Report(false, List.of(error("ENDPOINT", "任务缺少来源或目标配置", "")));
        }
        if (!"MYSQL".equalsIgnoreCase(task.sourceType())) items.add(error("SOURCE_TYPE", "离线同步源必须是 MySQL", task.sourceType()));
        if (!"STARROCKS".equalsIgnoreCase(task.targetType())) items.add(error("TARGET_TYPE", "离线同步目标必须是 StarRocks", task.targetType()));
        Map<String,Object> options = task.options() == null ? Map.of() : task.options();
        String sourceTimezone = string(options.get("sourceTimezone"), "");
        String targetTimezone = string(options.get("targetTimezone"), "");
        if (sourceTimezone.isBlank()) items.add(error("SOURCE_TIMEZONE", "MySQL 数据源必须显式配置时区", ""));
        else items.add(info("SOURCE_TIMEZONE", "MySQL 数据源时区已明确", sourceTimezone));
        if (targetTimezone.isBlank()) items.add(error("TARGET_TIMEZONE", "StarRocks 数据源必须显式配置时区", ""));
        else items.add(info("TARGET_TIMEZONE", "StarRocks 数据源时区已明确", targetTimezone));
        checkMode(task, options, items);
        checkWritePolicy(options, items);
        if (items.stream().anyMatch(Item::blocking)) return new Report(false, items);

        try (Connection source = DriverManager.getConnection(jdbcUrl(task.source(), sourceTimezone), task.source().username(), task.source().password());
             Connection target = DriverManager.getConnection(jdbcUrl(task.target(), targetTimezone), task.target().username(), task.target().password())) {
            items.add(info("SOURCE_CONNECTION", "MySQL 来源连接正常", task.source().host() + ":" + task.source().port()));
            items.add(info("TARGET_CONNECTION", "StarRocks 目标连接正常", task.target().host() + ":" + task.target().port()));
            for (IntegrationRequests.TableRequest table : task.tables()) {
                checkTable(source, target, table, items);
            }
        } catch (Exception ex) {
            items.add(error("CONNECTION", "离线同步发布前检查无法完成", safe(ex)));
        }
        return new Report(items.stream().noneMatch(Item::blocking), items);
    }

    private void checkMode(IntegrationTask task, Map<String,Object> options, List<Item> items) {
        if ("INCREMENTAL".equalsIgnoreCase(task.syncMode())) {
            String where = string(options.get("where"), "");
            String normalized = where.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (where.isBlank() || !normalized.contains(">=") || !normalized.contains("<")) {
                items.add(error("INCREMENTAL_WINDOW", "离线增量必须使用 [start, end) 半开区间", where));
            } else items.add(info("INCREMENTAL_WINDOW", "增量条件采用半开区间", where));
            items.add(warn("INCREMENTAL_COLUMN", "请确认增量字段在每次 INSERT / UPDATE 时都会变化", "否则时间字段增量可能漏掉 UPDATE；无法保证时应使用 Flink CDC"));
        } else {
            int parallelism = intValue(options.get("parallelism"), 1);
            if (parallelism > 1) items.add(warn("SNAPSHOT_CONSISTENCY", "并行 JDBC 全量读取不是严格单点一致性快照", "parallelism=" + parallelism + "；数据库镜像场景建议 Flink CDC initial + binlog"));
        }
    }

    private void checkWritePolicy(Map<String,Object> options, List<Item> items) {
        String dataMode = string(options.get("dataSaveMode"), "APPEND_DATA").toUpperCase(Locale.ROOT);
        String schemaMode = string(options.get("schemaSaveMode"), "CREATE_SCHEMA_WHEN_NOT_EXIST").toUpperCase(Locale.ROOT);
        if ("APPEND_DATA".equals(dataMode)) {
            items.add(warn("APPEND_RETRY", "仅追加写入不能保证失败重试幂等", "历史批次将禁止直接 Retry，避免重复写入"));
        }
        String targetPolicy = string(options.get("targetPolicy"), "").toUpperCase(Locale.ROOT);
        boolean stagedOverwrite = "FULL_OVERWRITE".equals(targetPolicy) || "RECREATE".equals(targetPolicy)
                || "DROP_DATA".equals(dataMode) || "RECREATE_SCHEMA".equals(schemaMode);
        if (stagedOverwrite) {
            items.add(info("SAFE_STAGING", "全量覆盖将使用 staging → validate → atomic publish",
                    "正式表在同步与校验完成前保持不变，DROP/重建只作用于 staging 表"));
        }
        if ("RECREATE_SCHEMA".equals(schemaMode)) {
            items.add(warn("RECREATE_SCHEMA", "目标结构将通过 staging 按源 Schema 重建", "校验通过后再原子替换正式表；实时镜像禁止使用此策略"));
        }
        if ("IGNORE".equals(schemaMode)) {
            items.add(info("SCHEMA_POLICY", "平台不会修改目标表结构", "目标表必须已存在且字段兼容"));
        } else {
            items.add(info("SCHEMA_POLICY", "平台 SchemaMapper 负责目标结构", schemaMode));
        }
    }

    private void checkTable(Connection source, Connection target, IntegrationRequests.TableRequest table, List<Item> items) throws Exception {
        List<Column> sourceColumns = sourceColumns(source, table.sourceDatabase(), table.sourceTable());
        if (sourceColumns.isEmpty()) {
            items.add(error("SOURCE_TABLE", "源表不存在或没有字段", table.sourceDatabase() + "." + table.sourceTable()));
            return;
        }
        for (Column column : sourceColumns) {
            try {
                String mapped = typeMapper.map(column.type());
                items.add(info("TYPE_MAPPING", "字段类型可安全映射", column.name() + ": " + column.type() + " → " + mapped));
            } catch (BadRequestException ex) {
                items.add(error("TYPE_MAPPING", ex.getMessage(), table.sourceTable() + "." + column.name()));
            }
            if (dateLike(column.type()) && hasZeroDate(source, table.sourceDatabase(), table.sourceTable(), column.name())) {
                items.add(error("ZERO_DATE", "发现 MySQL 零日期，当前策略禁止上线", table.sourceTable() + "." + column.name()));
            }
        }

        if (!databaseExists(target, table.targetDatabase())) {
            items.add(error("TARGET_DATABASE", "StarRocks 目标数据库不存在", table.targetDatabase()));
            return;
        }
        List<Column> targetColumns = targetColumns(target, table.targetDatabase(), table.targetTable());
        if (targetColumns.isEmpty()) {
            items.add(info("TARGET_TABLE", "目标表不存在，将由平台 SchemaMapper 创建", table.targetDatabase() + "." + table.targetTable()));
            return;
        }
        Map<String,String> targetTypes = new java.util.HashMap<>();
        targetColumns.forEach(c -> targetTypes.put(c.name().toLowerCase(Locale.ROOT), c.type()));
        for (Column sourceColumn : sourceColumns) {
            String targetType = targetTypes.get(sourceColumn.name().toLowerCase(Locale.ROOT));
            if (targetType == null) continue;
            String mapped = typeMapper.map(sourceColumn.type());
            if (!schemaService.compatibleTargetType(mapped, targetType)) {
                items.add(error("TARGET_TYPE", "目标字段类型不能安全承载源字段", sourceColumn.name() + ": " + mapped + " → " + targetType));
            }
        }
    }

    private List<Column> sourceColumns(Connection connection, String database, String table) throws Exception {
        return columns(connection, "SHOW FULL COLUMNS FROM " + id(database) + "." + id(table));
    }

    private List<Column> targetColumns(Connection connection, String database, String table) {
        try { return columns(connection, "SHOW FULL COLUMNS FROM " + id(database) + "." + id(table)); }
        catch (Exception ex) { return List.of(); }
    }

    private List<Column> columns(Connection connection, String sql) throws Exception {
        List<Column> result = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) result.add(new Column(rs.getString("Field"), rs.getString("Type")));
        }
        return result;
    }

    private boolean hasZeroDate(Connection connection, String database, String table, String column) {
        String sql = "SELECT 1 FROM " + id(database) + "." + id(table) + " WHERE CAST(" + id(column) + " AS CHAR) LIKE '0000-00-00%' LIMIT 1";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) { return rs.next(); }
        catch (Exception ignored) { return false; }
    }

    private boolean databaseExists(Connection connection, String database) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SHOW DATABASES LIKE " + literal(database))) { return rs.next(); }
    }

    private String jdbcUrl(IntegrationRequests.Endpoint endpoint, String timezone) {
        String host = endpoint.host().split(",")[0].trim();
        return "jdbc:mysql://" + host + ":" + endpoint.port() + "/?useUnicode=true&characterEncoding=UTF-8&serverTimezone=" + timezone
                + "&useSSL=false&tinyInt1isBit=false&allowPublicKeyRetrieval=true";
    }
    private boolean dateLike(String type) { String value=type==null?"":type.toLowerCase(Locale.ROOT); return value.startsWith("date") || value.startsWith("datetime") || value.startsWith("timestamp"); }
    private int intValue(Object value, int fallback) { try { return value instanceof Number n ? n.intValue() : value == null ? fallback : Integer.parseInt(value.toString()); } catch (Exception ex) { return fallback; } }
    private String string(Object value, String fallback) { String result=value==null?"":String.valueOf(value).trim(); return result.isBlank()?fallback:result; }
    private String id(String value) { return "`" + String.valueOf(value).replace("`", "``") + "`"; }
    private String literal(String value) { return "'" + String.valueOf(value).replace("\\", "\\\\").replace("'", "''") + "'"; }
    private String safe(Throwable ex) { String message=ex.getMessage(); return message==null?ex.getClass().getSimpleName():message.replaceAll("(?i)(password=)[^&\\s]+", "$1***"); }
    private Item error(String code,String message,String detail){return new Item("ERROR",code,message,detail);}
    private Item warn(String code,String message,String detail){return new Item("WARNING",code,message,detail);}
    private Item info(String code,String message,String detail){return new Item("INFO",code,message,detail);}

    public record Item(String level,String code,String message,String detail){public boolean blocking(){return "ERROR".equals(level);}}
    public record Report(boolean allowed,List<Item> items){}
    private record Column(String name,String type){}
}
