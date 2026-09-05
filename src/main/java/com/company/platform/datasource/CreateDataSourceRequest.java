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
        String password) { }
