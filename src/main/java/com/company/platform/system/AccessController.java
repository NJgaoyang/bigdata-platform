package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/system")
public class AccessController {
    private final AccessService service;
    private final AuditService audit;
    public AccessController(AccessService service, AuditService audit) { this.service = service; this.audit = audit; }
    @GetMapping("/users") public Result<List<UserView>> users(@RequestParam(required = false) String keyword) { return Result.ok(service.users(keyword)); }
    @PostMapping("/users") public Result<UserView> createUser(@Valid @RequestBody AccessRequests.UserRequest request) { return Result.ok(service.createUser(request), "用户已创建"); }
    @PutMapping("/users/{id}") public Result<UserView> updateUser(@PathVariable long id, @Valid @RequestBody AccessRequests.UserUpdateRequest request) { return Result.ok(service.updateUser(id, request), "用户已更新"); }
    @DeleteMapping("/users/{id}") public Result<Void> deleteUser(@PathVariable long id) { service.deleteUser(id); return Result.ok(null, "用户已删除"); }
    @PostMapping("/users/{id}/disable") public Result<UserView> disableUser(@PathVariable long id) { return Result.ok(service.setUserStatus(id, "DISABLED"), "用户已禁用"); }
    @PostMapping("/users/{id}/enable") public Result<UserView> enableUser(@PathVariable long id) { return Result.ok(service.setUserStatus(id, "ACTIVE"), "用户已启用"); }
    @GetMapping("/users/{id}/permissions") public Result<Set<String>> permissions(@PathVariable long id) { return Result.ok(service.permissions(id)); }
    @PutMapping("/users/{id}/permissions") public Result<Set<String>> setPermissions(@PathVariable long id, @RequestBody Map<String, Set<String>> request) { return Result.ok(service.setPermissions(id, request.getOrDefault("permissions", Set.of())), "用户权限已更新"); }
    @GetMapping("/roles") public Result<List<RoleView>> roles() { return Result.ok(service.roles()); }
    @PostMapping("/roles") public Result<RoleView> createRole(@Valid @RequestBody AccessRequests.RoleRequest request) { return Result.ok(service.createRole(request), "角色已创建"); }
    @PostMapping("/roles/{roleId}/permissions") public Result<RoleView> grant(@PathVariable long roleId, @Valid @RequestBody AccessRequests.PermissionRequest request) { return Result.ok(service.grant(roleId, request), "权限已授予"); }
    @PostMapping("/projects/{projectId}/permissions") public Result<String> grantProject(@PathVariable long projectId, @Valid @RequestBody AccessRequests.PermissionBindingRequest request) { return Result.ok(service.grantProjectPermission(projectId, request), "项目权限已授予"); }
    @PostMapping("/data-sources/{dataSourceId}/permissions") public Result<String> grantDatasource(@PathVariable long dataSourceId, @Valid @RequestBody AccessRequests.PermissionBindingRequest request) { return Result.ok(service.grantDatasourcePermission(dataSourceId, request), "数据源权限已授予"); }
    @GetMapping("/alert-channels") public Result<List<AlertChannelView>> channels() { return Result.ok(service.channels()); }
    @PostMapping("/alert-channels") public Result<AlertChannelView> createChannel(@Valid @RequestBody AccessRequests.AlertChannelRequest request) { return Result.ok(service.createChannel(request), "告警渠道已创建"); }
    @GetMapping("/audit-logs") public Result<List<AuditLogView>> auditLogs() { return Result.ok(audit.list()); }
}
