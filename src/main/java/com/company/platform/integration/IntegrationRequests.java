package com.company.platform.integration;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class IntegrationRequests {
    private IntegrationRequests() { }
    public record Endpoint(@NotBlank String host, int port, @NotBlank String database,
                           @NotBlank String username, String password, @NotBlank String table) { }
    public record FieldMapping(@NotBlank String source, @NotBlank String target) { }
    public record TaskRequest(@NotBlank String name, @NotBlank String sourceType,
                              @NotBlank String targetType, @NotBlank String syncMode,
                              Endpoint source, Endpoint target, List<FieldMapping> mappings,
                              Map<String, Object> options) { }
}
