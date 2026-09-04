package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class DevelopmentService {
    private final PlatformStore store;

    public DevelopmentService(PlatformStore store) { this.store = store; }

    public List<DevProjectView> projects() { return store.projects.values().stream().toList(); }
    public DevProjectView createProject(DevelopmentRequests.ProjectRequest request) {
        long id = store.nextId();
        DevProjectView view = new DevProjectView(id, request.name(), request.description(), "ACTIVE");
        store.projects.put(id, view);
        store.persistProject(view);
        return view;
    }
    public DevProjectView updateProject(long id, DevelopmentRequests.ProjectRequest request) {
        if (!store.projects.containsKey(id)) throw new NotFoundException("项目不存在：" + id);
        DevProjectView view = new DevProjectView(id, request.name(), request.description(), "ACTIVE");
        store.projects.put(id, view);
        store.persistProject(view);
        return view;
    }
    public void deleteProject(long id) {
        if (store.files.values().stream().anyMatch(file -> file.projectId() == id)
                || store.folders.values().stream().anyMatch(folder -> folder.projectId() == id)) {
            throw new BadRequestException("项目非空，不能删除");
        }
        if (store.projects.remove(id) == null) throw new NotFoundException("项目不存在：" + id);
        store.deleteCore("dev_project", id);
    }

    public List<DevFolderView> folders(long projectId) {
        return store.folders.values().stream().filter(folder -> folder.projectId() == projectId).toList();
    }
    public DevFolderView createFolder(DevelopmentRequests.FolderRequest request) {
        requireProject(request.projectId());
        long id = store.nextId();
        DevFolderView view = new DevFolderView(id, request.projectId(), request.parentId(), request.name());
        store.folders.put(id, view);
        store.persistFolder(view);
        return view;
    }
    public DevFolderView updateFolder(long id, DevelopmentRequests.FolderUpdateRequest request) {
        DevFolderView current = store.folders.get(id);
        if (current == null) throw new NotFoundException("文件夹不存在：" + id);
        DevFolderView view = new DevFolderView(id, current.projectId(), current.parentId(), request.name());
        store.folders.put(id, view);
        store.persistFolder(view);
        return view;
    }
    public void deleteFolder(long id) {
        boolean hasFolder = store.folders.values().stream().anyMatch(folder -> folder.parentId() != null && id == folder.parentId());
        boolean hasFile = store.files.values().stream().anyMatch(file -> file.folderId() != null && file.folderId() == id);
        if (hasFolder || hasFile) throw new BadRequestException("文件夹非空，不能删除");
        if (store.folders.remove(id) == null) throw new NotFoundException("文件夹不存在：" + id);
        store.deleteCore("dev_folder", id);
    }

    public List<DevFileView> files(long projectId) {
        return store.files.values().stream().filter(file -> file.projectId() == projectId)
                .sorted(Comparator.comparing(DevFileView::name)).toList();
    }
    public DevFileView createFile(DevelopmentRequests.FileRequest request) {
        requireProject(request.projectId());
        long id = store.nextId();
        DevFileView view = new DevFileView(id, request.projectId(), request.folderId(), request.name(),
                request.fileType().toUpperCase(), request.content() == null ? "" : request.content(), "DRAFT", 1);
        store.files.put(id, view);
        saveVersion(view);
        store.persistFile(view);
        return view;
    }
    public DevFileView getFile(long id) { return requireFile(id); }
    public void deleteFile(long id) {
        if (store.files.remove(id) == null) throw new NotFoundException("文件不存在：" + id);
        store.versions.values().removeIf(version -> version.fileId() == id);
        store.deleteFileData(id);
    }
    public DevFileView saveFile(long id, DevelopmentRequests.SaveFileRequest request) {
        DevFileView current = requireFile(id);
        String name = request.name() == null || request.name().isBlank() ? current.name() : request.name().trim();
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), name,
                current.fileType(), request.content(), "DRAFT", current.currentVersion() + 1);
        store.files.put(id, updated);
        saveVersion(updated);
        store.persistFile(updated);
        return updated;
    }
    public List<FileVersionView> versions(long fileId) {
        requireFile(fileId);
        return store.versions.values().stream().filter(version -> version.fileId() == fileId)
                .sorted(Comparator.comparingInt(FileVersionView::versionNo).reversed()).toList();
    }
    public FileVersionView createVersion(long fileId, DevelopmentRequests.VersionRequest request) {
        DevFileView current = requireFile(fileId);
        DevFileView updated = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(),
                current.fileType(), request.content(), "DRAFT", current.currentVersion() + 1);
        store.files.put(fileId, updated);
        FileVersionView version = saveVersion(updated);
        store.persistFile(updated);
        return version;
    }
    public Map<String, Object> tree(long projectId) {
        requireProject(projectId);
        return Map.of("projectId", projectId, "folders", folders(projectId), "files", files(projectId));
    }

    private FileVersionView saveVersion(DevFileView file) {
        String checksum = sha256(file.content());
        long id = store.nextId();
        FileVersionView version = new FileVersionView(id, file.id(), file.currentVersion(), file.content(), checksum, false);
        store.versions.put(id, version);
        store.persistVersion(version);
        return version;
    }
    private void requireProject(long id) {
        if (!store.projects.containsKey(id)) throw new NotFoundException("项目不存在：" + id);
    }
    private DevFileView requireFile(long id) {
        DevFileView file = store.files.get(id);
        if (file == null) throw new NotFoundException("文件不存在：" + id);
        return file;
    }
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
