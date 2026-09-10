package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares StarRocks target tables from the real MySQL source schema.
 * This ports the useful schema-management behavior from datasync-server while
 * deliberately running only immediately before execution, never on task save.
 */
@Service
public class StarRocksSchemaService {
    private static final Pattern TYPE = Pattern.compile("^([a-zA-Z]+)(?:\\(([^)]*)\\))?.*$");

    public List<SchemaResult> prepare(IntegrationTask task) {
        if (task == null || task.tables() == null || task.tables().isEmpty()) return List.of();
        String mode = schemaSaveMode(task.options());
        if ("IGNORE".equals(mode)) return List.of();

        IntegrationRequests.Endpoint target = task.target();
        List<SchemaResult> results = new ArrayList<>();
        try (Connection targetConnection = DriverManager.getConnection(jdbcRootUrl(target), target.username(), target.password())) {
            for (IntegrationRequests.TableRequest table : task.tables()) {
                requireDatabase(targetConnection, table.targetDatabase());
                boolean exists = tableExists(targetConnection, table.targetDatabase(), table.targetTable());
                if ("ERROR_WHEN_SCHEMA_NOT_EXIST".equals(mode)) {
                    if (!exists) throw new BadRequestException("目标表不存在：" + table.targetDatabase() + "." + table.targetTable());
                    results.add(new SchemaResult(table.targetDatabase(), table.targetTable(), "EXISTS", List.of()));
                    continue;
                }

                List<SourceColumn> sourceColumns = sourceColumns(task.source(), table.sourceDatabase(), table.sourceTable());
                if (sourceColumns.isEmpty()) throw new BadRequestException("源表没有可同步字段：" + table.sourceDatabase() + "." + table.sourceTable());
                if ("RECREATE_SCHEMA".equals(mode)) {
                    try (Statement statement = targetConnection.createStatement()) {
                        statement.execute("DROP TABLE IF EXISTS " + identifier(table.targetDatabase()) + "." + identifier(table.targetTable()));
                        statement.execute(createTableDdl(table.targetDatabase(), table.targetTable(), sourceColumns));
                    }
                    results.add(new SchemaResult(table.targetDatabase(), table.targetTable(), "RECREATED", sourceColumns.stream().map(SourceColumn::name).toList()));
                } else if (!exists) {
                    try (Statement statement = targetConnection.createStatement()) {
                        statement.execute(createTableDdl(table.targetDatabase(), table.targetTable(), sourceColumns));
                    }
                    results.add(new SchemaResult(table.targetDatabase(), table.targetTable(), "CREATED", sourceColumns.stream().map(SourceColumn::name).toList()));
                } else {
                    List<String> added = addMissingColumns(targetConnection, table.targetDatabase(), table.targetTable(), sourceColumns);
                    results.add(new SchemaResult(table.targetDatabase(), table.targetTable(), added.isEmpty() ? "MATCHED" : "ALTERED", added));
                }
            }
            return results;
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("准备 StarRocks 目标表结构失败，请检查目标库、账号权限和表结构兼容性");
        }
    }

    public String previewDdl(IntegrationTask task, IntegrationRequests.TableRequest table) {
        List<SourceColumn> columns = sourceColumns(task.source(), table.sourceDatabase(), table.sourceTable());
        return createTableDdl(table.targetDatabase(), table.targetTable(), columns);
    }

