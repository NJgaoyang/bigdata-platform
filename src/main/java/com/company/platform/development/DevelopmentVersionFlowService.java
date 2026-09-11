package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.ForbiddenException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the development delivery state machine:
 * personal development -> pushed project version -> online version.
 *
 * A logical version number never changes while moving through the stages. If
 * personal development is V12, project space receives V12 and publishing makes
 * that exact V12 the online version.
 */
@Service
public class DevelopmentVersionFlowService {
    private final PlatformStore store;
    private final DevelopmentService development;
    private final DevelopmentAccessService access;
    private final Map<Long, DevFileDeliveryView> memoryDeliveries = new ConcurrentHashMap<>();
    private JdbcTemplate jdbc;

    public DevelopmentVersionFlowService(PlatformStore store,
                                         DevelopmentService development,
                                         DevelopmentAccessService access) {
        this.store = store;
        this.development = development;
        this.access = access;
    }

    @Autowired(required = false)
    public void setJdbcTemplate(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public synchronized DevFileView pushToProject(DevelopmentVersionFlowRequests.PushToProjectRequest request,
                                                   String operator) {
        DevelopmentAccessService.ModuleAccess module = access.moduleAccess(operator);
        if (!module.edit()) throw new ForbiddenException("当前账号没有数据开发编辑权限");
        if (request.sourceFileId() <= 0) throw new BadRequestException("请先保存当前开发文件后再推送");
        if (request.projectId() <= 0) throw new BadRequestException("请选择目标项目");

        DevFileView source = development.getFile(request.sourceFileId(), operator);
        DevProjectView sourceProject = store.projects.get(source.projectId());
        if (sourceProject == null || !normalize(operator).equalsIgnoreCase(sourceProject.ownerName())) {
            throw new ForbiddenException("只能从自己的开发空间推送版本");
        }
        if (source.projectId() == request.projectId()) throw new BadRequestException("目标项目不能与当前开发空间相同");

        DevelopmentAccessService.ProjectAccess targetAccess = access.projectAccess(request.projectId(), operator);
        if (!targetAccess.edit()) throw new ForbiddenException("当前用户仅有目标项目查看权限，不能推送版本");
        validateFolder(request.projectId(), request.folderId());

        FileVersionView sourceVersion = version(source.id(), source.currentVersion());
        String requestedName = request.name() == null ? "" : request.name().trim();
        if (requestedName.isBlank()) requestedName = source.name();
        String description = request.description() == null ? source.description() : request.description().trim();

        DevFileView target = store.files.values().stream()
                .filter(file -> file.projectId() == request.projectId())
                .filter(file -> Objects.equals(file.folderId(), request.folderId()))
                .filter(file -> requestedName.equalsIgnoreCase(file.name()))
                .findFirst().orElse(null);

        if (target != null) {
            DevFileDeliveryView existingLink = deliveryForProjectFile(target.id());
            if (existingLink != null && existingLink.sourceFileId() != source.id()) {
                throw new BadRequestException("目标目录已存在由其他开发文件推送的同名文件");
            }
            if (!source.fileType().equalsIgnoreCase(target.fileType())) {
                throw new BadRequestException("目标目录已存在同名的其他类型文件");
            }
            if (source.currentVersion() < target.currentVersion()) {
                throw new BadRequestException("项目空间已有更高版本 V" + target.currentVersion() + "，不能推送旧版本 V" + source.currentVersion());
            }
        }

        Integer onlineVersion = target == null ? null : publishedVersionNo(target.id());
        if (target == null) {
            long id = store.nextId();
            target = new DevFileView(id, request.projectId(), request.folderId(), requestedName, source.fileType(),
                    sourceVersion.content(), description, "PENDING_PUBLISH", source.currentVersion(), LocalDateTime.now());
            // dev_file_version has a foreign key to dev_file, so persist the file first.
            store.persistFile(target);
            FileVersionView pushed = copyVersion(target.id(), sourceVersion, false);
            store.persistVersion(pushed);
            store.files.put(target.id(), target);
            store.versions.put(pushed.id(), pushed);
        } else {
            FileVersionView sameNumber = findVersion(target.id(), source.currentVersion());
            if (sameNumber != null && !Objects.equals(sameNumber.checksum(), sourceVersion.checksum())) {
                throw new BadRequestException("项目空间 V" + source.currentVersion() + " 已存在且内容不同，请先处理版本冲突");
            }
            if (sameNumber == null) {
                FileVersionView pushed = copyVersion(target.id(), sourceVersion, false);
                store.persistVersion(pushed);
                store.versions.put(pushed.id(), pushed);
            }
            String status = Objects.equals(onlineVersion, source.currentVersion()) ? "PUBLISHED" : "PENDING_PUBLISH";
            target = new DevFileView(target.id(), target.projectId(), request.folderId(), requestedName, target.fileType(),
                    sourceVersion.content(), description, status, source.currentVersion(), LocalDateTime.now());
            store.persistFile(target);
            store.files.put(target.id(), target);
        }

        persistDelivery(new DevFileDeliveryView(target.id(), source.id(), source.currentVersion(), onlineVersion, LocalDateTime.now()));
        return target;
    }

    @Transactional
    public synchronized DevFileView publish(long projectFileId, String operator) {
        DevFileView current = requireProjectFile(projectFileId, operator, true);
        DevFileDeliveryView delivery = deliveryForProjectFile(projectFileId);
        if (delivery == null) throw new BadRequestException("该文件还没有通过“推送到项目”进入版本发布流程");
        if (delivery.pushedVersionNo() != current.currentVersion()) {
            throw new BadRequestException("当前项目版本与最近推送版本不一致，请刷新后重试");
        }
        FileVersionView target = version(projectFileId, delivery.pushedVersionNo());

        List<FileVersionView> updates = new ArrayList<>();
        for (FileVersionView item : versions(projectFileId)) {
            boolean shouldPublish = item.id() == target.id();
            if (item.publishFlag() != shouldPublish) {
                updates.add(new FileVersionView(item.id(), item.fileId(), item.versionNo(), item.content(), item.checksum(), shouldPublish));
            }
        }
        updates.forEach(store::persistVersion);
        updates.forEach(item -> store.versions.put(item.id(), item));

        DevFileView published = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(),
                target.content(), current.description(), "PUBLISHED", target.versionNo(), LocalDateTime.now());
        store.persistFile(published);
        store.files.put(published.id(), published);
        persistDelivery(new DevFileDeliveryView(projectFileId, delivery.sourceFileId(), delivery.pushedVersionNo(), target.versionNo(), LocalDateTime.now()));
        return published;
    }

