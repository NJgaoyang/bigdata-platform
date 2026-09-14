package com.company.platform.datasource;

import java.time.LocalDateTime;

public record DataSourceView(long id, String name, DataSourceType type, String host, int port,
                             String databaseName, String timezone, String username, String status, boolean metadataVisible,
                             LocalDateTime lastCheckedAt, String lastCheckMessage) {
    public DataSourceView(long id, String name, DataSourceType type, String host, int port,
                          String databaseName, String username, String status, boolean metadataVisible,
                          LocalDateTime lastCheckedAt, String lastCheckMessage) {
        this(id, name, type, host, port, databaseName, "Asia/Shanghai", username, status, metadataVisible,
                lastCheckedAt, lastCheckMessage);
    }
}
