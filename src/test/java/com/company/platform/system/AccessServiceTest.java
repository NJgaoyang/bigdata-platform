package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void newUserPasswordUsesSaltedPbkdf2() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        UserView user = service.createUser(new AccessRequests.UserRequest("alice", "Alice", "secret", "USER", "ACTIVE"));
        assertTrue(user.passwordHash().startsWith("pbkdf2-sha256$"));
    }

    @Test
    void protectsBuiltInAdminButAllowsNormalUserLifecycle() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        UserView admin = new UserView(1L, "admin", "平台管理员", "ADMIN", "ACTIVE", java.time.LocalDateTime.now(), null);
        store.users.put(admin.id(), admin);
        assertThrows(BadRequestException.class, () -> service.setUserStatus(admin.id(), "DISABLED"));
        assertThrows(BadRequestException.class, () -> service.deleteUser(admin.id()));
        assertThrows(BadRequestException.class, () -> service.updateUser(admin.id(), new AccessRequests.UserUpdateRequest("admin", "平台管理员", "", "USER", "ACTIVE")));
        UserView user = service.createUser(new AccessRequests.UserRequest("alice", "Alice", "secret", "USER", "ACTIVE"));
        assertEquals("DISABLED", service.setUserStatus(user.id(), "DISABLED").status());
        assertEquals("ACTIVE", service.setUserStatus(user.id(), "ACTIVE").status());
        service.deleteUser(user.id());
        assertFalse(store.users.containsKey(user.id()));
    }

    @Test
    void disablingUserInvalidatesExistingSession() {
        PlatformStore store = new PlatformStore();
        PlatformProperties properties = new PlatformProperties();
        properties.getSecurity().setEnabled(true);
        AuthService auth = new AuthService(properties, store);
        AccessService service = new AccessService(store, new AuditService(store));
        service.setAuthService(auth);
        UserView user = service.createUser(new AccessRequests.UserRequest("bob", "Bob", "secret", "USER", "ACTIVE"));
        AuthService.AuthSession session = auth.login(new AuthRequests.LoginRequest("bob", "secret"));
        service.setUserStatus(user.id(), "DISABLED");
        assertFalse(auth.authenticate(session.token()));
    }

    @Test
    void rejectsUnknownProjectPermissionCode() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        UserView user = service.createUser(new AccessRequests.UserRequest("bob", "Bob"));
        long projectId = store.projects.keySet().iterator().next();
        assertThrows(BadRequestException.class, () -> service.grantProjectPermission(projectId,
                new AccessRequests.PermissionBindingRequest(user.id(), "OWNER")));
    }
}
