package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.integration.IntegrationRequests;
import com.company.platform.integration.IntegrationTask;
import com.company.platform.integration.MySqlToStarRocksTypeMapper;
import com.company.platform.integration.StarRocksSchemaService;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RealtimePreCheckService {
    private static final long RECOMMENDED_BINLOG_RETENTION_SECONDS = 7L * 24 * 3600;
    private final DataSourceService dataSources;
    private final MySqlToStarRocksTypeMapper typeMapper;
    private final StarRocksSchemaService schemaService;

    public RealtimePreCheckService(DataSourceService dataSources, MySqlToStarRocksTypeMapper typeMapper,
                                   StarRocksSchemaService schemaService) {
        this.dataSources = dataSources;
        this.typeMapper = typeMapper;
        this.schemaService = schemaService;
    }

    public Report check(Map<String, Object> spec) {
        List<Item> items = new ArrayList<>();
        long sourceId = longValue(spec.get("sourceDataSourceId"));
        long sinkId = longValue(spec.get("sinkDataSourceId"));
        if (sourceId <= 0 || sinkId <= 0) return new Report(false, List.of(error("DATASOURCE", "必须选择 MySQL 来源和 StarRocks 目标数据源", "")));
        var sourceView = dataSources.get(sourceId);
        var sinkView = dataSources.get(sinkId);
        if (sourceView.type() != DataSourceType.MYSQL) items.add(error("SOURCE_TYPE", "实时同步源必须是 MySQL", sourceView.type().name()));
        if (sinkView.type() != DataSourceType.STARROCKS) items.add(error("SINK_TYPE", "实时同步目标必须是 StarRocks", sinkView.type().name()));
        if (sourceView.timezone() == null || sourceView.timezone().isBlank()) items.add(error("TIMEZONE", "MySQL 数据源必须显式配置时区", ""));
        else items.add(info("TIMEZONE", "数据源时区已明确", sourceView.timezone()));
        if (!items.stream().filter(Item::blocking).toList().isEmpty()) return new Report(false, items);

        String sourceDb = string(spec.get("sourceDatabase"), sourceView.databaseName());
        String sinkDb = string(spec.get("sinkDatabase"), sinkView.databaseName());
        List<TablePair> tables = tables(spec);
        if (tables.isEmpty()) items.add(error("TABLES", "至少选择一张同步表", ""));
        try (Connection source = connection(dataSources.connectionInfo(sourceId));
             Connection sink = connection(dataSources.connectionInfo(sinkId))) {
            checkBinlog(source, items);
            checkPrivileges(source, items);
            checkTables(source, sink, sourceDb, sinkDb, tables, items);
        } catch (BadRequestException ex) {
            items.add(error("CONNECTION", ex.getMessage(), ""));
        } catch (Exception ex) {
            items.add(error("CONNECTION", "发布前检查无法连接数据源，请先检查网络、账号和端口", safe(ex)));
        }
        return new Report(items.stream().noneMatch(Item::blocking), items);
    }

    public void prepareTargets(Map<String, Object> spec) {
        long sourceId = longValue(spec.get("sourceDataSourceId"));
        long sinkId = longValue(spec.get("sinkDataSourceId"));
        var sourceView = dataSources.get(sourceId);
        var sinkView = dataSources.get(sinkId);
        var sourceInfo = dataSources.connectionInfo(sourceId);
        var sinkInfo = dataSources.connectionInfo(sinkId);
        String sourceDb = string(spec.get("sourceDatabase"), sourceView.databaseName());
        String sinkDb = string(spec.get("sinkDatabase"), sinkView.databaseName());
        List<IntegrationRequests.TableRequest> requests = tables(spec).stream()
                .map(t -> new IntegrationRequests.TableRequest(sourceDb, t.source(), sinkDb, t.target(), ""))
                .toList();
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint(sourceView.host(), sourceView.port(), sourceDb,
                sourceInfo.username(), sourceInfo.password(), requests.get(0).sourceTable());
        IntegrationRequests.Endpoint sink = new IntegrationRequests.Endpoint(sinkView.host(), sinkView.port(), sinkDb,
                sinkInfo.username(), sinkInfo.password(), requests.get(0).targetTable());
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("sourceTimezone", sourceView.timezone());
        options.put("targetTimezone", sinkView.timezone());
        options.put("schemaSaveMode", "CREATE_SCHEMA_WHEN_NOT_EXIST");
        options.put("starrocksReplicationNum", 1);
        schemaService.prepare(new IntegrationTask("realtime-precheck", "MYSQL", "STARROCKS", "REALTIME",
                source, sink, List.of(), options, requests));
    }

    private void checkBinlog(Connection source, List<Item> items) throws Exception {
        String logBin = variable(source, "log_bin");
        String format = variable(source, "binlog_format");
        String image = variable(source, "binlog_row_image");
        String serverId = variable(source, "server_id");
        requireEquals(items, "BINLOG_ENABLED", "log_bin", logBin, "ON");
        requireEquals(items, "BINLOG_FORMAT", "binlog_format", format, "ROW");
        requireEquals(items, "BINLOG_ROW_IMAGE", "binlog_row_image", image, "FULL");
        if (serverId == null || serverId.isBlank() || "0".equals(serverId)) items.add(error("MYSQL_SERVER_ID", "MySQL server_id 未正确配置", String.valueOf(serverId)));
        else items.add(info("MYSQL_SERVER_ID", "MySQL server_id 正常", serverId));

        long retention = longVariable(source, "binlog_expire_logs_seconds");
        if (retention <= 0) {
            long days = longVariable(source, "expire_logs_days");
            retention = days > 0 ? days * 86400L : 0;
        }
        if (retention > 0 && retention < RECOMMENDED_BINLOG_RETENTION_SECONDS) {
            items.add(warn("BINLOG_RETENTION", "Binlog 保留周期偏短", "当前约 " + retention / 3600 + " 小时，建议至少 7 天"));
        } else if (retention > 0) items.add(info("BINLOG_RETENTION", "Binlog 保留周期正常", retention / 86400 + " 天"));
        else items.add(warn("BINLOG_RETENTION", "无法确认 Binlog 保留周期", "请确认故障恢复窗口内 Binlog 不会被清理"));

        try (Statement st = source.createStatement(); ResultSet rs = st.executeQuery("SELECT @@global.time_zone, @@session.time_zone")) {
            if (rs.next()) items.add(info("MYSQL_TIMEZONE", "MySQL 时区已读取", "global=" + rs.getString(1) + ", session=" + rs.getString(2)));
        }
    }

    private void checkPrivileges(Connection source, List<Item> items) throws Exception {
        StringBuilder grants = new StringBuilder();
        try (Statement st = source.createStatement(); ResultSet rs = st.executeQuery("SHOW GRANTS")) {
            while (rs.next()) grants.append(rs.getString(1)).append('\n');
        }
        String value = grants.toString().toUpperCase(Locale.ROOT);
        boolean all = value.contains("ALL PRIVILEGES");
        List<String> missing = new ArrayList<>();
        if (!all && !value.contains("SELECT")) missing.add("SELECT");
        if (!all && !value.contains("SHOW DATABASES")) missing.add("SHOW DATABASES");
        if (!all && !(value.contains("REPLICATION SLAVE") || value.contains("REPLICATION REPLICA"))) missing.add("REPLICATION SLAVE/REPLICA");
        if (!all && !value.contains("REPLICATION CLIENT")) missing.add("REPLICATION CLIENT");
        if (missing.isEmpty()) items.add(info("CDC_PRIVILEGES", "CDC 权限正常", "SELECT / SHOW DATABASES / REPLICATION 权限已具备"));
        else items.add(error("CDC_PRIVILEGES", "CDC 权限不足", "缺少：" + String.join(", ", missing)));
    }

    private void checkTables(Connection source, Connection sink, String sourceDb, String sinkDb,
                             List<TablePair> tables, List<Item> items) throws Exception {
        if (!databaseExists(sink, sinkDb)) {
            items.add(error("SINK_DATABASE", "StarRocks 目标数据库不存在", sinkDb));
            return;
        }
        for (TablePair table : tables) {
            List<Column> columns = sourceColumns(source, sourceDb, table.source());
            if (columns.isEmpty()) {
                items.add(error("SOURCE_TABLE", "源表不存在或没有字段", sourceDb + "." + table.source()));
                continue;
            }
            List<String> primaryKeys = columns.stream().filter(Column::primaryKey).map(Column::name).toList();
            if (primaryKeys.isEmpty()) {
                items.add(error("PRIMARY_KEY", "实时数据库镜像要求源表存在 Primary Key", sourceDb + "." + table.source()));
            } else items.add(info("PRIMARY_KEY", "源表 Primary Key 正常", sourceDb + "." + table.source() + " → " + String.join(",", primaryKeys)));
            for (Column column : columns) {
                try {
                    String mapped = typeMapper.map(column.mysqlType());
                    if (column.mysqlType().toLowerCase(Locale.ROOT).startsWith("tinyint(1)"))
                        items.add(info("TINYINT1", "TINYINT(1) 已按数值 TINYINT 处理", table.source() + "." + column.name()));
                    if (column.mysqlType().toLowerCase(Locale.ROOT).contains("unsigned"))
                        items.add(info("UNSIGNED", "UNSIGNED 已扩大目标类型防止溢出", column.mysqlType() + " → " + mapped));
                } catch (BadRequestException ex) {
                    items.add(error("TYPE_MAPPING", ex.getMessage(), table.source() + "." + column.name()));
                }
                if (dateLike(column.mysqlType()) && hasZeroDate(source, sourceDb, table.source(), column.name())) {
                    items.add(error("ZERO_DATE", "发现 MySQL 零日期，当前策略禁止发布", table.source() + "." + column.name()));
                }
            }
            String create = showCreateTable(sink, sinkDb, table.target());
            if (create == null) {
                items.add(info("TARGET_TABLE", "目标表不存在，将按统一 SchemaMapper 创建 PRIMARY KEY 表", sinkDb + "." + table.target()));
            } else {
                String upper = create.toUpperCase(Locale.ROOT);
                if (!upper.contains("PRIMARY KEY")) items.add(error("TARGET_MODEL", "实时镜像目标表必须是 PRIMARY KEY 模型，才能正确传播 UPDATE / DELETE", sinkDb + "." + table.target()));
                for (String key : primaryKeys) {
                    if (!upper.contains("`" + key.toUpperCase(Locale.ROOT) + "`"))
                        items.add(error("TARGET_PRIMARY_KEY", "目标表 Primary Key 与源表不一致", "缺少主键字段 " + key));
                }
            }
        }
    }

    private List<Column> sourceColumns(Connection c, String db, String table) throws Exception {
        List<Column> result = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW FULL COLUMNS FROM " + id(db) + "." + id(table))) {
            while (rs.next()) result.add(new Column(rs.getString("Field"), rs.getString("Type"), "PRI".equalsIgnoreCase(rs.getString("Key"))));
        }
        return result;
    }

    private boolean hasZeroDate(Connection c, String db, String table, String column) {
        String sql = "SELECT 1 FROM " + id(db) + "." + id(table) + " WHERE CAST(" + id(column) + " AS CHAR) LIKE '0000-00-00%' LIMIT 1";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next(); }
        catch (Exception ignored) { return false; }
    }

    private boolean databaseExists(Connection c, String db) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW DATABASES LIKE " + literal(db))) { return rs.next(); }
    }
    private String showCreateTable(Connection c, String db, String table) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + id(db) + "." + id(table))) {
            return rs.next() ? rs.getString(2) : null;
        } catch (Exception ex) { return null; }
    }
    private String variable(Connection c, String name) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW VARIABLES LIKE " + literal(name))) { return rs.next() ? rs.getString(2) : null; }
    }
    private long longVariable(Connection c, String name) {
        try { String value = variable(c, name); return value == null ? 0 : Long.parseLong(value.trim()); }
        catch (Exception ignored) { return 0; }
    }
    private void requireEquals(List<Item> items, String code, String name, String actual, String expected) {
        if (!expected.equalsIgnoreCase(String.valueOf(actual))) items.add(error(code, name + " 不满足实时 CDC 要求", "当前=" + actual + "，要求=" + expected));
        else items.add(info(code, name + " 正常", actual));
    }
    private Connection connection(DataSourceService.ConnectionInfo info) throws Exception { return DriverManager.getConnection(info.jdbcUrl(), info.username(), info.password()); }
    private boolean dateLike(String type) { String v = type.toLowerCase(Locale.ROOT); return v.startsWith("date") || v.startsWith("datetime") || v.startsWith("timestamp"); }
    private List<TablePair> tables(Map<String, Object> spec) {
        Object raw = spec.get("tables");
        if (!(raw instanceof List<?> list)) return List.of();
        List<TablePair> result = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?> map) {
            Object sourceRaw = map.get("sourceTable");
            String source = sourceRaw == null ? "" : String.valueOf(sourceRaw).trim();
            Object targetRaw = map.get("targetTable");
            String target = targetRaw == null ? source : String.valueOf(targetRaw).trim();
            if (!source.isBlank()) result.add(new TablePair(source, target.isBlank() ? source : target));
        }
        return result;
    }
    private long longValue(Object value) { try { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); } catch (Exception ex) { return 0; } }
    private String string(Object value, String fallback) { String v = value == null ? "" : String.valueOf(value).trim(); return v.isBlank() ? (fallback == null ? "" : fallback) : v; }
    private String id(String value) { return "`" + String.valueOf(value).replace("`", "``") + "`"; }
    private String literal(String value) { return "'" + String.valueOf(value).replace("\\", "\\\\").replace("'", "''") + "'"; }
    private String safe(Throwable ex) { String m = ex.getMessage(); return m == null ? ex.getClass().getSimpleName() : m.replaceAll("(?i)(password=)[^&\\s]+", "$1***"); }
    private Item error(String code, String message, String detail) { return new Item("ERROR", code, message, detail); }
    private Item warn(String code, String message, String detail) { return new Item("WARNING", code, message, detail); }
    private Item info(String code, String message, String detail) { return new Item("INFO", code, message, detail); }

    public record Item(String level, String code, String message, String detail) { public boolean blocking() { return "ERROR".equals(level); } }
    public record Report(boolean allowed, List<Item> items) { }
    private record Column(String name, String mysqlType, boolean primaryKey) { }
    private record TablePair(String source, String target) { }
}
