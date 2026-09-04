package com.company.platform.system;

import java.time.LocalDateTime;

public record AuditLogView(long id, String action, String resourceType, Long resourceId,
                           String detail, String operatorName, LocalDateTime createdAt) { }
