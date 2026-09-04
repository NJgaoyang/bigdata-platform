package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service
public class AccessService {
    private final PlatformStore store;
    private final AuditService audit;
    public AccessService(PlatformStore store, AuditService audit) { this.store = store; this.audit = audit; }

    public static final Set<String> MODULE_PERMISSIONS = Set.of(
            "DATA_INTEGRATION", "DATA_DEVELOPMENT", "DATA_EXPLORE", "DATA_LINEAGE",
            "SCHEDULER", "OPERATIONS", "SYSTEM_SETTINGS");
    public List<UserView> users() { return users(null); }
    public List<UserView> users(String keyword) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        return store.users.values().stream()
                .filter(user -> query.isBlank() || user.username().toLowerCase().contains(query) || user.displayName().toLowerCase().contains(query))
                .toList();
    }
    public UserView createUser(AccessRequests.UserRequest request) {
        if (store.users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(request.username()))) {
            throw new BadRequestException("用户名已存在");
        }
        UserView user = new UserView(store.nextId(), request.username(), request.displayName(),
                normalizeRole(request.roleCode()), normalizeStatus(request.status()), LocalDateTime.now(), hash(request.password()));
        store.users.put(user.id(), user); audit.record("CREATE_USER", "USER", user.id(), user.username(), "admin");
        store.persistUser(user);
        return user;
    }
    public UserView updateUser(long id, AccessRequests.UserUpdateRequest request) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        boolean duplicate = store.users.values().stream().anyMatch(user -> user.id() != id && user.username().equalsIgnoreCase(request.username()));
        if (duplicate) throw new BadRequestException("用户名已存在");
        String passwordHash = request.password() == null || request.password().isBlank() ? current.passwordHash() : hash(request.password());
        String roleCode = request.roleCode() == null || request.roleCode().isBlank()
                ? current.roleCode() : normalizeRole(request.roleCode());
        UserView updated = new UserView(id, request.username(), request.displayName(),
                roleCode, normalizeStatus(request.status()), current.createdAt(), passwordHash);
        store.users.put(id, updated);
        store.persistUser(updated);
        audit.record("UPDATE_USER", "USER", id, updated.username(), "admin");
        return updated;
    }
    public void deleteUser(long id) {
        UserView current = store.users.remove(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        store.deleteUser(id);
        audit.record("DELETE_USER", "USER", id, current.username(), "admin");
    }
    public UserView setUserStatus(long id, String status) {
        UserView current = store.users.get(id);
        if (current == null) throw new NotFoundException("用户不存在：" + id);
        UserView updated = new UserView(id, current.username(), current.displayName(), current.roleCode(),
                normalizeStatus(status), current.createdAt(), current.passwordHash());
        store.users.put(id, updated);
        store.persistUser(updated);
        audit.record("UPDATE_USER_STATUS", "USER", id, updated.status(), "admin");
        return updated;
    }
    public Set<String> permissions(long userId) {
        if (!store.users.containsKey(userId)) throw new NotFoundException("用户不存在：" + userId);
        return Set.copyOf(store.userPermissions.getOrDefault(userId, Set.of()));
    }
    public Set<String> setPermissions(long userId, Set<String> requested) {
        if (!store.users.containsKey(userId)) throw new NotFoundException("用户不存在：" + userId);
        Set<String> permissions = new HashSet<>(requested == null ? Set.of() : requested);
        if (!MODULE_PERMISSIONS.containsAll(permissions)) throw new BadRequestException("包含不支持的模块权限");
        store.persistUserPermissions(userId, permissions);
        audit.record("SET_USER_PERMISSIONS", "USER", userId, String.join(",", permissions), "admin");
        return Set.copyOf(permissions);
    }
    public List<RoleView> roles() { return store.roles.values().stream().toList(); }
    public RoleView createRole(AccessRequests.RoleRequest request) {
        if (store.roles.values().stream().anyMatch(role -> role.roleCode().equalsIgnoreCase(request.roleCode()))) {
            throw new BadRequestException("角色编码已存在");
        }
        RoleView role = new RoleView(store.nextId(), request.roleCode(), request.roleName(), new HashSet<>());
        store.roles.put(role.id(), role); audit.record("CREATE_ROLE", "ROLE", role.id(), role.roleCode(), "admin");
        store.persistRole(role);
        return role;
    }
    public RoleView grant(long roleId, AccessRequests.PermissionRequest request) {
        RoleView current = store.roles.get(roleId);
        if (current == null) throw new NotFoundException("角色不存在：" + roleId);
        HashSet<String> permissions = new HashSet<>(current.permissions());
        permissions.add(request.permissionCode());
        RoleView updated = new RoleView(current.id(), current.roleCode(), current.roleName(), permissions);
        store.roles.put(roleId, updated); audit.record("GRANT_PERMISSION", "ROLE", roleId, request.permissionCode(), "admin");
        store.persistRole(updated);
        return updated;
    }
    public List<AlertChannelView> channels() { return store.alertChannels.values().stream().toList(); }
    public AlertChannelView createChannel(AccessRequests.AlertChannelRequest request) {
        AlertChannelView channel = new AlertChannelView(store.nextId(), request.name(), request.channelType(), request.configJson(), request.enabled());
        store.alertChannels.put(channel.id(), channel); audit.record("CREATE_ALERT_CHANNEL", "ALERT_CHANNEL", channel.id(), channel.name(), "admin");
        store.persistAlertChannel(channel);
        return channel;
    }
    private String normalizeStatus(String status) {
        if ("DISABLED".equalsIgnoreCase(status)) return "DISABLED";
        return "ACTIVE";
    }
    private String normalizeRole(String roleCode) {
        return "ADMIN".equalsIgnoreCase(roleCode) ? "ADMIN" : "USER";
    }
    private String hash(String password) {
        if (password == null || password.isBlank()) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成用户密码摘要", ex);
        }
    }
    public String grantProjectPermission(long projectId, AccessRequests.PermissionBindingRequest request) {
        if (!store.projects.containsKey(projectId)) throw new NotFoundException("项目不存在：" + projectId);
        if (!store.users.containsKey(request.userId())) throw new NotFoundException("用户不存在：" + request.userId());
        String key = projectId + ":" + request.userId() + ":" + request.permissionCode();
        if (store.projectPermissions.containsKey(key)) return key;
        store.projectPermissions.put(key, request.permissionCode());
        store.persistProjectPermission(projectId, request.userId(), request.permissionCode());
        audit.record("GRANT_PROJECT_PERMISSION", "PROJECT", projectId, key, "admin");
        return key;
    }
    public String grantDatasourcePermission(long datasourceId, AccessRequests.PermissionBindingRequest request) {
        if (!store.dataSources.containsKey(datasourceId)) throw new NotFoundException("数据源不存在：" + datasourceId);
        if (!store.users.containsKey(request.userId())) throw new NotFoundException("用户不存在：" + request.userId());
        String key = datasourceId + ":" + request.userId() + ":" + request.permissionCode();
        if (store.datasourcePermissions.containsKey(key)) return key;
        store.datasourcePermissions.put(key, request.permissionCode());
        store.persistDatasourcePermission(datasourceId, request.userId(), request.permissionCode());
        audit.record("GRANT_DATASOURCE_PERMISSION", "DATASOURCE", datasourceId, key, "admin");
        return key;
    }
}
