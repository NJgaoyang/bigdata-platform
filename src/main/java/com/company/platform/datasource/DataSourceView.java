package com.company.platform.datasource;

import java.time.LocalDateTime;

public record DataSourceView(long id, String name, DataSourceType type, String host, int port,
                             String databaseName, String username, String status, boolean metadataVisible,
                             LocalDateTime lastCheckedAt, String lastCheckMessage) { }
