package com.company.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SeaTunnel 2.3.12 HOCON generator.
 *
 * <p>The implementation intentionally follows the proven datasync-server model:
 * bounded MySQL JDBC reads for full/incremental jobs and MySQL-CDC for realtime
 * jobs, both writing through SeaTunnel's native StarRocks connector.</p>
 */
@Component
public class SeaTunnelConfigBuilder {
    public SeaTunnelConfigBuilder(ObjectMapper ignored) { }

    public String build(IntegrationTask task) {
        if (task == null || task.source() == null || task.target() == null) {
            throw new IllegalArgumentException("同步任务缺少源端或目标端配置");
        }
        if (!"MYSQL".equalsIgnoreCase(task.sourceType()) || !"STARROCKS".equalsIgnoreCase(task.targetType())) {
            throw new IllegalArgumentException("当前仅支持 MySQL → StarRocks 同步");
        }
        List<IntegrationRequests.TableRequest> tables = effectiveTables(task);
        if (tables.isEmpty()) throw new IllegalArgumentException("同步任务至少需要一张表");
        return realtime(task.syncMode()) ? buildRealtime(task, tables) : buildBatch(task, tables);
    }

    private String buildBatch(IntegrationTask task, List<IntegrationRequests.TableRequest> tables) {
        IntegrationRequests.Endpoint source = task.source();
        IntegrationRequests.Endpoint target = task.target();
        Map<String, Object> options = options(task);
        int parallelism = intOption(options, "parallelism", tables.size() > 10 ? 1 : 2);
        int fetchSize = intOption(options, "batchSize", 1000);
        String where = stringOption(options, "where", stringOption(options, "incrementalWhere", ""));
        String schemaSaveMode = schemaSaveMode(options);
        String dataSaveMode = dataSaveMode(options);
        boolean multiTable = tables.size() > 1;

        StringBuilder config = new StringBuilder();
        config.append("env {\n")
                .append("  parallelism = ").append(parallelism).append("\n")
                .append("  job.mode = \"BATCH\"\n")
                .append("}\n\nsource {\n");

        for (int index = 0; index < tables.size(); index++) {
            IntegrationRequests.TableRequest table = tables.get(index);
            config.append("  Jdbc {\n");
            if (multiTable) config.append("    plugin_output = \"table_").append(index).append("\"\n");
            config.append("    url = ").append(hocon(jdbcUrl(source, table.sourceDatabase()))).append("\n")
                    .append("    driver = \"com.mysql.cj.jdbc.Driver\"\n")
                    .append("    username = ").append(hocon(source.username())).append("\n")
                    .append("    password = ").append(hocon(source.password())).append("\n")
                    .append("    int_type_narrowing = false\n")
                    .append("    query = ").append(hocon(buildSourceQuery(table, task.mappings(), where))).append("\n")
                    .append("    fetch_size = ").append(fetchSize).append("\n")
                    .append("  }\n");
        }
        config.append("}\n\nsink {\n");
        for (int index = 0; index < tables.size(); index++) {
            IntegrationRequests.TableRequest table = tables.get(index);
            config.append("  StarRocks {\n");
            if (multiTable) config.append("    plugin_input = \"table_").append(index).append("\"\n");
            appendStarRocksEndpoint(config, target, table.targetDatabase(), table.targetTable(), options);
            appendStarRocksWriteOptions(config, schemaSaveMode, dataSaveMode, false, options);
            config.append("  }\n");
        }
        return config.append("}\n").toString();
    }

