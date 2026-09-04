package com.company.platform.integration;

import java.time.LocalDateTime;

public record IntegrationInstanceView(long id, long taskId, String executionId, String status,
                                      LocalDateTime startedAt, LocalDateTime finishedAt, String message) { }
