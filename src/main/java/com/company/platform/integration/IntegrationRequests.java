package com.company.platform.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class IntegrationRequests {
    private IntegrationRequests() { }
    public record Endpoint(@NotBlank String host, @Min(1) @Max(65535) int port, @NotBlank String database,
                           @NotBlank String username, String password, String table) { }
    public record TableRequest(@NotBlank String sourceDatabase, @NotBlank String sourceTable,
                               @NotBlank String targetDatabase, @NotBlank String targetTable,
                               String partitionColumn) { }
    public record FieldMapping(@NotBlank String source, @NotBlank String target) { }
    public record TaskRequest(@NotBlank String name, @NotBlank String sourceType,
                              @NotBlank String targetType, @NotBlank String syncMode,
                              @Valid @NotNull Endpoint source, @Valid @NotNull Endpoint target,
                              List<@Valid FieldMapping> mappings, Map<String, Object> options,
                              List<@Valid TableRequest> tables, Long sourceDataSourceId, Long targetDataSourceId) {
        public TaskRequest(String name, String sourceType, String targetType, String syncMode,
                           Endpoint source, Endpoint target, List<FieldMapping> mappings,
                           Map<String, Object> options, List<TableRequest> tables) {
            this(name, sourceType, targetType, syncMode, source, target, mappings, options, tables, null, null);
        }
        public TaskRequest(String name, String sourceType, String targetType, String syncMode,
                           Endpoint source, Endpoint target, List<FieldMapping> mappings,
                           Map<String, Object> options) {
            this(name, sourceType, targetType, syncMode, source, target, mappings, options, null, null, null);
        }
    }
}