    @Transactional
    public synchronized DevFileView unpublish(long projectFileId, String operator) {
        DevFileView current = requireProjectFile(projectFileId, operator, true);
        DevFileDeliveryView delivery = deliveryForProjectFile(projectFileId);
        if (delivery == null) throw new BadRequestException("该文件不在受控发布流程中");
        for (FileVersionView item : versions(projectFileId)) {
            if (!item.publishFlag()) continue;
            FileVersionView offline = new FileVersionView(item.id(), item.fileId(), item.versionNo(), item.content(), item.checksum(), false);
            store.persistVersion(offline);
            store.versions.put(offline.id(), offline);
        }
        DevFileView offlineFile = new DevFileView(current.id(), current.projectId(), current.folderId(), current.name(), current.fileType(),
                current.content(), current.description(), "OFFLINE", current.currentVersion(), LocalDateTime.now());
        store.persistFile(offlineFile);
        store.files.put(offlineFile.id(), offlineFile);
        persistDelivery(new DevFileDeliveryView(projectFileId, delivery.sourceFileId(), delivery.pushedVersionNo(), null, LocalDateTime.now()));
        return offlineFile;
    }

    /**
     * Opens the online version back in the operator's personal workspace.
     * The online version remains immutable and online; the next ordinary Save
     * in personal development creates V(n+1).
     */
    @Transactional
    public synchronized DevFileView createDevelopmentVersion(long projectFileId, String operator) {
        DevelopmentAccessService.ModuleAccess module = access.moduleAccess(operator);
        if (!module.edit()) throw new ForbiddenException("当前账号没有数据开发编辑权限");
        DevFileView projectFile = requireProjectFile(projectFileId, operator, false);
        Integer onlineVersionNo = publishedVersionNo(projectFileId);
        if (onlineVersionNo == null) throw new BadRequestException("当前文件没有线上版本，无法创建开发版本");
        FileVersionView onlineVersion = version(projectFileId, onlineVersionNo);

        DevFileDeliveryView delivery = deliveryForProjectFile(projectFileId);
        DevFileView source = delivery == null ? null : store.files.get(delivery.sourceFileId());
        if (source != null && !isOwnedBy(source.projectId(), operator)) source = null;
        if (source == null) source = findPersonalFile(operator, projectFile.name(), projectFile.fileType());
        if (source == null) source = createPersonalBase(operator, projectFile, onlineVersion);
        else source = synchronizePersonalBase(source, onlineVersion);

        int pushedVersion = delivery == null ? onlineVersionNo : delivery.pushedVersionNo();
        persistDelivery(new DevFileDeliveryView(projectFileId, source.id(), pushedVersion, onlineVersionNo, LocalDateTime.now()));
        return source;
    }

