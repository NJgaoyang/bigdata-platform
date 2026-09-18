package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.DataSphereProperties;
import com.company.platform.system.AccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private DataSphereProperties properties;
    private JdbcTemplate jdbc;
    private AccessService accessService;

    public DevelopmentService(PlatformStore store) { this.store = store; }
    @Autowired public void setProperties(DataSphereProperties properties) { this.properties = properties; }
    @Autowired(required = false) public void setJdbcTemplate(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Autowired public void setAccessService(AccessService accessService) { this.accessService = accessService; }

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
            List<String> usages = store.workflows.values().stream()
                    .filter(workflow -> workflow.nodes().stream().anyMatch(node ->
                            node.devFileId() != null && fileIds.contains(node.devFileId())))
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

    public DevFileView createFile(DevelopmentRequests.FileRequest request) { return createFileInternal(request, "admin"); }
    @Transactional
    public DevFileView createFile(DevelopmentRequests.FileRequest request, String operator) {
        requireProjectEdit(request.projectId(), operator);
        return createFileInternal(request, normalizeOperator(operator));
    }
    private DevFileView createFileInternal(DevelopmentRequests.FileRequest request, String owner) {
        requireProject(request.projectId());
        validateParentFolder(request.projectId(), request.folderId(), null);
        long id = store.nextId();
        DevFileView view = new DevFileView(id, request.projectId(), request.folderId(), request.name(), request.fileType().toUpperCase(),
                request.content() == null ? "" : request.content(), request.description() == null ? "" : request.description().trim(), "DRAFT", 1,
                LocalDateTime.now(), "OFFLINE", false, owner);
        store.persistFile(view);
        FileVersionView version = newVersion(view);
        store.persistVersion(version);
        store.files.put(id, view);
        store.versions.put(version.id(), version);
        return view;
    }

    public DevFileView getFile(long id) { return requireFile(id); }
    public DevFileView getFile(long id, String operator) {
        DevFileView file = requireFile(id);
        requireProjectView(file.projectId(), operator);
        touchRecent(file.id(), operator);
        return file;
    }
    public List<Long> recentFileIds(long projectId, String operator) {
        requireProjectView(projectId, operator);
        if (jdbc == null) return List.of();
        return jdbc.query("SELECT r.file_id FROM dev_file_recent r JOIN dev_file f ON f.id=r.file_id WHERE r.user_name=? AND f.project_id=? AND f.recycled=FALSE ORDER BY r.last_opened_at DESC LIMIT 50",
                (rs,n) -> rs.getLong(1), normalizeOperator(operator), projectId);
    }
    private void touchRecent(long fileId, String operator) {
        if (jdbc == null) return;
        jdbc.update("INSERT INTO dev_file_recent(user_name,file_id,last_opened_at) VALUES(?,?,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE last_opened_at=CURRENT_TIMESTAMP",
                normalizeOperator(operator), fileId);
    }
    public void requireFileEdit(long id, String operator) { requireProjectEdit(requireFile(id).projectId(), operator); }

    public void deleteFile(long id) { recycleFile(id, "admin"); }

    @Transactional
    public void deleteFile(long id, String operator) {
        requireProjectEdit(requireFile(id).projectId(), operator);
        recycleFile(id, operator);
    }

    private void recycleFile(long id, String operator) {
        DevFileView current = requireFile(id);
        if (!"OFFLINE".equalsIgnoreCase(current.lifecycleStatus())) {
            throw new BadRequestException("开发任务必须先下线后才能删除");
        }
        List<String> usages = store.workflows.values().stream()
                .filter(workflow -> workflow.nodes().stream().anyMatch(node ->
                        node.devFileId() != null && node.devFileId() == id))
                .map(workflow -> workflow.name() + (workflow.status() == null || workflow.status().isBlank() ? "" : "（" + workflow.status() + "）"))
                .sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
        if (!usages.isEmpty()) throw new BadRequestException("文件“" + current.name() + "”正在被调度任务使用：" + String.join("、", usages) + "，不能删除");
        if (jdbc != null) {
            jdbc.update("UPDATE dev_file SET recycled=TRUE,recycled_at=CURRENT_TIMESTAMP,recycled_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    normalizeOperator(operator), id);
        }
        store.files.remove(id);
    }

    public List<RecycledFileView> recycledFiles(long projectId, String operator) {
        requireProjectView(projectId, operator);
        if (jdbc == null) return List.of();
        return jdbc.query("SELECT f.id,f.project_id,f.folder_id,d.name AS folder_name,f.name,f.file_type,f.description,f.status,f.current_version,f.recycled_at,f.recycled_by " +
                        "FROM dev_file f LEFT JOIN dev_folder d ON d.id=f.folder_id WHERE f.project_id=? AND f.recycled=TRUE ORDER BY f.recycled_at DESC",
                (rs,n) -> new RecycledFileView(rs.getLong("id"), rs.getLong("project_id"), rs.getObject("folder_id", Long.class),
                        rs.getString("folder_name"), rs.getString("name"), rs.getString("file_type"), rs.getString("description"),
                        rs.getString("status"), rs.getInt("current_version"),
                        rs.getTimestamp("recycled_at") == null ? null : rs.getTimestamp("recycled_at").toLocalDateTime(), rs.getString("recycled_by")), projectId);
    }

    @Transactional
    public DevFileView restoreFile(long id, String operator) {
        if (jdbc == null) throw new BadRequestException("当前环境不支持回收箱恢复");
        Map<String,Object> row = jdbc.queryForMap("SELECT id,project_id,folder_id,name,file_type,content,description,status,current_version,updated_at,lifecycle_status,ever_online,owner_name FROM dev_file WHERE id=? AND recycled=TRUE", id);
        long projectId = ((Number) row.get("project_id")).longValue();
        requireProjectEdit(projectId, operator);
        jdbc.update("UPDATE dev_file SET recycled=FALSE,recycled_at=NULL,recycled_by=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        Long folderId = row.get("folder_id") == null ? null : ((Number) row.get("folder_id")).longValue();
        LocalDateTime updatedAt = row.get("updated_at") instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : LocalDateTime.now();
        DevFileView restored = new DevFileView(id, projectId, folderId, String.valueOf(row.get("name")), String.valueOf(row.get("file_type")),
                String.valueOf(row.get("content")), row.get("description") == null ? "" : String.valueOf(row.get("description")),
                String.valueOf(row.get("status")), ((Number) row.get("current_version")).intValue(), updatedAt,
                String.valueOf(row.get("lifecycle_status")), Boolean.TRUE.equals(row.get("ever_online")) || (row.get("ever_online") instanceof Number n && n.intValue()!=0),
                row.get("owner_name") == null ? "admin" : String.valueOf(row.get("owner_name")));
        store.files.put(id, restored);
        return restored;
    }

    @Transactional
    public void permanentlyDeleteFile(long id, String operator) {
        if (jdbc == null) throw new BadRequestException("当前环境不支持彻底删除");
        List<Long> projectIds = jdbc.query("SELECT project_id FROM dev_file WHERE id=? AND recycled=TRUE", (rs,n)->rs.getLong(1), id);
        if (projectIds.isEmpty()) throw new NotFoundException("回收箱文件不存在：" + id);
        requireProjectEdit(projectIds.getFirst(), operator);
        store.deleteFileData(id);
        store.files.remove(id);
        store.versions.values().removeIf(version -> version.fileId() == id);
    }

    private boolean hasPublishedVersion(long id) {
        if (jdbc != null) {
            Integer count = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM dev_file_release_bundle WHERE file_id=?) + (SELECT COUNT(*) FROM dev_file_version WHERE file_id=? AND publish_flag=TRUE)", Integer.class, id, id);
            return count != null && count > 0;
        }
        DevFileView file = store.files.get(id);
        return file != null && "PUBLISHED".equalsIgnoreCase(file.status())
                || store.versions.values().stream().anyMatch(v -> v.fileId() == id && v.publishFlag());
    }

    public DevFileView saveFile(long id, DevelopmentRequests.SaveFileRequest request) {
        DevFileView current = requireFile(id);
        requireOfflineForEdit(current);
        String name = request.name() == null || request.name().isBlank() ? current.name() : request.name().trim();
        Long folderId = Boolean.TRUE.equals(request.moveToRoot()) ? null : request.folderId() == null ? current.folderId() : request.folderId();
        validateParentFolder(current.projectId(), folderId, null);
        String content = request.content() == null ? current.content() : request.content();
        boolean contentChanged = !java.util.Objects.equals(content, current.content());
        int nextVersion = contentChanged ? current.currentVersion() + 1 : current.currentVersion();
        String nextStatus = contentChanged ? "DRAFT" : current.status();
        DevFileView updated = new DevFileView(current.id(), current.projectId(), folderId, name, current.fileType(), content,
                request.description() == null ? current.description() : request.description().trim(), nextStatus, nextVersion,
                LocalDateTime.now(), current.lifecycleStatus(), current.everOnline(), current.ownerName());
        if (contentChanged) {
            FileVersionView version = newVersion(updated);
            store.persistVersion(version);
            store.versions.put(version.id(), version);
        }
        store.persistFile(updated);
        store.files.put(id, updated);
        return updated;
    }
    @Transactional
    public DevFileView saveFile(long id, DevelopmentRequests.SaveFileRequest request, String operator) { requireProjectEdit(requireFile(id).projectId(), operator); return saveFile(id, request); }

    /**
     * Create a new user-visible task version when non-code task configuration changes.
     * The code snapshot is intentionally duplicated so task version Vx always identifies
     * one complete development state while schedule/release sequence numbers stay internal.
     */
    @Transactional
    public DevFileView bumpTaskVersion(long fileId, String operator) {
        DevFileView current = requireFile(fileId);
        requireProjectEdit(current.projectId(), operator);
        requireOfflineForEdit(current);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(),
                current.content(), current.description(), "DRAFT", current.currentVersion() + 1, LocalDateTime.now(),
                current.lifecycleStatus(), current.everOnline(), current.ownerName());
        FileVersionView version = newVersion(updated);
        store.persistVersion(version);
        store.persistFile(updated);
        store.versions.put(version.id(), version);
        store.files.put(fileId, updated);
        return updated;
    }

    public List<FileVersionView> versions(long fileId) {
        requireFile(fileId);
        return store.versions.values().stream().filter(version -> version.fileId() == fileId)
                .sorted(Comparator.comparingInt(FileVersionView::versionNo).reversed()).toList();
    }
    public List<FileVersionView> versions(long fileId, String operator) { requireProjectView(requireFile(fileId).projectId(), operator); return versions(fileId); }

    public FileVersionView createVersion(long fileId, DevelopmentRequests.VersionRequest request) {
        DevFileView current = requireFile(fileId);
        requireOfflineForEdit(current);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(), request.content(),
                current.description(), "DRAFT", current.currentVersion() + 1, LocalDateTime.now(), current.lifecycleStatus(), current.everOnline(), current.ownerName());
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
        if (!"ONLINE".equalsIgnoreCase(current.lifecycleStatus())) throw new BadRequestException("开发任务当前已下线，请先上线后再发布");
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
                current.description(), "PUBLISHED", current.currentVersion(), LocalDateTime.now(), current.lifecycleStatus(), current.everOnline(), current.ownerName());
        store.persistFile(published);
        updates.forEach(version -> store.versions.put(version.id(), version));
        store.files.put(fileId, published);
        return published;
    }

    @Transactional
    public DevFileView onlineFile(long id, String operator) {
        DevFileView current = requireFile(id);
        requireProjectEdit(current.projectId(), operator);
        if ("ONLINE".equalsIgnoreCase(current.lifecycleStatus())) return current;
        clearPublishedVersions(id);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(),
                current.content(), current.description(), "DRAFT", current.currentVersion(), LocalDateTime.now(), "ONLINE", true, current.ownerName());
        store.persistFile(updated); store.files.put(id, updated); return updated;
    }

    @Transactional
    public DevFileView unpublishFile(long id, String operator) {
        DevFileView current = requireFile(id);
        requireProjectEdit(current.projectId(), operator);
        if (!"ONLINE".equalsIgnoreCase(current.lifecycleStatus())) throw new BadRequestException("任务已下线，不能取消发布");
        if (!"PUBLISHED".equalsIgnoreCase(current.status())) throw new BadRequestException("当前上线版本尚未发布");
        clearPublishedVersions(id);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(),
                current.content(), current.description(), "DRAFT", current.currentVersion(), LocalDateTime.now(), "ONLINE", current.everOnline(), current.ownerName());
        store.persistFile(updated); store.files.put(id, updated); return updated;
    }

    private void clearPublishedVersions(long fileId) {
        List<FileVersionView> updates = store.versions.values().stream()
                .filter(version -> version.fileId() == fileId && version.publishFlag())
                .map(version -> new FileVersionView(version.id(), version.fileId(), version.versionNo(), version.content(), version.checksum(), false))
                .toList();
        updates.forEach(store::persistVersion);
        updates.forEach(version -> store.versions.put(version.id(), version));
    }

    @Transactional
    public DevFileView offlineFile(long id, String operator) {
        DevFileView current = requireFile(id);
        requireProjectEdit(current.projectId(), operator);
        if ("OFFLINE".equalsIgnoreCase(current.lifecycleStatus())) return current;
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(),
                current.content(), current.description(), current.status(), current.currentVersion(), LocalDateTime.now(), "OFFLINE", current.everOnline(), current.ownerName());
        store.persistFile(updated); store.files.put(id, updated); return updated;
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
        if (!canProjectView(projectId, normalizeOperator(operator))) throw new BadRequestException("当前用户没有数据开发查看权限");
    }
    private boolean canProjectView(long projectId, String operator) {
        if (!store.projects.containsKey(projectId)) return false;
        String username = normalizeOperator(operator);
        if (isAdministrator(username)) return true;
        Long userId = userId(username);
        if (userId == null) return false;
        Set<String> permissions = accessService == null
                ? AccessService.effectivePermissions(store.userPermissions.getOrDefault(userId, Set.of()))
                : accessService.effectivePermissions(userId);
        boolean projectAll = permissions.contains(AccessService.DATA_DEVELOPMENT_PROJECT_ALL);
        boolean moduleView = permissions.contains("DATA_DEVELOPMENT_VIEW") || permissions.contains("DATA_DEVELOPMENT_EDIT") || projectAll;
        if (!moduleView) return false;
        if (projectAll) return true;
        DevProjectView project = store.projects.get(projectId);
        boolean owner = project.ownerName() != null && username.equalsIgnoreCase(project.ownerName());
        boolean assigned = store.projectPermissions.containsKey(projectId + ":" + userId + ":VIEW")
                || store.projectPermissions.containsKey(projectId + ":" + userId + ":EDIT");
        return owner || assigned;
    }
    private void requireProjectEdit(long projectId, String operator) {
        requireProject(projectId);
        String username = normalizeOperator(operator);
        if (isAdministrator(username)) return;
        Long userId = userId(username);
        if (userId != null) {
            Set<String> permissions = accessService == null
                    ? AccessService.effectivePermissions(store.userPermissions.getOrDefault(userId, Set.of()))
                    : accessService.effectivePermissions(userId);
            boolean projectAll = permissions.contains(AccessService.DATA_DEVELOPMENT_PROJECT_ALL);
            if (projectAll) return;
            DevProjectView project = store.projects.get(projectId);
            boolean owner = project.ownerName() != null && username.equalsIgnoreCase(project.ownerName());
            boolean assignedEdit = store.projectPermissions.containsKey(projectId + ":" + userId + ":EDIT");
            if (permissions.contains("DATA_DEVELOPMENT_EDIT") && (owner || assignedEdit)) return;
        }
        throw new BadRequestException("当前用户仅有数据开发查看权限，无法编辑任务");
    }
    private void requireOfflineForEdit(DevFileView file) {
        if (file != null && "ONLINE".equalsIgnoreCase(file.lifecycleStatus())) {
            throw new BadRequestException("任务已上线，请先下线后再修改");
        }
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
