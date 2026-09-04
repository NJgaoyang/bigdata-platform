package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test
    void loginIssuesTokenForConfiguredAdminAndRejectsWrongPassword() {
        PlatformProperties properties = new PlatformProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSecurity().setAdminPasswordHash(sha256("secret"));
        AuthService service = new AuthService(properties, new PlatformStore());

        AuthService.AuthSession session = service.login(new AuthRequests.LoginRequest("admin", "secret"));

        assertNotNull(session.token());
        assertTrue(service.authenticate(session.token()));
        assertThrows(RuntimeException.class, () -> service.login(new AuthRequests.LoginRequest("admin", "wrong")));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
