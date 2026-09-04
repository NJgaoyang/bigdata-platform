package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {
    private final PlatformStore store;
    public AuditService(PlatformStore store) { this.store = store; }
    public AuditLogView record(String action, String resourceType, Long resourceId, String detail, String operator) {
        AuditLogView log = new AuditLogView(store.nextId(), action, resourceType, resourceId, detail,
                operator == null || operator.isBlank() ? "admin" : operator, LocalDateTime.now());
        store.auditLogs.put(log.id(), log);
        store.operationLogs.put(log.id(), action + " " + (detail == null ? "" : detail));
        store.persistAudit(log);
        return log;
    }
    public List<AuditLogView> list() { return store.auditLogs.values().stream().toList(); }
}
