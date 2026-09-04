package com.company.platform.integration;

import java.util.List;
import java.util.Map;

public record IntegrationTask(String name, String sourceType, String targetType, String syncMode,
                              IntegrationRequests.Endpoint source, IntegrationRequests.Endpoint target,
                              List<IntegrationRequests.FieldMapping> mappings,
                              Map<String, Object> options) { }
