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

@Service
public class MetadataService {
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;

    public MetadataService(DataSourceService dataSources, DynamicDataSourceManager connectionManager) {
        this.dataSources = dataSources;
        this.connectionManager = connectionManager;
    }

    public List<DatabaseView> databases(long dataSourceId, DataSourceType type) {
        if (type != DataSourceType.STARROCKS) {
            throw new BadRequestException("数据库元数据仅允许查看 StarRocks 数据源");
        }
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.STARROCKS) {
            throw new BadRequestException("该数据源不是 StarRocks 数据源");
        }
        try (Connection connection = connection(dataSourceId)) {
            List<DatabaseView> result = new ArrayList<>();
            try (ResultSet catalogs = connection.getMetaData().getCatalogs()) {
                while (catalogs.next()) {
                    String name = catalogs.getString("TABLE_CAT");
                    if (name != null && !name.isBlank()) result.add(new DatabaseView(name, ""));
                }
            }
            return result.stream()
                    .sorted(Comparator.comparing(DatabaseView::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (SQLException ex) {
            throw new BadRequestException("读取数据库元数据失败：" + ex.getMessage());
        }
    }

    public List<TableView> tables(long dataSourceId, String database) {
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
            throw new BadRequestException("读取表元数据失败：" + ex.getMessage());
        }
    }

    public List<ColumnView> columns(long dataSourceId, String database, String table) {
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
            throw new BadRequestException("读取字段元数据失败：" + ex.getMessage());
        }
    }

    private Connection connection(long dataSourceId) throws SQLException {
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        return connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
    }
    public record DatabaseView(String name, String comment) { }
    public record TableView(String database, String name, String comment, String type) { }
    public record ColumnView(String name, String dataType, boolean nullable, String comment) { }
}
