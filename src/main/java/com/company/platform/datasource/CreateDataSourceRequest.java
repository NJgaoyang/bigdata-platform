package com.company.platform.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDataSourceRequest(
        @NotBlank String name,
        @NotNull DataSourceType type,
        @NotBlank String host,
        int port,
        String databaseName,
        @NotBlank String username,
        String password,
        Boolean metadataVisible) {

    /** Backwards-compatible constructor for callers that use the original request shape. */
    public CreateDataSourceRequest(String name, DataSourceType type, String host, int port,
                                   String databaseName, String username, String password) {
        this(name, type, host, port, databaseName, username, password, null);
    }
}