    private List<SourceColumn> sourceColumns(IntegrationRequests.Endpoint source, String database, String table) {
        String url = jdbcDatabaseUrl(source, database);
        try (Connection connection = DriverManager.getConnection(url, source.username(), source.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW FULL COLUMNS FROM " + identifier(database) + "." + identifier(table))) {
            List<SourceColumn> result = new ArrayList<>();
            while (rs.next()) {
                String mysqlType = rs.getString("Type");
                result.add(new SourceColumn(rs.getString("Field"), mysqlType, mysqlToStarRocks(mysqlType),
                        "PRI".equalsIgnoreCase(rs.getString("Key")), "YES".equalsIgnoreCase(rs.getString("Null")),
                        rs.getString("Comment")));
            }
            return result;
        } catch (Exception ex) {
            throw new BadRequestException("读取 MySQL 源表结构失败：" + database + "." + table);
        }
    }

    private List<String> addMissingColumns(Connection connection, String database, String table, List<SourceColumn> sourceColumns) throws Exception {
        Set<String> existing = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW FULL COLUMNS FROM " + identifier(database) + "." + identifier(table))) {
            while (rs.next()) existing.add(rs.getString("Field").toLowerCase(Locale.ROOT));
        }
        List<String> added = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            for (SourceColumn column : sourceColumns) {
                if (existing.contains(column.name().toLowerCase(Locale.ROOT))) continue;
                if (column.primaryKey()) {
                    throw new BadRequestException("目标表已存在但缺少源主键字段，不能安全自动补列：" + column.name());
                }
                statement.execute("ALTER TABLE " + identifier(database) + "." + identifier(table)
                        + " ADD COLUMN " + columnDefinition(column));
                added.add(column.name());
            }
        }
        return added;
    }

    private String createTableDdl(String database, String table, List<SourceColumn> sourceColumns) {
        if (sourceColumns == null || sourceColumns.isEmpty()) throw new BadRequestException("无法为无字段表生成 StarRocks DDL");
        List<SourceColumn> ordered = new ArrayList<>();
        sourceColumns.stream().filter(SourceColumn::primaryKey).forEach(ordered::add);
        sourceColumns.stream().filter(column -> !column.primaryKey()).forEach(ordered::add);
        List<String> keys = ordered.stream().filter(SourceColumn::primaryKey).map(SourceColumn::name).toList();
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(identifier(database)).append('.').append(identifier(table)).append(" (\n");
        for (int index = 0; index < ordered.size(); index++) {
            ddl.append("  ").append(columnDefinition(ordered.get(index)));
            ddl.append(index + 1 < ordered.size() ? ",\n" : "\n");
        }
        ddl.append(") ENGINE=OLAP\n");
        if (!keys.isEmpty()) {
            ddl.append("PRIMARY KEY(");
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) ddl.append(", ");
                ddl.append(identifier(keys.get(index)));
            }
            ddl.append(")\n");
        }
        String distribution = ordered.stream().filter(column -> hashable(column.starRocksType())).findFirst().orElse(ordered.get(0)).name();
        ddl.append("DISTRIBUTED BY HASH(").append(identifier(distribution)).append(") BUCKETS 3");
        return ddl.toString();
    }

    private String columnDefinition(SourceColumn column) {
        StringBuilder value = new StringBuilder(identifier(column.name())).append(' ').append(column.starRocksType());
        if (column.primaryKey()) value.append(" NOT NULL");
        else if (!column.nullable()) value.append(" NULL");
        if (column.comment() != null && !column.comment().isBlank()) value.append(" COMMENT '").append(escapeComment(column.comment())).append("'");
        return value.toString();
    }

    private String mysqlToStarRocks(String rawType) {
        if (rawType == null || rawType.isBlank()) return "STRING";
        String lower = rawType.toLowerCase(Locale.ROOT).trim();
        Matcher matcher = TYPE.matcher(lower);
        if (!matcher.matches()) return "STRING";
        String base = matcher.group(1);
        String args = matcher.group(2);
        boolean unsigned = lower.contains("unsigned");
        return switch (base) {
            case "tinyint" -> unsigned ? "SMALLINT" : "TINYINT";
            case "smallint" -> unsigned ? "INT" : "SMALLINT";
            case "mediumint" -> unsigned ? "BIGINT" : "INT";
            case "int", "integer" -> unsigned ? "BIGINT" : "INT";
            case "bigint" -> unsigned ? "LARGEINT" : "BIGINT";
            case "float" -> "FLOAT";
            case "double", "real" -> "DOUBLE";
            case "decimal", "numeric" -> decimal(args);
            case "char" -> args == null ? "CHAR(1)" : "CHAR(" + args.split(",")[0].trim() + ")";
            case "varchar" -> args == null ? "VARCHAR(65533)" : "VARCHAR(" + args.split(",")[0].trim() + ")";
            case "date" -> "DATE";
            case "datetime", "timestamp" -> "DATETIME";
            case "year" -> "SMALLINT";
            case "json" -> "JSON";
            case "bit" -> "1".equals(args) ? "BOOLEAN" : "BIGINT";
            case "boolean", "bool" -> "BOOLEAN";
            default -> "STRING";
        };
    }

    private String decimal(String args) {
        if (args == null || args.isBlank()) return "DECIMAL(38,9)";
        try {
            String[] values = args.split(",");
            int precision = Math.min(38, Math.max(1, Integer.parseInt(values[0].trim())));
            int scale = values.length > 1 ? Math.max(0, Integer.parseInt(values[1].trim())) : 0;
            scale = Math.min(scale, precision);
            return "DECIMAL(" + precision + "," + scale + ")";
        } catch (NumberFormatException ignored) {
            return "DECIMAL(38,9)";
        }
    }

    private void requireDatabase(Connection connection, String database) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SHOW DATABASES LIKE " + literal(database))) {
            if (!rs.next()) throw new BadRequestException("StarRocks 目标数据库不存在，请先创建：" + database);
        }
    }
    private boolean tableExists(Connection connection, String database, String table) {
        try (Statement statement = connection.createStatement();
             ResultSet ignored = statement.executeQuery("SHOW FULL COLUMNS FROM " + identifier(database) + "." + identifier(table))) {
            return true;
        } catch (Exception ex) { return false; }
    }
    private String schemaSaveMode(Map<String, Object> options) {
        Object raw = options == null ? null : options.get("schemaSaveMode");
        String value = raw == null ? "CREATE_SCHEMA_WHEN_NOT_EXIST" : raw.toString().trim().toUpperCase(Locale.ROOT);
        return Set.of("CREATE_SCHEMA_WHEN_NOT_EXIST", "RECREATE_SCHEMA", "ERROR_WHEN_SCHEMA_NOT_EXIST", "IGNORE").contains(value)
                ? value : "CREATE_SCHEMA_WHEN_NOT_EXIST";
    }
    private boolean hashable(String type) {
        String value = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return !value.startsWith("JSON") && !value.startsWith("FLOAT") && !value.startsWith("DOUBLE") && !value.startsWith("STRING");
    }
    private String jdbcDatabaseUrl(IntegrationRequests.Endpoint endpoint, String database) {
        return jdbcAuthority(endpoint) + "/" + database + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&tinyInt1isBit=false&allowPublicKeyRetrieval=true";
    }
    private String jdbcRootUrl(IntegrationRequests.Endpoint endpoint) {
        return jdbcAuthority(endpoint) + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    }
    private String jdbcAuthority(IntegrationRequests.Endpoint endpoint) {
        String host = endpoint.host().split(",")[0].trim();
        return "jdbc:mysql://" + host + ":" + endpoint.port();
    }
    private String identifier(String value) { return "`" + String.valueOf(value).replace("`", "``") + "`"; }
    private String literal(String value) { return "'" + String.valueOf(value).replace("\\", "\\\\").replace("'", "''") + "'"; }
    private String escapeComment(String value) { return String.valueOf(value).replace("\\", "\\\\").replace("'", "''"); }

    public record SourceColumn(String name, String mysqlType, String starRocksType, boolean primaryKey,
                               boolean nullable, String comment) { }
    public record SchemaResult(String database, String table, String action, List<String> columns) { }
}
