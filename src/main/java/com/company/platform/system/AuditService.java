package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {
    private final PlatformStore store;
    public AuditService(PlatformStore store) { this.store = store; }
    public AuditLogView record(String action, String resourceType, Long resourceId, String detail, String operator) {
        if ((operator == null || operator.isBlank() || "admin".equalsIgnoreCase(operator))
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object current = attributes.getRequest().getAttribute("platform.operator");
            if (current instanceof String value && !value.isBlank()) operator = value;
        }
        AuditLogView log = new AuditLogView(store.nextId(), action, resourceType, resourceId, detail,
                operator == null || operator.isBlank() ? "admin" : operator, LocalDateTime.now());
        store.auditLogs.put(log.id(), log);
        store.operationLogs.put(log.id(), action + " " + (detail == null ? "" : detail));
        store.persistAudit(log);
        return log;
    }
    public List<AuditLogView> list() { return store.auditLogs.values().stream().sorted(java.util.Comparator.comparing(AuditLogView::createdAt).reversed()).toList(); }
}
