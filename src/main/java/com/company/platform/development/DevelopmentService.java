package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.system.AccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DevelopmentService {
    private final PlatformStore store;
    private PlatformProperties properties;

    public DevelopmentService(PlatformStore store) { this.store = store; }
    @Autowired public void setProperties(PlatformProperties properties) { this.properties = properties; }

    public List<DevProjectView> projects() { return store.projects.values().stream().toList(); }
    public List<DevProjectView> projects(String operator, boolean includeAll) {
        String username = normalizeOperator(operator);
        if (!includeAll) return store.projects.values().stream().filter(project -> username.equalsIgnoreCase(project.ownerName())).toList();
        return store.projects.values().stream().filter(project -> canProjectView(project.id(), username)).toList();
    }

    public DevProjectView createProject(DevelopmentRequests.ProjectRequest request) { return createProject(request, "admin"); }
    @Transactional
    public DevProjectView createProject(DevelopmentRequests.ProjectRequest request, String operator) {
        long id = store.nextId();
        DevProjectView view = new DevProjectView(id, request.name(), request.description(), "ACTIVE", normalizeOperator(operator));
        store.persistProject(view);
        store.projects.put(id, view);
        return view;
    }

    public DevProjectView updateProject(long id, DevelopmentRequests.ProjectRequest request) {
        if (!store.projects.containsKey(id)) throw new NotFoundException("项目不存在：" + id);
        DevProjectView view = new DevProjectView(id, request.name(), request.description(), "ACTIVE", currentOwner(id));
        store.persistProject(view);
        store.projects.put(id, view);
        return view;
    }
    @Transactional
    public DevProjectView updateProject(long id, DevelopmentRequests.ProjectRequest request, String operator) {
        requireProjectEdit(id, operator);
        return updateProject(id, request);
    }

    public void deleteProject(long id) {
        if (store.files.values().stream().anyMatch(file -> file.projectId() == id)
                || store.folders.values().stream().anyMatch(folder -> folder.projectId() == id)) throw new BadRequestException("项目非空，不能删除");
        if (!store.projects.containsKey(id)) throw new NotFoundException("项目不存在：" + id);
        store.deleteCore("dev_project", id);
        store.projects.remove(id);
    }
    @Transactional
    public void deleteProject(long id, String operator) { requireProjectEdit(id, operator); deleteProject(id); }

    public List<DevFolderView> folders(long projectId) {
        requireProject(projectId);
        return store.folders.values().stream().filter(folder -> folder.projectId() == projectId)
                .sorted(Comparator.comparing(DevFolderView::createdAt)).toList();
    }
    public List<DevFolderView> folders(long projectId, String operator) { requireProjectView(projectId, operator); return folders(projectId); }

    public DevFolderView createFolder(DevelopmentRequests.FolderRequest request) {
        requireProject(request.projectId());
        validateParentFolder(request.projectId(), request.parentId(), null);
        long id = store.nextId();
        DevFolderView view = new DevFolderView(id, request.projectId(), request.parentId(), request.name(), java.time.LocalDateTime.now());
        store.persistFolder(view);
        store.folders.put(id, view);
        return view;
    }
    @Transactional
    public DevFolderView createFolder(DevelopmentRequests.FolderRequest request, String operator) {
        requireProjectEdit(request.projectId(), operator);
        return createFolder(request);
    }

    public DevFolderView updateFolder(long id, DevelopmentRequests.FolderUpdateRequest request) {
        DevFolderView current = store.folders.get(id);
        if (current == null) throw new NotFoundException("文件夹不存在：" + id);
        Long parentId = Boolean.TRUE.equals(request.moveToRoot()) ? null : request.parentId() == null ? current.parentId() : request.parentId();
        validateParentFolder(current.projectId(), parentId, id);
        if (parentId != null) {
            Set<Long> seen = new HashSet<>();
            Long cursor = parentId;
            while (cursor != null && seen.add(cursor)) {
                if (cursor == id) throw new BadRequestException("不能移动到当前文件夹的子目录");
                DevFolderView ancestor = store.folders.get(cursor);
                cursor = ancestor == null ? null : ancestor.parentId();
            }
        }
        DevFolderView view = new DevFolderView(id, current.projectId(), parentId, request.name(), current.createdAt());
        store.persistFolder(view);
        store.folders.put(id, view);
        return view;
    }
    @Transactional
    public DevFolderView updateFolder(long id, DevelopmentRequests.FolderUpdateRequest request, String operator) {
        DevFolderView folder = store.folders.get(id);
        if (folder == null) throw new NotFoundException("文件夹不存在：" + id);
        requireProjectEdit(folder.projectId(), operator);
        return updateFolder(id, request);
    }

    public void deleteFolder(long id) {
        if (!store.folders.containsKey(id)) throw new NotFoundException("文件夹不存在：" + id);
        boolean hasFolder = store.folders.values().stream().anyMatch(folder -> folder.parentId() != null && id == folder.parentId());
        boolean hasFile = store.files.values().stream().anyMatch(file -> file.folderId() != null && file.folderId() == id);
        if (hasFolder || hasFile) {
            Set<Long> folderIds = descendantFolderIds(id);
            List<Long> fileIds = store.files.values().stream().filter(file -> file.folderId() != null && folderIds.contains(file.folderId())).map(DevFileView::id).toList();
            List<Long> versionIds = store.versions.values().stream().filter(version -> fileIds.contains(version.fileId())).map(FileVersionView::id).toList();
            List<String> usages = store.workflows.values().stream()
                    .filter(workflow -> workflow.nodes().stream().anyMatch(node -> node.fileVersionId() != null && versionIds.contains(node.fileVersionId())))
                    .map(workflow -> workflow.name() + (workflow.status() == null || workflow.status().isBlank() ? "" : "（" + workflow.status() + "）"))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
            if (!usages.isEmpty()) throw new BadRequestException("文件夹内文件正在被调度任务使用：" + String.join("、", usages) + "，不能删除");
            throw new BadRequestException("文件夹非空，不能删除");
        }
        store.deleteCore("dev_folder", id);
        store.folders.remove(id);
    }
    @Transactional
    public void deleteFolder(long id, String operator) {
        DevFolderView folder = store.folders.get(id);
        if (folder == null) throw new NotFoundException("文件夹不存在：" + id);
        requireProjectEdit(folder.projectId(), operator);
        deleteFolder(id);
    }

    public List<DevFileView> files(long projectId) {
        requireProject(projectId);
        return store.files.values().stream().filter(file -> file.projectId() == projectId).sorted(Comparator.comparing(DevFileView::name)).toList();
    }
    public List<DevFileView> files(long projectId, String operator) { requireProjectView(projectId, operator); return files(projectId); }

    public DevFileView createFile(DevelopmentRequests.FileRequest request) {
        requireProject(request.projectId());
        validateParentFolder(request.projectId(), request.folderId(), null);
        long id = store.nextId();
        DevFileView view = new DevFileView(id, request.projectId(), request.folderId(), request.name(), request.fileType().toUpperCase(),
                request.content() == null ? "" : request.content(), request.description() == null ? "" : request.description().trim(), "DRAFT", 1,
                LocalDateTime.now());
        // File must exist before version because dev_file_version has an FK to dev_file.
        store.persistFile(view);
        FileVersionView version = newVersion(view);
        store.persistVersion(version);
        store.files.put(id, view);
        store.versions.put(version.id(), version);
        return view;
    }
    @Transactional
    public DevFileView createFile(DevelopmentRequests.FileRequest request, String operator) { requireProjectEdit(request.projectId(), operator); return createFile(request); }

    public DevFileView getFile(long id) { return requireFile(id); }
    public DevFileView getFile(long id, String operator) {
        DevFileView file = requireFile(id);
        requireProjectView(file.projectId(), operator);
        return file;
    }

    public void deleteFile(long id) {
        DevFileView current = requireFile(id);
        List<Long> versionIds = store.versions.values().stream().filter(version -> version.fileId() == id).map(FileVersionView::id).toList();
        List<String> usages = store.workflows.values().stream()
                .filter(workflow -> workflow.nodes().stream().anyMatch(node -> node.fileVersionId() != null && versionIds.contains(node.fileVersionId())))
                .map(workflow -> workflow.name() + (workflow.status() == null || workflow.status().isBlank() ? "" : "（" + workflow.status() + "）"))
                .sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
        if (!usages.isEmpty()) throw new BadRequestException("文件“" + current.name() + "”正在被调度任务使用：" + String.join("、", usages) + "，不能删除");
        store.deleteFileData(id);
        store.files.remove(id);
        store.versions.values().removeIf(version -> version.fileId() == id);
    }
    @Transactional
    public void deleteFile(long id, String operator) { requireProjectEdit(requireFile(id).projectId(), operator); deleteFile(id); }

    public DevFileView saveFile(long id, DevelopmentRequests.SaveFileRequest request) {
        DevFileView current = requireFile(id);
        String name = request.name() == null || request.name().isBlank() ? current.name() : request.name().trim();
        Long folderId = Boolean.TRUE.equals(request.moveToRoot()) ? null : request.folderId() == null ? current.folderId() : request.folderId();
        validateParentFolder(current.projectId(), folderId, null);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), folderId, name, current.fileType(), request.content(),
                request.description() == null ? current.description() : request.description().trim(), "DRAFT", current.currentVersion() + 1,
                LocalDateTime.now());
        FileVersionView version = newVersion(updated);
        store.persistVersion(version);
        store.persistFile(updated);
        store.versions.put(version.id(), version);
        store.files.put(id, updated);
        return updated;
    }
    @Transactional
    public DevFileView saveFile(long id, DevelopmentRequests.SaveFileRequest request, String operator) { requireProjectEdit(requireFile(id).projectId(), operator); return saveFile(id, request); }

    public List<FileVersionView> versions(long fileId) {
        requireFile(fileId);
        return store.versions.values().stream().filter(version -> version.fileId() == fileId)
                .sorted(Comparator.comparingInt(FileVersionView::versionNo).reversed()).toList();
    }
    public List<FileVersionView> versions(long fileId, String operator) { requireProjectView(requireFile(fileId).projectId(), operator); return versions(fileId); }

    public FileVersionView createVersion(long fileId, DevelopmentRequests.VersionRequest request) {
        DevFileView current = requireFile(fileId);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(), request.content(),
                current.description(), "DRAFT", current.currentVersion() + 1, LocalDateTime.now());
        FileVersionView version = newVersion(updated);
        store.persistVersion(version);
        store.persistFile(updated);
        store.versions.put(version.id(), version);
        store.files.put(fileId, updated);
        return version;
    }
    @Transactional
    public FileVersionView createVersion(long fileId, DevelopmentRequests.VersionRequest request, String operator) { requireProjectEdit(requireFile(fileId).projectId(), operator); return createVersion(fileId, request); }

    @Transactional
    public DevFileView publishFile(long fileId, String operator) {
        DevFileView current = requireFile(fileId);
        requireProjectEdit(current.projectId(), operator);
        List<FileVersionView> fileVersions = store.versions.values().stream().filter(version -> version.fileId() == fileId)
                .sorted(Comparator.comparingInt(FileVersionView::versionNo).reversed()).toList();
        FileVersionView target = fileVersions.stream().filter(version -> version.versionNo() == current.currentVersion()).findFirst()
                .orElseThrow(() -> new BadRequestException("当前文件没有可发布版本"));
        List<FileVersionView> updates = new ArrayList<>();
        for (FileVersionView version : fileVersions) {
            boolean publish = version.id() == target.id();
            if (version.publishFlag() != publish) updates.add(new FileVersionView(version.id(), version.fileId(), version.versionNo(), version.content(), version.checksum(), publish));
        }
        updates.forEach(store::persistVersion);
        DevFileView published = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(), current.content(),
                current.description(), "PUBLISHED", current.currentVersion(), LocalDateTime.now());
        store.persistFile(published);
        updates.forEach(version -> store.versions.put(version.id(), version));
        store.files.put(fileId, published);
        return published;
    }

    public Map<String, Object> tree(long projectId) { requireProject(projectId); return Map.of("projectId", projectId, "folders", folders(projectId), "files", files(projectId)); }
    public Map<String, Object> tree(long projectId, String operator) { requireProjectView(projectId, operator); return tree(projectId); }

    private FileVersionView newVersion(DevFileView file) {
        return new FileVersionView(store.nextId(), file.id(), file.currentVersion(), file.content(), sha256(file.content()), false);
    }

    private void validateParentFolder(long projectId, Long folderId, Long currentFolderId) {
        if (folderId == null) return;
        DevFolderView folder = store.folders.get(folderId);
        if (folder == null || folder.projectId() != projectId || currentFolderId != null && folder.id() == currentFolderId) throw new BadRequestException("目标文件夹无效");
    }
    private Set<Long> descendantFolderIds(long id) {
        Set<Long> folderIds = new HashSet<>();
        folderIds.add(id);
        boolean expanded;
        do {
            expanded = false;
            for (DevFolderView folder : store.folders.values()) {
                if (folder.parentId() != null && folderIds.contains(folder.parentId()) && folderIds.add(folder.id())) expanded = true;
            }
        } while (expanded);
        return folderIds;
    }
    private void requireProject(long id) { if (!store.projects.containsKey(id)) throw new NotFoundException("项目不存在：" + id); }
    private String currentOwner(long id) {
        DevProjectView project = store.projects.get(id);
        return project == null || project.ownerName() == null || project.ownerName().isBlank() ? "admin" : project.ownerName();
    }
    private String normalizeOperator(String operator) { return operator == null || operator.isBlank() ? "admin" : operator.trim(); }

    private void requireProjectView(long projectId, String operator) {
        requireProject(projectId);
        if (!canProjectView(projectId, normalizeOperator(operator))) throw new BadRequestException("当前用户没有该项目的查看权限");
    }
    private boolean canProjectView(long projectId, String operator) {
        DevProjectView project = store.projects.get(projectId);
        if (project == null) return false;
        String username = normalizeOperator(operator);
        if (isAdministrator(username) || username.equalsIgnoreCase(project.ownerName())) return true;
        Long userId = userId(username);
        if (userId == null) return false;
        if (AccessService.effectivePermissions(store.userPermissions.getOrDefault(userId, Set.of())).contains(AccessService.DATA_DEVELOPMENT_PROJECT_ALL)) return true;
        return hasProjectPermission(projectId, userId, "VIEW") || hasProjectPermission(projectId, userId, "EDIT");
    }
    private void requireProjectEdit(long projectId, String operator) {
        DevProjectView project = store.projects.get(projectId);
        if (project == null) throw new NotFoundException("项目不存在：" + projectId);
        String username = normalizeOperator(operator);
        if (isAdministrator(username) || username.equalsIgnoreCase(project.ownerName())) return;
        Long userId = userId(username);
        if (userId != null && (hasProjectPermission(projectId, userId, "EDIT")
                || AccessService.effectivePermissions(store.userPermissions.getOrDefault(userId, Set.of())).contains(AccessService.DATA_DEVELOPMENT_PROJECT_ALL))) return;
        throw new BadRequestException("当前用户仅有查看权限，无法编辑该项目");
    }
    private boolean isAdministrator(String username) {
        if ("admin".equalsIgnoreCase(username)) return true;
        if (properties != null && properties.getSecurity().getAdminUsername() != null
                && username.equalsIgnoreCase(properties.getSecurity().getAdminUsername().trim())) return true;
        return store.users.values().stream().anyMatch(user -> username.equalsIgnoreCase(user.username())
                && "ACTIVE".equalsIgnoreCase(user.status()) && "ADMIN".equalsIgnoreCase(user.roleCode()));
    }
    private Long userId(String username) {
        return store.users.values().stream().filter(user -> username.equalsIgnoreCase(user.username())).map(user -> user.id()).findFirst().orElse(null);
    }
    private boolean hasProjectPermission(long projectId, long userId, String permission) {
        return store.projectPermissions.containsKey(projectId + ":" + userId + ":" + permission);
    }
    private DevFileView requireFile(long id) {
        DevFileView file = store.files.get(id);
        if (file == null) throw new NotFoundException("文件不存在：" + id);
        return file;
    }
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
