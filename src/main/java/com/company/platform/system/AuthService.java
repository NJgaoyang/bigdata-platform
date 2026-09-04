package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final PlatformProperties properties;
    private final PlatformStore store;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private AuditService audit;

    public AuthService(PlatformProperties properties, PlatformStore store) {
        this.properties = properties;
        this.store = store;
    }

    @Autowired
    public void setAuditService(AuditService audit) { this.audit = audit; }

    public boolean enabled() { return properties.getSecurity().isEnabled(); }

    public AuthSession login(AuthRequests.LoginRequest request) {
        var security = properties.getSecurity();
        String configuredHash = security.getAdminPasswordHash();
        var user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(request.username())).findFirst().orElse(null);
        String passwordHash = sha256(request.password());
        boolean configuredAdmin = configuredHash != null && !configuredHash.isBlank() && security.getAdminUsername().equals(request.username()) &&
                MessageDigest.isEqual(configuredHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8), passwordHash.getBytes(StandardCharsets.UTF_8));
        boolean storedUser = user != null && "ACTIVE".equalsIgnoreCase(user.status()) && user.passwordHash() != null &&
                MessageDigest.isEqual(user.passwordHash().getBytes(StandardCharsets.UTF_8), passwordHash.getBytes(StandardCharsets.UTF_8));
        if (!configuredAdmin && !storedUser) {
            if (audit != null) audit.record("LOGIN_FAILED", "AUTH", null, request.username(), request.username());
            throw new BadRequestException("用户名或密码错误");
        }
        String token = randomToken();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(5, security.getSessionTtlMinutes()) * 60L);
        String username = configuredAdmin ? security.getAdminUsername() : user.username();
        sessions.put(token, new Session(username, expiresAt));
        if (audit != null) audit.record("LOGIN", "AUTH", user == null ? null : user.id(), username, username);
        return new AuthSession(token, username, expiresAt);
    }

    public boolean authenticate(String token) {
        if (!enabled()) return true;
        if (token == null || token.isBlank()) return false;
        Session session = sessions.get(token);
        if (session == null) return false;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void logout(String token) {
        String username = currentUsername(token);
        if (audit != null && token != null && !token.isBlank() && authenticate(token)) audit.record("LOGOUT", "AUTH", null, username, username);
        if (token != null) sessions.remove(token);
    }

    public boolean hasPermission(String token, String path) {
        if (!enabled() || token == null || token.isBlank()) return !enabled();
        String username = currentUsername(token);
        if (username.equalsIgnoreCase(properties.getSecurity().getAdminUsername())) return true;
        var user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.status())) return false;
        if ("ADMIN".equalsIgnoreCase(user.roleCode())) return true;
        String permission = permissionFor(path);
        return permission == null || store.userPermissions.getOrDefault(user.id(), java.util.Set.of()).contains(permission);
    }

    public Set<String> permissionsForToken(String token) {
        if (!enabled()) return AccessService.MODULE_PERMISSIONS;
        if (!authenticate(token)) return Set.of();
        String username = currentUsername(token);
        if (username.equalsIgnoreCase(properties.getSecurity().getAdminUsername())) return AccessService.MODULE_PERMISSIONS;
        var user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        if (user == null) return Set.of();
        if ("ADMIN".equalsIgnoreCase(user.roleCode())) return AccessService.MODULE_PERMISSIONS;
        return Set.copyOf(store.userPermissions.getOrDefault(user.id(), Set.of()));
    }

    private String permissionFor(String path) {
        if (path.startsWith("/api/integration")) return "DATA_INTEGRATION";
        if (path.startsWith("/api/development")) return "DATA_DEVELOPMENT";
        if (path.startsWith("/api/query") || path.startsWith("/api/metadata")) return "DATA_EXPLORE";
        if (path.startsWith("/api/lineage")) return "DATA_LINEAGE";
        if (path.startsWith("/api/workflows") || path.startsWith("/api/scheduler")) return "SCHEDULER";
        if (path.startsWith("/api/operations")) return "OPERATIONS";
        if (path.startsWith("/api/system")) return "SYSTEM_SETTINGS";
        return null;
    }

    public String currentUsername(String token) {
        Session session = sessions.get(token);
        return session == null ? "admin" : session.username();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法初始化平台认证摘要算法", ex);
        }
    }

    private record Session(String username, Instant expiresAt) { }
    public record AuthSession(String token, String username, Instant expiresAt) { }
}
