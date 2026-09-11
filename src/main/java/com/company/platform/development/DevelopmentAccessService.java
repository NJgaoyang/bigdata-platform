package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.ForbiddenException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.system.AccessService;
import com.company.platform.system.UserView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
public class DevelopmentAccessService {
    private final PlatformStore store;
    private final DevelopmentService development;
    private final PlatformProperties properties;

    public DevelopmentAccessService(PlatformStore store, DevelopmentService development, PlatformProperties properties) {
        this.store = store;
        this.development = development;
        this.properties = properties;
    }

    public ModuleAccess moduleAccess(String operator) {
        String username = normalize(operator);
        if (isAdministrator(username)) return new ModuleAccess(true, true, true);
        Long userId = userId(username);
        if (userId == null) return new ModuleAccess(false, false, false);
        Set<String> permissions = AccessService.effectivePermissions(store.userPermissions.getOrDefault(userId, Set.of()));
        boolean projectAll = permissions.contains(AccessService.DATA_DEVELOPMENT_PROJECT_ALL);
        return new ModuleAccess(
                permissions.contains("DATA_DEVELOPMENT_VIEW") || permissions.contains("DATA_DEVELOPMENT_EDIT") || projectAll,
                permissions.contains("DATA_DEVELOPMENT_EDIT") || projectAll,
                projectAll);
    }

    public ProjectAccess projectAccess(long projectId, String operator) {
        DevProjectView project = store.projects.get(projectId);
        if (project == null) throw new NotFoundException("项目不存在：" + projectId);
        String username = normalize(operator);
        ModuleAccess module = moduleAccess(username);
        boolean admin = isAdministrator(username);
        boolean owner = project.ownerName() != null && username.equalsIgnoreCase(project.ownerName());
        Long userId = userId(username);
        boolean projectAll = module.projectAll();
        boolean memberView = userId != null && (hasProjectPermission(projectId, userId, "VIEW") || hasProjectPermission(projectId, userId, "EDIT"));
        boolean memberEdit = userId != null && hasProjectPermission(projectId, userId, "EDIT");
        boolean view = module.view() && (admin || owner || projectAll || memberView);
        boolean edit = module.edit() && (admin || owner || projectAll || memberEdit);
        if (!view) throw new ForbiddenException("当前用户没有该项目的查看权限");
        return new ProjectAccess(projectId, true, edit, owner, projectAll);
    }

    @Transactional
    public synchronized DevFileView saveToProject(DevelopmentAccessRequests.SaveToProjectRequest request, String operator) {
        if (request.projectId() <= 0) throw new BadRequestException("请选择目标项目");
        ProjectAccess access = projectAccess(request.projectId(), operator);
        if (!access.edit()) throw new ForbiddenException("当前用户仅有查看权限，无法保存到该项目");

        String name = request.name() == null ? "" : request.name().trim();
        String fileType = request.fileType() == null ? "" : request.fileType().trim().toUpperCase();
        if (name.isBlank()) throw new BadRequestException("文件名称不能为空");
        if (fileType.isBlank()) throw new BadRequestException("文件类型不能为空");

        DevFileView existing = store.files.values().stream()
                .filter(file -> file.projectId() == request.projectId())
                .filter(file -> Objects.equals(file.folderId(), request.folderId()))
                .filter(file -> name.equalsIgnoreCase(file.name()))
                .findFirst().orElse(null);

        String content = request.content() == null ? "" : request.content();
        String description = request.description() == null ? "" : request.description().trim();
        if (existing != null) {
            if (!fileType.equalsIgnoreCase(existing.fileType())) {
                throw new BadRequestException("目标目录已存在同名的其他类型文件");
            }
            return development.saveFile(existing.id(), new DevelopmentRequests.SaveFileRequest(
                    content, existing.name(), description, existing.folderId(), existing.folderId() == null), operator);
        }

        return development.createFile(new DevelopmentRequests.FileRequest(
                request.projectId(), request.folderId(), name, fileType, content, description), operator);
    }

    private String normalize(String operator) {
        return operator == null || operator.isBlank() ? "admin" : operator.trim();
    }

    private boolean isAdministrator(String username) {
        if ("admin".equalsIgnoreCase(username)) return true;
        String configured = properties.getSecurity().getAdminUsername();
        if (configured != null && username.equalsIgnoreCase(configured.trim())) return true;
        return store.users.values().stream().anyMatch(user -> username.equalsIgnoreCase(user.username())
                && "ACTIVE".equalsIgnoreCase(user.status()) && "ADMIN".equalsIgnoreCase(user.roleCode()));
    }

    private Long userId(String username) {
        return store.users.values().stream()
                .filter(user -> username.equalsIgnoreCase(user.username()))
                .map(UserView::id).findFirst().orElse(null);
    }

    private boolean hasProjectPermission(long projectId, long userId, String permission) {
        return store.projectPermissions.containsKey(projectId + ":" + userId + ":" + permission);
    }

    public record ModuleAccess(boolean view, boolean edit, boolean projectAll) { }
    public record ProjectAccess(long projectId, boolean view, boolean edit, boolean owner, boolean projectAll) { }
}
