package com.company.platform.integration;

import java.util.List;

public record IntegrationTaskView(long id, String name, String sourceType, String targetType,
                                  String syncMode, String status, String lifecycleStatus, String sourceConfigJson,
                                  String targetConfigJson, String transformConfigJson, String seatunnelConfig,
                                  List<IntegrationTableView> tables) {
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String sourceConfigJson,
                               String targetConfigJson, String transformConfigJson, String seatunnelConfig,
                               List<IntegrationTableView> tables) {
        this(id, name, sourceType, targetType, syncMode, status, "ONLINE", sourceConfigJson, targetConfigJson,
                transformConfigJson, seatunnelConfig, tables);
    }
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String seatunnelConfig) {
        this(id, name, sourceType, targetType, syncMode, status, "ONLINE", "{}", "{}", "{}", seatunnelConfig, List.of());
    }
    public IntegrationTaskView(long id, String name, String sourceType, String targetType,
                               String syncMode, String status, String sourceConfigJson,
                               String targetConfigJson, String transformConfigJson, String seatunnelConfig) {
        this(id, name, sourceType, targetType, syncMode, status, "ONLINE", sourceConfigJson, targetConfigJson,
                transformConfigJson, seatunnelConfig, List.of());
    }
}
