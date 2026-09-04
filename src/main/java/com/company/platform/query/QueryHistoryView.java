package com.company.platform.query;

import java.time.LocalDateTime;

public record QueryHistoryView(String queryId, Long dataSourceId, String databaseName, String sql,
                               String status, LocalDateTime startedAt, LocalDateTime finishedAt,
                               long elapsedMs, String errorMessage) { }
