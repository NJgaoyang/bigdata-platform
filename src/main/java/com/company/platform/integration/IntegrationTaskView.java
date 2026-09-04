package com.company.platform.integration;

public record IntegrationTaskView(long id, String name, String sourceType, String targetType,
                                  String syncMode, String status, String seatunnelConfig) { }
