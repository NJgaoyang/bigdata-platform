package com.company.platform.integration;

import java.time.LocalDateTime;

public record IntegrationBatchView(
        long id,
        long taskId,
        String batchCode,
        String triggerType,
        String status,
        Long clusterId,
        String parametersJson,
        Long sourceBatchId,
        String createdBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage,
        LocalDateTime createdAt
) { }
