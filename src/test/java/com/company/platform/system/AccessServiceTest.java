package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.DataSphereProperties;
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
        DataSphereProperties properties = new DataSphereProperties();
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

    @Test
    void rolePermissionsApplyUntilUserGetsExplicitOverride() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        RoleView role = service.createRole(new AccessRequests.RoleRequest("DEVELOPER", "开发者"));
        service.setRolePermissions(role.id(), java.util.Set.of("WORKBENCH_VIEW", "DATA_DEVELOPMENT_VIEW", "DATA_DEVELOPMENT_EDIT"));
        UserView user = service.createUser(new AccessRequests.UserRequest("dev", "Dev", "secret", "DEVELOPER", "ACTIVE"));
        assertTrue(service.effectivePermissions(user.id()).contains("DATA_DEVELOPMENT_EDIT"));
        assertFalse(service.effectivePermissions(user.id()).contains("SYSTEM_SETTINGS_VIEW"));
        service.setPermissions(user.id(), java.util.Set.of("WORKBENCH_VIEW"));
        assertEquals(java.util.Set.of("WORKBENCH_VIEW"), service.effectivePermissions(user.id()));
    }

    @Test
    void datasourcePermissionsCanBeListedAndRevoked() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        UserView user = service.createUser(new AccessRequests.UserRequest("analyst", "Analyst"));
        long dataSourceId = 77L;
        store.dataSources.put(dataSourceId, new com.company.platform.datasource.DataSourceView(dataSourceId, "orders", com.company.platform.datasource.DataSourceType.MYSQL,
                "127.0.0.1", 3306, "app", "root", "ACTIVE", true, null, null));
        service.grantDatasourcePermission(dataSourceId, new AccessRequests.PermissionBindingRequest(user.id(), "QUERY"));
        assertEquals(1, service.datasourcePermissions().size());
        assertEquals("QUERY", service.datasourcePermissions().get(0).permissionCode());
        service.revokeDatasourcePermission(dataSourceId, user.id(), "QUERY");
        assertTrue(service.datasourcePermissions().isEmpty());
    }

    @Test
    void customRoleCanBeAssignedToUser() {
        PlatformStore store = new PlatformStore();
        AccessService service = new AccessService(store, new AuditService(store));
        RoleView role = service.createRole(new AccessRequests.RoleRequest("data_owner", "数据负责人"));
        UserView user = service.createUser(new AccessRequests.UserRequest("owner", "Owner", "secret", role.roleCode(), "ACTIVE"));
        assertEquals("DATA_OWNER", role.roleCode());
        assertEquals("DATA_OWNER", user.roleCode());
    }
}
