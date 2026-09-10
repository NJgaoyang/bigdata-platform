package com.company.platform.metadata;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DynamicDataSourceManager;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MetadataService {
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;

    public MetadataService(DataSourceService dataSources, DynamicDataSourceManager connectionManager) {
        this.dataSources = dataSources;
        this.connectionManager = connectionManager;
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
}