    private String buildRealtime(IntegrationTask task, List<IntegrationRequests.TableRequest> tables) {
        IntegrationRequests.Endpoint source = task.source();
        IntegrationRequests.Endpoint target = task.target();
        Map<String, Object> options = options(task);
        Set<String> sourceDatabases = new LinkedHashSet<>();
        Set<String> targetDatabases = new LinkedHashSet<>();
        for (IntegrationRequests.TableRequest table : tables) {
            sourceDatabases.add(table.sourceDatabase());
            targetDatabases.add(table.targetDatabase());
        }
        if (sourceDatabases.size() != 1) {
            throw new IllegalArgumentException("实时同步一个任务暂只支持同一个 MySQL 数据库，请按数据库拆分任务");
        }
        if (targetDatabases.size() != 1) {
            throw new IllegalArgumentException("实时同步一个任务暂只支持写入同一个 StarRocks 数据库，请拆分任务");
        }

        int parallelism = intOption(options, "parallelism", 2);
        int checkpointSeconds = intOption(options, "checkpointSeconds", 10);
        int snapshotSplitSize = intOption(options, "snapshotSplitSize", 16000);
        int snapshotFetchSize = intOption(options, "snapshotFetchSize", 5000);
        String startupMode = normalizeStartupMode(stringOption(options, "startupMode", "initial"));
        String startupTimestamp = stringOption(options, "startupTimestamp", "");
        String serverId = stringOption(options, "serverId", "");
        String serverTimeZone = stringOption(options, "serverTimeZone", "Asia/Shanghai");
        String schemaSaveMode = schemaSaveMode(options);
        String dataSaveMode = dataSaveMode(options);
        boolean renameTables = tables.size() > 1 && tables.stream().anyMatch(table -> !Objects.equals(table.sourceTable(), table.targetTable()));

        StringBuilder config = new StringBuilder();
        config.append("env {\n")
                .append("  parallelism = ").append(parallelism).append("\n")
                .append("  job.mode = \"STREAMING\"\n")
                .append("  checkpoint.interval = ").append(checkpointSeconds * 1000L).append("\n")
                .append("}\n\nsource {\n")
                .append("  MySQL-CDC {\n");
        if (renameTables) config.append("    plugin_output = \"mysql_cdc_source\"\n");
        config.append("    url = ").append(hocon(jdbcUrl(source, sourceDatabases.iterator().next()))).append("\n")
                .append("    username = ").append(hocon(source.username())).append("\n")
                .append("    password = ").append(hocon(source.password())).append("\n")
                .append("    table-names = [");
        for (int index = 0; index < tables.size(); index++) {
            if (index > 0) config.append(", ");
            IntegrationRequests.TableRequest table = tables.get(index);
            config.append(hocon(table.sourceDatabase() + "." + table.sourceTable()));
        }
        config.append("]\n")
                .append("    startup.mode = ").append(hocon(startupMode)).append("\n");
        if ("timestamp".equals(startupMode)) {
            if (startupTimestamp.isBlank()) throw new IllegalArgumentException("startupMode=timestamp 时必须填写 startupTimestamp（毫秒时间戳）");
            config.append("    startup.timestamp = ").append(parsePositiveLong(startupTimestamp, "startupTimestamp")).append("\n");
        }
        if (!serverId.isBlank()) config.append("    server-id = ").append(hocon(serverId)).append("\n");
        config.append("    server-time-zone = ").append(hocon(serverTimeZone)).append("\n")
                .append("    snapshot.split.size = ").append(snapshotSplitSize).append("\n")
                .append("    snapshot.fetch.size = ").append(snapshotFetchSize).append("\n")
                .append("    schema-changes.enabled = true\n")
                .append("  }\n")
                .append("}\n\n");

        if (renameTables) {
            config.append("transform {\n")
                    .append("  TableRename {\n")
                    .append("    plugin_input = \"mysql_cdc_source\"\n")
                    .append("    plugin_output = \"renamed_cdc_tables\"\n")
                    .append("    replacements_with_regex = [\n");
            for (int index = 0; index < tables.size(); index++) {
                if (index > 0) config.append(",\n");
                IntegrationRequests.TableRequest table = tables.get(index);
                config.append("      { replace_from = ")
                        .append(hocon("^" + Pattern.quote(table.sourceTable()) + "$"))
                        .append(", replace_to = ").append(hocon(table.targetTable())).append(" }");
            }
            config.append("\n    ]\n")
                    .append("  }\n")
                    .append("}\n\n");
        }

        config.append("sink {\n")
                .append("  StarRocks {\n");
        if (renameTables) config.append("    plugin_input = \"renamed_cdc_tables\"\n");
        String targetTable = tables.size() == 1 ? tables.get(0).targetTable() : "${table_name}";
        appendStarRocksEndpoint(config, target, targetDatabases.iterator().next(), targetTable, options);
        appendStarRocksWriteOptions(config, schemaSaveMode, dataSaveMode, true, options);
        return config.append("  }\n}\n").toString();
    }

    private void appendStarRocksEndpoint(StringBuilder config, IntegrationRequests.Endpoint target, String database,
                                         String table, Map<String, Object> options) {
        int httpPort = intOption(options, "starrocksHttpPort", 8030);
        String[] hosts = target.host().split(",");
        String firstHost = hosts[0].trim();
        config.append("    nodeUrls = [");
        for (int index = 0; index < hosts.length; index++) {
            if (index > 0) config.append(", ");
            config.append(hocon(hosts[index].trim() + ":" + httpPort));
        }
        config.append("]\n")
                .append("    base-url = ").append(hocon("jdbc:mysql://" + firstHost + ":" + target.port())).append("\n")
                .append("    username = ").append(hocon(target.username())).append("\n")
                .append("    password = ").append(hocon(target.password())).append("\n")
                .append("    database = ").append(hocon(database)).append("\n")
                .append("    table = ").append(hocon(table)).append("\n");
    }

