package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DynamicDataSourceManager;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class IntegrationMetadataService {
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;

    public IntegrationMetadataService(DataSourceService dataSources, DynamicDataSourceManager connectionManager) {
        this.dataSources = dataSources;
        this.connectionManager = connectionManager;
    }

    public List<DatabaseOption> mysqlDatabases(long dataSourceId) {
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.MYSQL) throw new BadRequestException("同步源数据库仅允许从 MySQL 业务数据源读取");
        try (Connection connection = connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
             ResultSet catalogs = connection.getMetaData().getCatalogs()) {
            List<DatabaseOption> result = new ArrayList<>();
            while (catalogs.next()) {
                String name = catalogs.getString("TABLE_CAT");
                if (name != null && !name.isBlank() && !isMysqlSystemDatabase(name)) {
                    result.add(new DatabaseOption(name, ""));
                }
            }
            return result.stream().sorted(java.util.Comparator.comparing(DatabaseOption::name, String.CASE_INSENSITIVE_ORDER)).toList();
        } catch (Exception ex) {
            throw new BadRequestException("读取业务库数据库列表失败：" + ex.getMessage());
        }
    }

    private boolean isMysqlSystemDatabase(String database) {
        return Set.of("information_schema", "mysql", "performance_schema", "sys", "ndbinfo")
                .contains(database.toLowerCase(Locale.ROOT));
    }

    public List<TableOption> mysqlTables(long dataSourceId) {
        return mysqlTables(dataSourceId, null);
    }

    public List<TableOption> mysqlTables(long dataSourceId, String database) {
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.MYSQL) throw new BadRequestException("同步源表仅允许从 MySQL 业务数据源读取");
        String selectedDatabase = database == null || database.isBlank() ? info.databaseName() : database.trim();
        if (selectedDatabase.isBlank()) throw new BadRequestException("请先选择源数据库");
        try (Connection connection = connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
             ResultSet tables = connection.getMetaData().getTables(selectedDatabase, null, "%", new String[]{"TABLE", "VIEW"})) {
            List<TableOption> result = new ArrayList<>();
            while (tables.next()) result.add(new TableOption(tables.getString("TABLE_NAME"), tables.getString("REMARKS")));
            return result.stream().sorted(java.util.Comparator.comparing(TableOption::name)).toList();
        } catch (Exception ex) {
            throw new BadRequestException("读取业务库同步表失败：" + ex.getMessage());
        }
    }

    public record DatabaseOption(String name, String comment) { }
    public record TableOption(String name, String comment) { }
}