    public DevelopmentVersionState state(long fileId, String operator) {
        DevFileView requested = development.getFile(fileId, operator);
        DevFileDeliveryView delivery = deliveryForProjectFile(fileId);
        boolean projectFile = delivery != null;
        if (delivery == null) delivery = deliveryForSourceFile(fileId);

        if (delivery == null) {
            Integer online = publishedVersionNo(fileId);
            return new DevelopmentVersionState(fileId, fileId, null, requested.projectId(), requested.currentVersion(), null, online,
                    requested.status(), false, false, online != null, false);
        }

        DevFileView source = store.files.get(delivery.sourceFileId());
        DevFileView project = store.files.get(delivery.projectFileId());
        int developmentVersion = source == null ? delivery.pushedVersionNo() : source.currentVersion();
        Integer pushed = delivery.pushedVersionNo();
        Integer online = delivery.onlineVersionNo();
        boolean pendingPush = developmentVersion > pushed;
        boolean pendingPublish = pushed != null && !Objects.equals(pushed, online);
        String status = project == null ? requested.status() : project.status();
        return new DevelopmentVersionState(fileId, delivery.sourceFileId(), delivery.projectFileId(),
                project == null ? requested.projectId() : project.projectId(), developmentVersion, pushed, online,
                status, pendingPush, pendingPublish, online != null, projectFile);
    }

    public boolean isManagedProjectFile(long fileId) {
        return deliveryForProjectFile(fileId) != null;
    }

    private DevFileView requireProjectFile(long fileId, String operator, boolean requireEdit) {
        DevFileView file = development.getFile(fileId, operator);
        DevelopmentAccessService.ProjectAccess projectAccess = access.projectAccess(file.projectId(), operator);
        if (requireEdit && !projectAccess.edit()) throw new ForbiddenException("当前用户仅有项目查看权限，不能执行该操作");
        return file;
    }

    private DevFileView createPersonalBase(String operator, DevFileView projectFile, FileVersionView onlineVersion) {
        DevProjectView personal = personalProject(operator);
        long id = store.nextId();
        DevFileView source = new DevFileView(id, personal.id(), null, projectFile.name(), projectFile.fileType(), onlineVersion.content(),
                projectFile.description(), "DRAFT", onlineVersion.versionNo(), LocalDateTime.now());
        store.persistFile(source);
        FileVersionView base = new FileVersionView(store.nextId(), source.id(), onlineVersion.versionNo(), onlineVersion.content(), onlineVersion.checksum(), false);
        store.persistVersion(base);
        store.files.put(source.id(), source);
        store.versions.put(base.id(), base);
        return source;
    }

    private DevFileView synchronizePersonalBase(DevFileView source, FileVersionView onlineVersion) {
        if (source.currentVersion() > onlineVersion.versionNo()) return source;
        FileVersionView existing = findVersion(source.id(), onlineVersion.versionNo());
        if (source.currentVersion() == onlineVersion.versionNo()) {
            if (existing != null && Objects.equals(existing.checksum(), onlineVersion.checksum())) return source;
            if (existing != null) throw new BadRequestException("我的开发中 V" + onlineVersion.versionNo() + " 与线上版本内容冲突");
        }
        if (existing != null && !Objects.equals(existing.checksum(), onlineVersion.checksum())) {
            throw new BadRequestException("我的开发中 V" + onlineVersion.versionNo() + " 与线上版本内容冲突");
        }
        if (existing == null) {
            FileVersionView base = new FileVersionView(store.nextId(), source.id(), onlineVersion.versionNo(), onlineVersion.content(), onlineVersion.checksum(), false);
            store.persistVersion(base);
            store.versions.put(base.id(), base);
        }
        DevFileView synchronizedFile = new DevFileView(source.id(), source.projectId(), source.folderId(), source.name(), source.fileType(),
                onlineVersion.content(), source.description(), "DRAFT", onlineVersion.versionNo(), LocalDateTime.now());
        store.persistFile(synchronizedFile);
        store.files.put(synchronizedFile.id(), synchronizedFile);
        return synchronizedFile;
    }

    private DevProjectView personalProject(String operator) {
        String username = normalize(operator);
        DevProjectView existing = store.projects.values().stream()
                .filter(project -> username.equalsIgnoreCase(project.ownerName()))
                .filter(this::isPersonalProject)
                .findFirst().orElse(null);
        if (existing != null) return existing;
        return development.createProject(new DevelopmentRequests.ProjectRequest("我的开发", "个人工作区"), username);
    }

    private DevFileView findPersonalFile(String operator, String name, String fileType) {
        String username = normalize(operator);
        return store.files.values().stream()
                .filter(file -> name.equalsIgnoreCase(file.name()) && fileType.equalsIgnoreCase(file.fileType()))
                .filter(file -> {
                    DevProjectView project = store.projects.get(file.projectId());
                    return project != null && username.equalsIgnoreCase(project.ownerName()) && isPersonalProject(project);
                })
                .max(Comparator.comparingInt(DevFileView::currentVersion))
                .orElse(null);
    }

