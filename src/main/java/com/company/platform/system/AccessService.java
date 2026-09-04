package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
public class AccessService {
    private final PlatformStore store;
    private final AuditService audit;
    public AccessService(PlatformStore store, AuditService audit) { this.store = store; this.audit = audit; }

    public List<UserView> users() { return store.users.values().stream().toList(); }
    public UserView createUser(AccessRequests.UserRequest request) {
        if (store.users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(request.username()))) {
            throw new BadRequestException("用户名已存在");
        }
        UserView user = new UserView(store.nextId(), request.username(), request.displayName(), "ACTIVE");
        store.users.put(user.id(), user); audit.record("CREATE_USER", "USER", user.id(), user.username(), "admin");
        store.persistUser(user);
        return user;
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
