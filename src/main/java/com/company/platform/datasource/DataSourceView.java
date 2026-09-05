package com.company.platform.datasource;

public record DataSourceView(long id, String name, DataSourceType type, String host, int port,
                             String databaseName, String username, String status, boolean metadataVisible) { }