    private void appendStarRocksWriteOptions(StringBuilder config, String schemaSaveMode, String dataSaveMode,
                                             boolean cdc, Map<String, Object> options) {
        config.append("    batch_max_rows = ").append(intOption(options, "batchMaxRows", 10240)).append("\n")
                .append("    batch_max_bytes = ").append(longOption(options, "batchMaxBytes", 52_428_800L)).append("\n")
                .append("    max_retries = ").append(intOption(options, "maxRetries", 5)).append("\n")
                .append("    retry_backoff_multiplier_ms = ").append(intOption(options, "retryBackoffMs", 200)).append("\n")
                .append("    max_retry_backoff_ms = ").append(intOption(options, "maxRetryBackoffMs", 60000)).append("\n")
                .append("    enable_upsert_delete = ").append(cdc).append("\n")
                .append("    schema_save_mode = ").append(hocon(schemaSaveMode)).append("\n")
                .append("    data_save_mode = ").append(hocon(dataSaveMode)).append("\n")
                .append("    starrocks.config = {\n")
                .append("      format = \"JSON\"\n")
                .append("      strip_outer_array = true\n")
                .append("    }\n");
    }

    private String buildSourceQuery(IntegrationRequests.TableRequest table, List<IntegrationRequests.FieldMapping> mappings, String where) {
        String projection = mappings == null || mappings.isEmpty()
                ? "*"
                : mappings.stream().map(mapping -> quoteIdentifier(mapping.source()) + " AS " + quoteIdentifier(mapping.target()))
                .reduce((left, right) -> left + ", " + right).orElse("*");
        String query = "SELECT " + projection + " FROM " + quoteIdentifier(table.sourceDatabase()) + "." + quoteIdentifier(table.sourceTable());
        if (!where.isBlank()) query += " WHERE " + where.trim();
        return query;
    }

    private String jdbcUrl(IntegrationRequests.Endpoint endpoint, String database) {
        return "jdbc:mysql://" + endpoint.host().split(",")[0].trim() + ":" + endpoint.port() + "/" + database
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&tinyInt1isBit=false";
    }

    private Map<String, Object> options(IntegrationTask task) { return task.options() == null ? Map.of() : task.options(); }
    private boolean realtime(String value) {
        String mode = value == null ? "" : value.trim().toUpperCase();
        return Set.of("REALTIME", "STREAMING", "CDC").contains(mode);
    }
    private String schemaSaveMode(Map<String, Object> options) {
        String value = stringOption(options, "schemaSaveMode", "CREATE_SCHEMA_WHEN_NOT_EXIST").toUpperCase();
        return Set.of("CREATE_SCHEMA_WHEN_NOT_EXIST", "RECREATE_SCHEMA", "ERROR_WHEN_SCHEMA_NOT_EXIST", "IGNORE").contains(value)
                ? value : "CREATE_SCHEMA_WHEN_NOT_EXIST";
    }
    private String dataSaveMode(Map<String, Object> options) {
        String value = stringOption(options, "dataSaveMode", "APPEND_DATA").toUpperCase();
        return Set.of("APPEND_DATA", "DROP_DATA", "ERROR_WHEN_DATA_EXISTS").contains(value) ? value : "APPEND_DATA";
    }
    private String normalizeStartupMode(String value) {
        String mode = value == null ? "initial" : value.trim().toLowerCase();
        return Set.of("initial", "earliest", "latest", "timestamp").contains(mode) ? mode : "initial";
    }
    private int intOption(Map<String, Object> options, String key, int fallback) {
        Object value = options.get(key);
        if (value instanceof Number number) return Math.max(1, number.intValue());
        try { return value == null ? fallback : Math.max(1, Integer.parseInt(value.toString())); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private long longOption(Map<String, Object> options, String key, long fallback) {
        Object value = options.get(key);
        if (value instanceof Number number) return Math.max(1L, number.longValue());
        try { return value == null ? fallback : Math.max(1L, Long.parseLong(value.toString())); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private String stringOption(Map<String, Object> options, String key, String fallback) {
        Object value = options.get(key);
        return value == null ? fallback : value.toString().trim();
    }
    private long parsePositiveLong(String value, String key) {
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " 必须是大于 0 的整数");
        }
    }
    private String quoteIdentifier(String value) { return "`" + String.valueOf(value).replace("`", "``") + "`"; }
    private String hocon(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    private List<IntegrationRequests.TableRequest> effectiveTables(IntegrationTask task) {
        if (task.tables() != null && !task.tables().isEmpty()) return task.tables();
        if (task.source().table() == null || task.target().table() == null) return List.of();
        return List.of(new IntegrationRequests.TableRequest(task.source().database(), task.source().table(),
                task.target().database(), task.target().table(), ""));
    }
}
