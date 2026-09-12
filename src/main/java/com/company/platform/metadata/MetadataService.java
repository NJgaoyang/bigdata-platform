package com.company.platform.metadata;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DynamicDataSourceManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;

@Service
public class MetadataService {
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;
    private final JdbcTemplate platformJdbc;

    public MetadataService(DataSourceService dataSources, DynamicDataSourceManager connectionManager, JdbcTemplate platformJdbc) {
        this.dataSources = dataSources;
        this.connectionManager = connectionManager;
        this.platformJdbc = platformJdbc;
    }

    public List<DatabaseView> databases(long dataSourceId, DataSourceType type) {
        ensureMetadataVisible(dataSourceId);
        if (type != DataSourceType.STARROCKS && type != DataSourceType.MYSQL) {
            throw new BadRequestException("当前数据源类型暂不支持元数据读取");
        }
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != type) {
            throw new BadRequestException("数据源类型与请求不一致");
        }
        try (Connection connection = connection(dataSourceId)) {
            List<DatabaseView> result = new ArrayList<>();
            try (ResultSet catalogs = connection.getMetaData().getCatalogs()) {
                while (catalogs.next()) {
                    String name = catalogs.getString("TABLE_CAT");
                    if (name != null && !name.isBlank() && !isSystemDatabase(name, type)) {
                        result.add(new DatabaseView(name, ""));
                    }
                }
            }
            return result.stream()
                    .sorted(Comparator.comparing(DatabaseView::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (SQLException ex) {
            throw metadataReadFailure("数据库");
        }
    }

    private boolean isSystemDatabase(String database, DataSourceType type) {
        String name = database.toLowerCase(Locale.ROOT);
        Set<String> systemDatabases = type == DataSourceType.MYSQL
                ? Set.of("information_schema", "mysql", "performance_schema", "sys", "ndbinfo")
                : Set.of("information_schema", "_statistics_", "sys");
        return systemDatabases.contains(name);
    }

    public List<TableView> tables(long dataSourceId, String database) {
        ensureMetadataVisible(dataSourceId);
        try (Connection connection = connection(dataSourceId)) {
            List<TableView> result = new ArrayList<>();
            try (ResultSet tables = connection.getMetaData().getTables(database, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (tables.next()) {
                    result.add(new TableView(database, tables.getString("TABLE_NAME"),
                            tables.getString("REMARKS"), tables.getString("TABLE_TYPE")));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw metadataReadFailure("表");
        }
    }

    public List<ColumnView> columns(long dataSourceId, String database, String table) {
        ensureMetadataVisible(dataSourceId);
        try (Connection connection = connection(dataSourceId)) {
            List<ColumnView> result = new ArrayList<>();
            try (ResultSet columns = connection.getMetaData().getColumns(database, null, table, "%")) {
                while (columns.next()) {
                    result.add(new ColumnView(columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME"),
                            columns.getInt("NULLABLE") != 0, columns.getString("REMARKS")));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw metadataReadFailure("字段");
        }
    }


    public TableProfileView tableProfile(long dataSourceId, String database, String table) {
        ensureMetadataVisible(dataSourceId);
        String db = requiredName(database, "数据库");
        String tableName = requiredName(table, "数据表");
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        String sizeExpression = info.type() == DataSourceType.MYSQL
                ? "COALESCE(DATA_LENGTH,0)+COALESCE(INDEX_LENGTH,0)"
                : "DATA_LENGTH";
        String sql = "SELECT TABLE_ROWS, " + sizeExpression + " AS ESTIMATED_SIZE, CREATE_TIME, UPDATE_TIME "
                + "FROM information_schema.tables WHERE TABLE_SCHEMA=? AND TABLE_NAME=? LIMIT 1";
        try (Connection connection = connection(dataSourceId); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, db);
            statement.setString(2, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new BadRequestException("数据表不存在或无权读取元数据");
                Long rowCount = nullableLong(rs, "TABLE_ROWS");
                Long estimatedSize = nullableLong(rs, "ESTIMATED_SIZE");
                LocalDateTime createTime = localDateTime(rs.getTimestamp("CREATE_TIME"));
                LocalDateTime updateTime = localDateTime(rs.getTimestamp("UPDATE_TIME"));
                return new TableProfileView(dataSourceId, db, tableName, rowCount, estimatedSize,
                        owner(dataSourceId, db, tableName), createTime, updateTime);
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw metadataReadFailure("表画像");
        }
    }

    public TableProfileView setTableOwner(long dataSourceId, String database, String table, String owner, String operator) {
        TableProfileView current = tableProfile(dataSourceId, database, table);
        String normalized = owner == null ? "" : owner.trim();
        if (normalized.length() > 128) throw new BadRequestException("拥有者不能超过128个字符");
        if (normalized.isBlank()) {
            platformJdbc.update("DELETE FROM metadata_table_owner WHERE data_source_id=? AND database_name=? AND table_name=?",
                    dataSourceId, current.database(), current.table());
        } else {
            platformJdbc.update("INSERT INTO metadata_table_owner(data_source_id,database_name,table_name,owner_name,updated_by) "
                            + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE owner_name=VALUES(owner_name),updated_by=VALUES(updated_by),updated_at=CURRENT_TIMESTAMP",
                    dataSourceId, current.database(), current.table(), normalized, operator);
        }
        return new TableProfileView(current.dataSourceId(), current.database(), current.table(), current.rowCount(),
                current.estimatedSizeBytes(), normalized.isBlank() ? null : normalized, current.createTime(), current.updateTime());
    }

    private String owner(long dataSourceId, String database, String table) {
        try {
            return platformJdbc.queryForObject("SELECT owner_name FROM metadata_table_owner WHERE data_source_id=? AND database_name=? AND table_name=?",
                    String.class, dataSourceId, database, table);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String requiredName(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new BadRequestException("请选择" + label);
        return normalized;
    }

    private Connection connection(long dataSourceId) throws SQLException {
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        return connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
    }

    private void ensureMetadataVisible(long dataSourceId) {
        if (!dataSources.get(dataSourceId).metadataVisible()) {
            throw new BadRequestException("该数据源未开启元数据展示");
        }
    }

    private BadRequestException metadataReadFailure(String object) {
        // Raw JDBC errors can contain infrastructure addresses and SQL-driver details.
        // Keep browser-facing metadata errors stable and non-sensitive.
        return new BadRequestException("读取" + object + "元数据失败，请检查数据源连接、账号权限和元数据可见性");
    }

    public record DatabaseView(String name, String comment) { }
    public record TableView(String database, String name, String comment, String type) { }
    public record ColumnView(String name, String dataType, boolean nullable, String comment) { }
    public record TableProfileView(long dataSourceId, String database, String table, Long rowCount,
                                   Long estimatedSizeBytes, String owner, LocalDateTime createTime,
                                   LocalDateTime updateTime) { }
}
