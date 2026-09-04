package com.company.platform.integration;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class IntegrationRequests {
    private IntegrationRequests() { }
    public record Endpoint(@NotBlank String host, int port, @NotBlank String database,
                           @NotBlank String username, String password, String table) { }
    public record TableRequest(@NotBlank String sourceDatabase, @NotBlank String sourceTable,
                               @NotBlank String targetDatabase, @NotBlank String targetTable,
                               String partitionColumn) { }
    public record FieldMapping(@NotBlank String source, @NotBlank String target) { }
    public record TaskRequest(@NotBlank String name, @NotBlank String sourceType,
                              @NotBlank String targetType, @NotBlank String syncMode,
                              Endpoint source, Endpoint target, List<FieldMapping> mappings,
                              Map<String, Object> options, List<TableRequest> tables) {
        public TaskRequest(String name, String sourceType, String targetType, String syncMode,
                           Endpoint source, Endpoint target, List<FieldMapping> mappings,
                           Map<String, Object> options) {
            this(name, sourceType, targetType, syncMode, source, target, mappings, options, null);
        }
    }
}
