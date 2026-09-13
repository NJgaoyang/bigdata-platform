package com.company.platform.integration;

import java.time.LocalDateTime;

public record IntegrationCursorView(
        long taskId,
        String cursorColumn,
        String cursorValue,
        LocalDateTime updatedAt
) { }
