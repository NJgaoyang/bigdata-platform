package com.company.platform.integration;

import java.time.LocalDateTime;

public record IntegrationAttemptView(
        long id,
        long batchId,
        int attemptNo,
        String executionId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage,
        LocalDateTime createdAt
) { }
