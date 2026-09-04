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
        int parallelism = integerOption(options, "parallelism", 1);
        int batchSize = integerOption(options, "batchSize", 1000);
        String where = stringOption(options, "where", "");
        String sourceQuery = buildSourceQuery(source, task.mappings(), where);
        String targetCompatibility = task.targetType().equalsIgnoreCase("STARROCKS")
                ? "    compatible_mode = \"StarRocks\"\n    enable_upsert = false\n" : "";
        return """
                env {
                  parallelism = %d
                  job.mode = \"BATCH\"
                }
                source {
                  Jdbc {
                    url = \"%s\"
                    driver = \"com.mysql.cj.jdbc.Driver\"
                    username = \"%s\"
                    password = \"%s\"
                    query = \"%s\"
                    fetch_size = %d
                  }
                }
                transform {
                }
                sink {
                  Jdbc {
                    url = \"%s\"
                    driver = \"com.mysql.cj.jdbc.Driver\"
                    username = \"%s\"
                    password = \"%s\"
                %s    database = \"%s\"
                    table = \"%s\"
                    generate_sink_sql = true
                  }
                }
                """.formatted(parallelism, jdbcUrl(task.sourceType(), source), quote(source.username()), quote(source.password()),
                quote(sourceQuery), batchSize, jdbcBaseUrl(task.targetType(), target),
                quote(target.username()), quote(target.password()), targetCompatibility,
                quote(target.database()), quote(target.table()));
    }

    private String buildSourceQuery(IntegrationRequests.Endpoint source, List<IntegrationRequests.FieldMapping> mappings, String where) {
        String projection = mappings == null || mappings.isEmpty()
                ? "*"
                : mappings.stream().map(mapping -> quoteIdentifier(mapping.source()) + " AS " + quoteIdentifier(mapping.target()))
                .reduce((left, right) -> left + ", " + right).orElse("*");
        String query = "SELECT " + projection + " FROM " + quoteIdentifier(source.database()) + "." + quoteIdentifier(source.table());
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
        return jdbcBaseUrl(type, endpoint) + "/" + quote(endpoint.database())
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
}
