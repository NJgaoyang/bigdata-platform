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

@Service
public class IntegrationMetadataService {
    private final DataSourceService dataSources;
    private final DynamicDataSourceManager connectionManager;

    public IntegrationMetadataService(DataSourceService dataSources, DynamicDataSourceManager connectionManager) {
        this.dataSources = dataSources;
        this.connectionManager = connectionManager;
    }

    public List<TableOption> mysqlTables(long dataSourceId) {
        DataSourceService.ConnectionInfo info = dataSources.connectionInfo(dataSourceId);
        if (info.type() != DataSourceType.MYSQL) throw new BadRequestException("同步源表仅允许从 MySQL 业务数据源读取");
        try (Connection connection = connectionManager.getConnection(info.id(), info.jdbcUrl(), info.username(), info.password());
             ResultSet tables = connection.getMetaData().getTables(info.databaseName(), null, "%", new String[]{"TABLE", "VIEW"})) {
            List<TableOption> result = new ArrayList<>();
            while (tables.next()) result.add(new TableOption(tables.getString("TABLE_NAME"), tables.getString("REMARKS")));
            return result.stream().sorted(java.util.Comparator.comparing(TableOption::name)).toList();
        } catch (Exception ex) {
            throw new BadRequestException("读取业务库同步表失败：" + ex.getMessage());
        }
    }

    public record TableOption(String name, String comment) { }
}
