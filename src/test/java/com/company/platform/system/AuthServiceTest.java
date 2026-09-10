package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test void loginIssuesTokenForConfiguredAdminAndRejectsWrongPassword() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); properties.getSecurity().setAdminPasswordHash(sha256("secret"));
        AuthService service = new AuthService(properties, new PlatformStore()); AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("admin", "secret"));
        assertNotNull(session.token()); assertTrue(service.authenticate(session.token())); assertThrows(RuntimeException.class, () -> service.login(new AuthRequests.LoginRequest("admin", "wrong")));
    }
    @Test void invalidTokenDoesNotMasqueradeAsAdminInIdentityView() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); AuthService service = new AuthService(properties, new PlatformStore());
        assertEquals("", service.currentUsername("missing")); assertEquals("USER", service.roleForToken("missing")); assertFalse(service.isSuperAdminToken("missing"));
    }
    @Test void configuredAdminUsernameIsComparedIgnoringCaseAndWhitespace() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); properties.getSecurity().setAdminUsername(" admin "); properties.getSecurity().setAdminPasswordHash(sha256("secret"));
        AuthService service = new AuthService(properties, new PlatformStore()); AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest(" ADMIN ", "secret"));
        assertEquals("admin", session.username()); assertTrue(service.hasPermission(session.token(), "/api/system/clusters")); assertTrue(service.permissionsForToken(session.token()).contains("SYSTEM_SETTINGS"));
    }
    @Test void builtInAdminKeepsAllPermissionsWhenConfiguredNameDiffers() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); properties.getSecurity().setAdminUsername("platform-admin"); PlatformStore store = new PlatformStore();
        store.users.put(1L, new UserView(1L, "admin", "平台管理员", "ACTIVE", sha256("secret"))); AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("admin", "secret")); assertTrue(service.hasPermission(session.token(), "/api/system/clusters"));
    }
    @Test void legacyStoredHashIsUpgradedAfterSuccessfulLogin() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); PlatformStore store = new PlatformStore();
        store.users.put(7L, new UserView(7L, "legacy", "Legacy", "USER", "ACTIVE", java.time.LocalDateTime.now(), sha256("secret"))); AuthService service = new AuthService(properties, store);
        service.login(new AuthRequests.LoginRequest("legacy", "secret")); assertTrue(store.users.get(7L).passwordHash().startsWith("pbkdf2-sha256$"));
    }
    @Test void adminCanChangePasswordAndLoginWithTheNewPassword() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(false); PlatformStore store = new PlatformStore(); AuthService service = new AuthService(properties, store);
        service.changePassword("", new AuthRequests.ChangePasswordRequest("", "new-secret", "new-secret"));
        assertTrue(store.users.values().stream().filter(user -> user.username().equals("admin")).findFirst().orElseThrow().passwordHash().startsWith("pbkdf2-sha256$"));
        assertEquals("admin", service.login(new AuthRequests.LoginRequest("admin", "new-secret")).username());
    }
    @Test void changingConfiguredAdminPasswordDisablesTheOldBootstrapPasswordAndInvalidatesSession() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); properties.getSecurity().setAdminPasswordHash(sha256("old-secret")); PlatformStore store = new PlatformStore(); AuthService service = new AuthService(properties, store);
        AuthService.AuthSession current = service.login(new AuthRequests.LoginRequest("admin", "old-secret")); service.changePassword(current.token(), new AuthRequests.ChangePasswordRequest("old-secret", "new-secret", "new-secret"));
        assertFalse(service.authenticate(current.token())); assertThrows(RuntimeException.class, () -> service.login(new AuthRequests.LoginRequest("admin", "old-secret"))); assertNotNull(service.login(new AuthRequests.LoginRequest("admin", "new-secret")));
    }
    @Test void permissionChecksCoverAliasesSensitiveTestsAndUnknownApis() {
        PlatformProperties properties = new PlatformProperties(); properties.getSecurity().setEnabled(true); PlatformStore store = new PlatformStore();
        UserView user = new UserView(7L, "analyst", "Analyst", "USER", "ACTIVE", java.time.LocalDateTime.now(), sha256("secret")); store.users.put(user.id(), user);
        store.userPermissions.put(user.id(), java.util.concurrent.ConcurrentHashMap.newKeySet()); store.userPermissions.get(user.id()).add("DATA_EXPLORE"); AuthService service = new AuthService(properties, store);
        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("analyst", "secret"));
        assertTrue(service.hasPermission(session.token(), "GET", "/api/data-sources"));
        assertTrue(service.hasPermission(session.token(), "GET", "/api/datasources"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/data-sources/1/test"));
        assertFalse(service.hasPermission(session.token(), "PUT", "/api/data-sources/1"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/projects"));
        assertFalse(service.hasPermission(session.token(), "GET", "/api/unknown-new-api"));
    }
    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
}
