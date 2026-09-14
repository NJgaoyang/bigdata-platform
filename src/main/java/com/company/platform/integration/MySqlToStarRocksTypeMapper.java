package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared MySQL -> StarRocks type policy used by offline SeaTunnel and realtime CDC prechecks. */
@Component
public class MySqlToStarRocksTypeMapper {
    private static final Pattern TYPE = Pattern.compile("^([a-zA-Z]+)(?:\\(([^)]*)\\))?.*$");

    public String map(String rawType) {
        if (rawType == null || rawType.isBlank()) return "STRING";
        String lower = rawType.toLowerCase(Locale.ROOT).trim();
        Matcher matcher = TYPE.matcher(lower);
        if (!matcher.matches()) return "STRING";
        String base = matcher.group(1);
        String args = matcher.group(2);
        boolean unsigned = lower.contains("unsigned");
        return switch (base) {
            case "tinyint" -> unsigned ? "SMALLINT" : "TINYINT"; // TINYINT(1) deliberately stays numeric.
            case "smallint" -> unsigned ? "INT" : "SMALLINT";
            case "mediumint" -> unsigned ? "BIGINT" : "INT";
            case "int", "integer" -> unsigned ? "BIGINT" : "INT";
            case "bigint" -> unsigned ? "LARGEINT" : "BIGINT";
            case "float" -> "FLOAT";
            case "double", "real" -> "DOUBLE";
            case "decimal", "numeric" -> decimal(args, rawType);
            case "char" -> args == null ? "CHAR(1)" : "CHAR(" + firstArg(args) + ")";
            case "varchar" -> args == null ? "VARCHAR(65533)" : "VARCHAR(" + firstArg(args) + ")";
            case "date" -> "DATE";
            case "datetime", "timestamp" -> "DATETIME";
            case "time" -> "VARCHAR(32)"; // duration/time-of-day semantics are not a timezone instant.
            case "year" -> "SMALLINT";
            case "json" -> "JSON";
            case "enum", "set" -> "VARCHAR(65533)";
            case "binary", "varbinary", "blob", "tinyblob", "mediumblob", "longblob" -> "VARBINARY";
            case "bit" -> "1".equals(args) ? "BOOLEAN" : "BIGINT";
            case "boolean", "bool" -> "BOOLEAN";
            default -> "STRING";
        };
    }

    private String decimal(String args, String rawType) {
        if (args == null || args.isBlank()) return "DECIMAL(38,9)";
        try {
            String[] values = args.split(",");
            int precision = Integer.parseInt(values[0].trim());
            int scale = values.length > 1 ? Integer.parseInt(values[1].trim()) : 0;
            if (precision < 1 || scale < 0 || scale > precision) {
                throw new BadRequestException("MySQL DECIMAL 定义无效：" + rawType);
            }
            if (precision > 38) {
                throw new BadRequestException("MySQL " + rawType + " 超出 StarRocks DECIMAL 最大精度 38，禁止静默截断；请显式改为 STRING 或调整源字段精度");
            }
            return "DECIMAL(" + precision + "," + scale + ")";
        } catch (NumberFormatException ex) {
            throw new BadRequestException("无法解析 MySQL DECIMAL 精度：" + rawType);
        }
    }

    private String firstArg(String args) {
        String value = args.split(",")[0].trim();
        try {
            int length = Integer.parseInt(value);
            if (length < 1) throw new NumberFormatException();
            return String.valueOf(Math.min(length, 65533));
        } catch (NumberFormatException ex) {
            throw new BadRequestException("无法解析字符字段长度：" + args);
        }
    }
}
