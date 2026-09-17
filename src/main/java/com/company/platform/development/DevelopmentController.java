package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/development", "/api"})
public class DevelopmentController {
    private final DevelopmentService service;
    private final DevelopmentScheduleService schedules;

    public DevelopmentController(DevelopmentService service, DevelopmentScheduleService schedules) {
        this.service = service;
        this.schedules = schedules;
    }

    @GetMapping("/projects")
    public Result<List<DevProjectView>> projects(HttpServletRequest servletRequest) {
        return Result.ok(service.projects(operator(servletRequest), true));
    }

    @PostMapping("/projects")
    public Result<DevProjectView> createProject(@Valid @RequestBody DevelopmentRequests.ProjectRequest request,
                                                 HttpServletRequest servletRequest) {
        throw new BadRequestException("系统采用单项目共享开发模式，不支持新建开发项目");
    }

    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable long id, HttpServletRequest servletRequest) {
        throw new BadRequestException("系统采用单项目共享开发模式，不支持删除开发项目");
    }

    @PutMapping("/projects/{id}")
    public Result<DevProjectView> updateProject(@PathVariable long id,
                                                 @Valid @RequestBody DevelopmentRequests.ProjectRequest request,
                                                 HttpServletRequest servletRequest) {
        throw new BadRequestException("系统采用单项目共享开发模式，项目由平台统一维护");
    }

    @GetMapping("/projects/{id}/tree")
    public Result<java.util.Map<String, Object>> tree(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.tree(id, operator(servletRequest)));
    }

    @GetMapping("/folders")
    public Result<List<DevFolderView>> folders(@RequestParam long projectId, HttpServletRequest servletRequest) {
        return Result.ok(service.folders(projectId, operator(servletRequest)));
    }

    @PostMapping("/folders")
    public Result<DevFolderView> createFolder(@Valid @RequestBody DevelopmentRequests.FolderRequest request,
                                               HttpServletRequest servletRequest) {
        return Result.ok(service.createFolder(request, operator(servletRequest)));
    }

    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable long id, HttpServletRequest servletRequest) {
        service.deleteFolder(id, operator(servletRequest));
        return Result.ok(null);
    }

    @PutMapping("/folders/{id}")
    public Result<DevFolderView> updateFolder(@PathVariable long id,
                                               @Valid @RequestBody DevelopmentRequests.FolderUpdateRequest request,
                                               HttpServletRequest servletRequest) {
        return Result.ok(service.updateFolder(id, request, operator(servletRequest)));
    }

    @GetMapping("/files")
    public Result<List<DevFileView>> files(@RequestParam long projectId, HttpServletRequest servletRequest) {
        return Result.ok(service.files(projectId, operator(servletRequest)));
    }

    @GetMapping("/files/recent")
    public Result<List<Long>> recentFiles(@RequestParam long projectId, HttpServletRequest servletRequest) {
        return Result.ok(service.recentFileIds(projectId, operator(servletRequest)));
    }

    @PostMapping("/files")
    public Result<DevFileView> createFile(@Valid @RequestBody DevelopmentRequests.FileRequest request,
                                           HttpServletRequest servletRequest) {
        return Result.ok(service.createFile(request, operator(servletRequest)));
    }

    @GetMapping("/files/{id}")
    public Result<DevFileView> getFile(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.getFile(id, operator(servletRequest)));
    }

    @PutMapping("/files/{id}")
    public Result<DevFileView> saveFile(@PathVariable long id,
                                         @Valid @RequestBody DevelopmentRequests.SaveFileRequest request,
                                         HttpServletRequest servletRequest) {
        return Result.ok(service.saveFile(id, request, operator(servletRequest)), "文件已保存");
    }

    @PostMapping("/files/{id}/online")
    public Result<DevFileView> onlineFile(@PathVariable long id, HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        DevFileView before = service.getFile(id, operator);
        DevFileView updated = service.onlineFile(id, operator);
        try {
            if (!"ONLINE".equalsIgnoreCase(before.lifecycleStatus())) schedules.resetPublication(id);
            schedules.refreshLifecycle(id);
        }
        catch (RuntimeException ex) { service.offlineFile(id, operator); try { schedules.refreshLifecycle(id); } catch (RuntimeException ignored) {} throw ex; }
        return Result.ok(updated, "任务已上线，等待发布");
    }

    @PostMapping("/files/{id}/offline")
    public Result<DevFileView> offlineFile(@PathVariable long id, HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        DevFileView updated = service.offlineFile(id, operator);
        try { schedules.refreshLifecycle(id); }
        catch (RuntimeException ex) { service.onlineFile(id, operator); try { schedules.refreshLifecycle(id); } catch (RuntimeException ignored) {} throw ex; }
        return Result.ok(updated, "任务已下线");
    }

    @DeleteMapping("/files/{id}")
    public Result<Void> deleteFile(@PathVariable long id, HttpServletRequest servletRequest) {
        service.deleteFile(id, operator(servletRequest));
        return Result.ok(null);
    }

    @GetMapping("/files/recycle")
    public Result<List<RecycledFileView>> recycledFiles(@RequestParam long projectId, HttpServletRequest servletRequest) {
        return Result.ok(service.recycledFiles(projectId, operator(servletRequest)));
    }

    @PostMapping("/files/{id}/restore")
    public Result<DevFileView> restoreFile(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.restoreFile(id, operator(servletRequest)), "文件已恢复");
    }

    @DeleteMapping("/files/{id}/permanent")
    public Result<Void> permanentlyDeleteFile(@PathVariable long id, HttpServletRequest servletRequest) {
        service.permanentlyDeleteFile(id, operator(servletRequest));
        return Result.ok(null);
    }

    @GetMapping("/files/{id}/versions")
    public Result<List<FileVersionView>> versions(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.versions(id, operator(servletRequest)));
    }

    @PostMapping("/files/{id}/versions")
    public Result<FileVersionView> createVersion(@PathVariable long id,
                                                  @Valid @RequestBody DevelopmentRequests.VersionRequest request,
                                                  HttpServletRequest servletRequest) {
        return Result.ok(service.createVersion(id, request, operator(servletRequest)), "文件版本已创建");
    }

    @PostMapping("/files/{id}/publish")
    public Result<DevFileView> publishFile(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.publishFile(id, operator(servletRequest)), "文件已发布上线");
    }

    @GetMapping("/files/{id}/schedule")
    public Result<DevelopmentScheduleService.ScheduleView> schedule(@PathVariable long id, HttpServletRequest servletRequest) {
        service.getFile(id, operator(servletRequest));
        return Result.ok(schedules.get(id));
    }

    @PutMapping("/files/{id}/schedule")
    public Result<DevelopmentScheduleService.ScheduleView> saveSchedule(@PathVariable long id,
            @RequestBody DevelopmentScheduleService.ScheduleRequest request, HttpServletRequest servletRequest) {
        service.requireFileEdit(id, operator(servletRequest));
        return Result.ok(schedules.save(id, request, operator(servletRequest)), "调度配置已保存");
    }

    @GetMapping("/files/{id}/schedule/versions/{versionNo}")
    public Result<DevelopmentScheduleService.ScheduleView> scheduleVersion(@PathVariable long id,
            @PathVariable int versionNo, HttpServletRequest servletRequest) {
        service.getFile(id, operator(servletRequest));
        return Result.ok(schedules.version(id, versionNo));
    }

    @GetMapping("/files/{id}/schedule/runtime")
    public Result<DevelopmentScheduleService.ScheduleRuntimeView> scheduleRuntime(@PathVariable long id, HttpServletRequest servletRequest) {
        service.getFile(id, operator(servletRequest));
        return Result.ok(schedules.runtime(id));
    }

    @GetMapping("/files/{id}/bundle")
    public Result<DevelopmentScheduleService.BundleView> bundle(@PathVariable long id) {
        return Result.ok(schedules.bundle(id));
    }

    @GetMapping("/files/{id}/bundle/releases")
    public Result<List<DevelopmentScheduleService.ReleaseView>> bundleReleases(@PathVariable long id) {
        return Result.ok(schedules.releases(id));
    }

    @PostMapping("/files/{id}/bundle/releases/{releaseNo}/rollback")
    public Result<DevelopmentScheduleService.BundleView> rollbackBundle(@PathVariable long id,@PathVariable int releaseNo,HttpServletRequest servletRequest) {
        service.requireFileEdit(id, operator(servletRequest));
        return Result.ok(schedules.rollback(id, releaseNo, operator(servletRequest)), "已回滚到 P" + releaseNo);
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
