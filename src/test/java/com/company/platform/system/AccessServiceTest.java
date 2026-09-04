package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessServiceTest {
    @Test
    void recordsPermissionGrantAndAuditEvent() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        RoleView role = service.createRole(new AccessRequests.RoleRequest("developer", "数据开发者"));
        RoleView updated = service.grant(role.id(), new AccessRequests.PermissionRequest("SQL_EXECUTE"));
        service.createUser(new AccessRequests.UserRequest("alice", "Alice"));

        assertTrue(updated.permissions().contains("SQL_EXECUTE"));
        assertTrue(store.auditLogs.size() >= 3);
    }
}
