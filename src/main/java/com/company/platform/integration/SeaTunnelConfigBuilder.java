package com.company.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SeaTunnelConfigBuilder {
    /** Retained for Spring and existing callers; SeaTunnel now emits HOCON directly. */
    public SeaTunnelConfigBuilder(ObjectMapper ignored) { }

    public String build(IntegrationTask task) {
        var source = task.source();
        var target = task.target();
        Map<String, Object> options = task.options() == null ? Map.of() : task.options();
        List<IntegrationRequests.TableRequest> tables = effectiveTables(task);
        if (tables.isEmpty()) return "";
        int parallelism = integerOption(options, "parallelism", 1);
        int batchSize = integerOption(options, "batchSize", 1000);
        String where = stringOption(options, "where", "");
        String targetCompatibility = task.targetType().equalsIgnoreCase("STARROCKS")
                ? "    compatible_mode = \"StarRocks\"\n    enable_upsert = false\n" : "";
        boolean multiTable = tables.size() > 1;
        StringBuilder config = new StringBuilder();
        config.append("env {\n  parallelism = ").append(parallelism).append("\n  job.mode = \"BATCH\"\n}\n\nsource {\n");
        for (int index = 0; index < tables.size(); index++) {
            IntegrationRequests.TableRequest table = tables.get(index);
            String pluginOutput = multiTable ? "    plugin_output = \"table_" + index + "\"\n" : "";
            String sourceQuery = buildSourceQuery(table, task.mappings(), where);
            config.append("  Jdbc {\n").append(pluginOutput)
                    .append("    url = \"").append(quote(jdbcUrl(task.sourceType(), source, table.sourceDatabase())))
                    .append("\"\n    driver = \"com.mysql.cj.jdbc.Driver\"\n    username = \"")
                    .append(quote(source.username())).append("\"\n    password = \"").append(quote(source.password()))
                    .append("\"\n    query = \"").append(quote(sourceQuery)).append("\"\n    fetch_size = ")
                    .append(batchSize).append("\n  }\n");
        }
        config.append("}\n\ntransform {\n}\n\nsink {\n");
        for (int index = 0; index < tables.size(); index++) {
            IntegrationRequests.TableRequest table = tables.get(index);
            String pluginInput = multiTable ? "    plugin_input = \"table_" + index + "\"\n" : "";
            config.append("  Jdbc {\n").append(pluginInput)
                    .append("    url = \"").append(quote(jdbcBaseUrl(task.targetType(), target))).append("\"\n")
                    .append("    driver = \"com.mysql.cj.jdbc.Driver\"\n    username = \"")
                    .append(quote(target.username())).append("\"\n    password = \"").append(quote(target.password()))
                    .append("\"\n").append(targetCompatibility)
                    .append("    database = \"").append(quote(table.targetDatabase())).append("\"\n    table = \"")
                    .append(quote(table.targetTable())).append("\"\n    generate_sink_sql = true\n  }\n");
        }
        return config.append("}\n").toString();
    }

    private String buildSourceQuery(IntegrationRequests.TableRequest table, List<IntegrationRequests.FieldMapping> mappings, String where) {
        String projection = mappings == null || mappings.isEmpty()
                ? "*"
                : mappings.stream().map(mapping -> quoteIdentifier(mapping.source()) + " AS " + quoteIdentifier(mapping.target()))
                .reduce((left, right) -> left + ", " + right).orElse("*");
        String query = "SELECT " + projection + " FROM " + quoteIdentifier(table.sourceDatabase()) + "." + quoteIdentifier(table.sourceTable());
        if (!where.isBlank()) query += " WHERE " + where;
        return query;
    }

    private String quoteIdentifier(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private int integerOption(Map<String, Object> options, String key, int fallback) {
        Object value = options.get(key);
        if (value instanceof Number number) return Math.max(1, number.intValue());
        try { return value == null ? fallback : Math.max(1, Integer.parseInt(value.toString())); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private String stringOption(Map<String, Object> options, String key, String fallback) {
        Object value = options.get(key);
        return value == null ? fallback : value.toString().trim();
    }

    private String jdbcUrl(String type, IntegrationRequests.Endpoint endpoint) {
        return jdbcUrl(type, endpoint, endpoint.database());
    }

    private String jdbcUrl(String type, IntegrationRequests.Endpoint endpoint, String database) {
        return jdbcBaseUrl(type, endpoint) + "/" + quote(database)
                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false";
    }

    private String jdbcBaseUrl(String type, IntegrationRequests.Endpoint endpoint) {
        if (!type.equalsIgnoreCase("MYSQL") && !type.equalsIgnoreCase("STARROCKS")) {
            throw new IllegalArgumentException("当前仅支持 MySQL 与 StarRocks JDBC 同步：" + type);
        }
        return "jdbc:mysql://" + quote(endpoint.host()) + ":" + endpoint.port();
    }

    private String quote(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<IntegrationRequests.TableRequest> effectiveTables(IntegrationTask task) {
        if (task.tables() != null) return task.tables();
        if (task.source() == null || task.target() == null || task.source().table() == null || task.target().table() == null) return List.of();
        return List.of(new IntegrationRequests.TableRequest(task.source().database(), task.source().table(),
                task.target().database(), task.target().table(), ""));
    }
}
