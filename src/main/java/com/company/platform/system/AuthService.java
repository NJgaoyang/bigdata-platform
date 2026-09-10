package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        purgeExpiredSessions();
        var security = properties.getSecurity();
        String configuredHash = security.getAdminPasswordHash();
        String loginUsername = request.username() == null ? "" : request.username().trim();
        String configuredAdminUsername = security.getAdminUsername() == null ? "" : security.getAdminUsername().trim();
        UserView user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(loginUsername)).findFirst().orElse(null);
        boolean configuredAdmin = configuredHash != null && !configuredHash.isBlank()
                && configuredAdminUsername.equalsIgnoreCase(loginUsername)
                && PasswordHasher.verify(request.password(), configuredHash);
        boolean storedUser = user != null && "ACTIVE".equalsIgnoreCase(user.status())
                && PasswordHasher.verify(request.password(), user.passwordHash());
        // Once a platform user has a password, it takes precedence over the bootstrap password.
        if (user != null && user.passwordHash() != null) configuredAdmin = false;
        if (!configuredAdmin && !storedUser) {
            if (audit != null) audit.record("LOGIN_FAILED", "AUTH", null, loginUsername, loginUsername);
            throw new BadRequestException("用户名或密码错误");
        }
        if (storedUser && PasswordHasher.needsUpgrade(user.passwordHash())) {
            UserView upgraded = new UserView(user.id(), user.username(), user.displayName(), user.roleCode(), user.status(),
                    user.createdAt(), PasswordHasher.hash(request.password()));
            store.persistUser(upgraded);
            store.users.put(upgraded.id(), upgraded);
            user = upgraded;
        }
        String token = randomToken();
        Instant expiresAt = Instant.now().plusSeconds(Math.max(5, security.getSessionTtlMinutes()) * 60L);
        String username = configuredAdmin ? configuredAdminUsername : user.username();
        sessions.put(token, new Session(username, expiresAt));
        if (audit != null) audit.record("LOGIN", "AUTH", user == null ? null : user.id(), username, username);
        return new AuthSession(token, username, expiresAt);
    }

    public void changePassword(String token, AuthRequests.ChangePasswordRequest request) {
        if (!authenticate(token)) throw new BadRequestException("登录会话已失效，请重新登录");
        String username = currentUsername(token);
        String newPassword = request.newPassword() == null ? "" : request.newPassword().trim();
        String confirmPassword = request.confirmPassword() == null ? "" : request.confirmPassword().trim();
        if (newPassword.length() < 6) throw new BadRequestException("新密码至少需要 6 位");
        if (!newPassword.equals(confirmPassword)) throw new BadRequestException("两次输入的新密码不一致");

        UserView user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        String currentPassword = request.currentPassword() == null ? "" : request.currentPassword();
        String configuredHash = properties.getSecurity().getAdminPasswordHash();
        String existingHash = user == null ? null : user.passwordHash();
        if (existingHash == null && isConfiguredAdmin(username) && configuredHash != null && !configuredHash.isBlank()) existingHash = configuredHash.trim();
        if (existingHash != null && !PasswordHasher.verify(currentPassword, existingHash)) throw new BadRequestException("当前密码不正确");
        if (existingHash == null && enabled() && !isConfiguredAdmin(username)) throw new BadRequestException("当前账号尚未设置可验证的密码");

        String nextHash = PasswordHasher.hash(newPassword);
        UserView updated = user == null
                ? new UserView(store.nextId(), username.trim(), "平台管理员", "ADMIN", "ACTIVE", java.time.LocalDateTime.now(), nextHash)
                : new UserView(user.id(), user.username(), user.displayName(), isConfiguredAdmin(username) ? "ADMIN" : user.roleCode(),
                    user.status(), user.createdAt(), nextHash);
        store.persistUser(updated);
        store.users.put(updated.id(), updated);
        invalidateUser(updated.username());
        if (audit != null) audit.record("CHANGE_PASSWORD", "AUTH", updated.id(), updated.username(), updated.username());
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

    public boolean hasPermission(String token, String path) { return hasPermission(token, "GET", path); }

    public boolean hasPermission(String token, String method, String path) {
        if (!enabled()) return true;
        if (!authenticate(token)) return false;
        String username = currentUsername(token);
        if (isConfiguredAdmin(username)) return true;
        UserView user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.status())) return false;
        if ("ADMIN".equalsIgnoreCase(user.roleCode())) return true;
        Set<String> granted = accessPermissions(user.id());
        if (path.startsWith("/api/dashboard")) return granted.contains("WORKBENCH_VIEW");
        if (path.startsWith("/api/data-sources")) {
            if (!"GET".equalsIgnoreCase(method)) return granted.contains("SYSTEM_SETTINGS_EDIT");
            return granted.contains("METADATA_VIEW") || granted.contains("SYSTEM_SETTINGS_VIEW");
        }
        String permission = permissionFor(path);
        if (permission == null) return false; // API authorization is fail-closed for newly added routes.
        return granted.contains(permission + ("GET".equalsIgnoreCase(method) ? "_VIEW" : "_EDIT"));
    }

    public Set<String> permissionsForToken(String token) {
        if (!enabled()) return AccessService.MODULE_PERMISSIONS;
        if (!authenticate(token)) return Set.of();
        String username = currentUsername(token);
        if (isConfiguredAdmin(username)) return AccessService.MODULE_PERMISSIONS;
        UserView user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        if (user == null) return Set.of();
        if ("ADMIN".equalsIgnoreCase(user.roleCode())) return AccessService.MODULE_PERMISSIONS;
        return accessPermissions(user.id());
    }

    private String permissionFor(String path) {
        if (path.startsWith("/api/integration")) return "DATA_INTEGRATION";
        if (path.startsWith("/api/development") || path.startsWith("/api/projects")
                || path.startsWith("/api/folders") || path.startsWith("/api/files")) return "DATA_DEVELOPMENT";
        if (path.startsWith("/api/query") || path.startsWith("/api/metadata")) return "METADATA";
        if (path.startsWith("/api/lineage")) return "DATA_ASSETS";
        if (path.startsWith("/api/workflows") || path.startsWith("/api/scheduler")) return "WORKFLOW";
        if (path.startsWith("/api/operations")) return "OPERATIONS";
        if (path.startsWith("/api/system")) return "SYSTEM_SETTINGS";
        return null;
    }

    private Set<String> accessPermissions(long userId) {
        return AccessService.effectivePermissions(store.userPermissions.getOrDefault(userId, Set.of()));
    }

    private boolean isConfiguredAdmin(String username) {
        String configuredAdmin = properties.getSecurity().getAdminUsername();
        if (username == null || username.isBlank()) return false;
        String normalizedUsername = username.trim();
        return normalizedUsername.equalsIgnoreCase("admin")
                || configuredAdmin != null && normalizedUsername.equalsIgnoreCase(configuredAdmin.trim());
    }

    public String currentUsername(String token) {
        if (!enabled()) {
            String configuredAdmin = properties.getSecurity().getAdminUsername();
            return configuredAdmin == null || configuredAdmin.isBlank() ? "admin" : configuredAdmin.trim();
        }
        if (token == null || token.isBlank()) return "";
        Session session = sessions.get(token);
        if (session == null) return "";
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return "";
        }
        return session.username();
    }

    public String roleForToken(String token) {
        String username = currentUsername(token);
        if (username.isBlank()) return "USER";
        if (isConfiguredAdmin(username)) return "ADMIN";
        return store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username))
                .map(UserView::roleCode).findFirst().orElse("USER");
    }

    public boolean isSuperAdminToken(String token) {
        String username = currentUsername(token);
        return !username.isBlank() && isConfiguredAdmin(username);
    }

    public boolean isAdministrator(String username) {
        if (username == null || username.isBlank()) return false;
        if (isConfiguredAdmin(username)) return true;
        return store.users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(username.trim())
                && "ACTIVE".equalsIgnoreCase(user.status()) && "ADMIN".equalsIgnoreCase(user.roleCode()));
    }

    public void invalidateUser(String username) {
        if (username == null) return;
        sessions.entrySet().removeIf(entry -> entry.getValue().username().equalsIgnoreCase(username.trim()));
    }

    private void purgeExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private record Session(String username, Instant expiresAt) { }
    public record AuthSession(String token, String username, Instant expiresAt) { }
}
