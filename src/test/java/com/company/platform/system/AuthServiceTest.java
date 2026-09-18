package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.DataSphereProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test void storedAdminLoginIssuesTokenAndRejectsWrongPassword() {
        DataSphereProperties properties = secured();
        PlatformStore store = new PlatformStore();
        setPassword(store, "admin", "secret");
        AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("admin", "secret"));
        assertNotNull(session.token());
        assertTrue(service.authenticate(session.token()));
        assertThrows(RuntimeException.class, () -> service.login(new AuthRequests.LoginRequest("admin", "wrong")));
    }

    @Test void invalidTokenDoesNotMasqueradeAsAdminInIdentityView() {
        AuthService service = new AuthService(secured(), new PlatformStore());
        assertEquals("", service.currentUsername("missing"));
        assertEquals("USER", service.roleForToken("missing"));
        assertFalse(service.isSuperAdminToken("missing"));
    }

    @Test void configuredAdminUsernameIsComparedIgnoringCaseAndWhitespace() {
        DataSphereProperties properties = secured();
        properties.getSecurity().setAdminUsername(" admin ");
        PlatformStore store = new PlatformStore();
        setPassword(store, "admin", "secret");
        AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest(" ADMIN ", "secret"));
        assertEquals("admin", session.username());
        assertTrue(service.hasPermission(session.token(), "/api/system/clusters"));
        assertTrue(service.permissionsForToken(session.token()).contains("SYSTEM_SETTINGS_VIEW"));
    }

    @Test void builtInAdminKeepsAllPermissionsWhenConfiguredNameDiffers() {
        DataSphereProperties properties = secured();
        properties.getSecurity().setAdminUsername("datasphere-admin");
        PlatformStore store = new PlatformStore();
        setPassword(store, "admin", "secret");
        AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("admin", "secret"));
        assertTrue(service.hasPermission(session.token(), "/api/system/clusters"));
    }

    @Test void adminCanChangePasswordAndLoginWithTheNewPassword() {
        DataSphereProperties properties = secured();
        PlatformStore store = new PlatformStore();
        setPassword(store, "admin", "old-secret");
        AuthService service = new AuthService(properties, store);
        AuthService.AuthSession current = service.login(new AuthRequests.LoginRequest("admin", "old-secret"));
        service.changePassword(current.token(), new AuthRequests.ChangePasswordRequest("old-secret", "new-secret", "new-secret"));
        assertFalse(service.authenticate(current.token()));
        assertThrows(RuntimeException.class, () -> service.login(new AuthRequests.LoginRequest("admin", "old-secret")));
        assertNotNull(service.login(new AuthRequests.LoginRequest("admin", "new-secret")));
    }

    @Test void permissionChecksUseOnlyCurrentPermissionCodes() {
        DataSphereProperties properties = secured();
        PlatformStore store = new PlatformStore();
        UserView user = new UserView(7L, "analyst", "Analyst", "USER", "ACTIVE", LocalDateTime.now(), PasswordHasher.hash("secret"));
        store.users.put(user.id(), user);
        store.userPermissions.put(user.id(), java.util.concurrent.ConcurrentHashMap.newKeySet());
        store.userPermissions.get(user.id()).add("METADATA_VIEW");
        store.userPermissions.get(user.id()).add(AccessService.PERMISSION_MARKER);
        AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("analyst", "secret"));
        assertTrue(service.hasPermission(session.token(), "GET", "/api/data-sources"));
        assertTrue(service.hasPermission(session.token(), "GET", "/api/datasources"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/data-sources/1/test"));
        assertFalse(service.hasPermission(session.token(), "PUT", "/api/data-sources/1"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/projects"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/unknown-new-api"));
    }

    @Test void rolePermissionsAreUsedWhenUserHasNoExplicitOverride() {
        DataSphereProperties properties = secured();
        PlatformStore store = new PlatformStore();
        UserView user = new UserView(9L, "dev", "Dev", "DEVELOPER", "ACTIVE", LocalDateTime.now(), PasswordHasher.hash("secret"));
        store.users.put(user.id(), user);
        store.roles.put(2L, new RoleView(2L, "DEVELOPER", "开发者", Set.of("WORKBENCH_VIEW", "DATA_DEVELOPMENT_VIEW", "DATA_DEVELOPMENT_EDIT")));
        AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("dev", "secret"));
        assertTrue(service.hasPermission(session.token(), "GET", "/api/development/projects"));
        assertTrue(service.hasPermission(session.token(), "POST", "/api/development/projects"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/system/users"));
    }

    private DataSphereProperties secured() {
        DataSphereProperties properties = new DataSphereProperties();
        properties.getSecurity().setEnabled(true);
        return properties;
    }

    private void setPassword(PlatformStore store, String username, String password) {
        UserView current = store.users.values().stream().filter(user -> user.username().equalsIgnoreCase(username)).findFirst().orElseThrow();
        store.users.put(current.id(), new UserView(current.id(), current.username(), current.displayName(), current.phone(), current.roleCode(), current.status(), current.createdAt(), PasswordHasher.hash(password)));
    }
}