    private boolean isPersonalProject(DevProjectView project) {
        String name = project.name() == null ? "" : project.name().trim();
        String description = project.description() == null ? "" : project.description().trim();
        return "我的开发".equals(name) || "默认开发空间".equals(name) || "个人工作区".equals(description);
    }

    private boolean isOwnedBy(long projectId, String operator) {
        DevProjectView project = store.projects.get(projectId);
        return project != null && normalize(operator).equalsIgnoreCase(project.ownerName()) && isPersonalProject(project);
    }

    private void validateFolder(long projectId, Long folderId) {
        if (folderId == null) return;
        DevFolderView folder = store.folders.get(folderId);
        if (folder == null || folder.projectId() != projectId) throw new BadRequestException("目标文件夹无效");
    }

    private FileVersionView copyVersion(long targetFileId, FileVersionView source, boolean published) {
        return new FileVersionView(store.nextId(), targetFileId, source.versionNo(), source.content(), source.checksum(), published);
    }

    private List<FileVersionView> versions(long fileId) {
        return store.versions.values().stream().filter(item -> item.fileId() == fileId)
                .sorted(Comparator.comparingInt(FileVersionView::versionNo)).toList();
    }

    private FileVersionView version(long fileId, int versionNo) {
        FileVersionView value = findVersion(fileId, versionNo);
        if (value == null) throw new NotFoundException("文件 V" + versionNo + " 版本不存在");
        return value;
    }

    private FileVersionView findVersion(long fileId, int versionNo) {
        return store.versions.values().stream()
                .filter(item -> item.fileId() == fileId && item.versionNo() == versionNo)
                .findFirst().orElse(null);
    }

    private Integer publishedVersionNo(long fileId) {
        return store.versions.values().stream()
                .filter(item -> item.fileId() == fileId && item.publishFlag())
                .map(FileVersionView::versionNo)
                .max(Integer::compareTo).orElse(null);
    }

    private DevFileDeliveryView deliveryForProjectFile(long projectFileId) {
        if (jdbc == null) return memoryDeliveries.get(projectFileId);
        return jdbc.query("SELECT project_file_id,source_file_id,pushed_version_no,online_version_no,updated_at FROM dev_file_delivery WHERE project_file_id=?",
                rs -> {
                    if (!rs.next()) return null;
                    var updated = rs.getTimestamp("updated_at");
                    return new DevFileDeliveryView(rs.getLong("project_file_id"), rs.getLong("source_file_id"), rs.getInt("pushed_version_no"),
                            rs.getObject("online_version_no", Integer.class), updated == null ? null : updated.toLocalDateTime());
                }, projectFileId);
    }

    private DevFileDeliveryView deliveryForSourceFile(long sourceFileId) {
        if (jdbc == null) return memoryDeliveries.values().stream()
                .filter(item -> item.sourceFileId() == sourceFileId)
                .max(Comparator.comparing(item -> item.updatedAt() == null ? LocalDateTime.MIN : item.updatedAt())).orElse(null);
        return jdbc.query("SELECT project_file_id,source_file_id,pushed_version_no,online_version_no,updated_at FROM dev_file_delivery WHERE source_file_id=? ORDER BY updated_at DESC LIMIT 1",
                rs -> {
                    if (!rs.next()) return null;
                    var updated = rs.getTimestamp("updated_at");
                    return new DevFileDeliveryView(rs.getLong("project_file_id"), rs.getLong("source_file_id"), rs.getInt("pushed_version_no"),
                            rs.getObject("online_version_no", Integer.class), updated == null ? null : updated.toLocalDateTime());
                }, sourceFileId);
    }

    private void persistDelivery(DevFileDeliveryView delivery) {
        memoryDeliveries.put(delivery.projectFileId(), delivery);
        if (jdbc == null) return;
        jdbc.update("INSERT INTO dev_file_delivery (project_file_id,source_file_id,pushed_version_no,online_version_no) VALUES (?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE source_file_id=VALUES(source_file_id),pushed_version_no=VALUES(pushed_version_no),online_version_no=VALUES(online_version_no),updated_at=CURRENT_TIMESTAMP",
                delivery.projectFileId(), delivery.sourceFileId(), delivery.pushedVersionNo(), delivery.onlineVersionNo());
    }

    private String normalize(String operator) {
        return operator == null || operator.isBlank() ? "admin" : operator.trim();
    }

    @SuppressWarnings("unused")
    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record DevelopmentVersionState(
            long requestedFileId,
            Long sourceFileId,
            Long projectFileId,
            Long projectId,
            int developmentVersion,
            Integer pushedVersion,
            Integer onlineVersion,
            String status,
            boolean pendingPush,
            boolean pendingPublish,
            boolean online,
            boolean projectFile) { }
}
