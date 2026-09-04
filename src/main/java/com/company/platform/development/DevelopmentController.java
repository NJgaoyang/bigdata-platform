package com.company.platform.development;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/development", "/api"})
public class DevelopmentController {
    private final DevelopmentService service;
    public DevelopmentController(DevelopmentService service) { this.service = service; }

    @GetMapping("/projects")
    public Result<List<DevProjectView>> projects() { return Result.ok(service.projects()); }
    @PostMapping("/projects")
    public Result<DevProjectView> createProject(@Valid @RequestBody DevelopmentRequests.ProjectRequest request) {
        return Result.ok(service.createProject(request));
    }
    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable long id) { service.deleteProject(id); return Result.ok(null); }
    @PutMapping("/projects/{id}")
    public Result<DevProjectView> updateProject(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.ProjectRequest request) { return Result.ok(service.updateProject(id, request)); }
    @GetMapping("/projects/{id}/tree")
    public Result<java.util.Map<String, Object>> tree(@PathVariable long id) { return Result.ok(service.tree(id)); }
    @GetMapping("/folders")
    public Result<List<DevFolderView>> folders(@RequestParam long projectId) { return Result.ok(service.folders(projectId)); }
    @PostMapping("/folders")
    public Result<DevFolderView> createFolder(@Valid @RequestBody DevelopmentRequests.FolderRequest request) {
        return Result.ok(service.createFolder(request));
    }
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable long id) { service.deleteFolder(id); return Result.ok(null); }
    @PutMapping("/folders/{id}")
    public Result<DevFolderView> updateFolder(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.FolderUpdateRequest request) { return Result.ok(service.updateFolder(id, request)); }
    @GetMapping("/files")
    public Result<List<DevFileView>> files(@RequestParam long projectId) { return Result.ok(service.files(projectId)); }
    @PostMapping("/files")
    public Result<DevFileView> createFile(@Valid @RequestBody DevelopmentRequests.FileRequest request) {
        return Result.ok(service.createFile(request));
    }
    @GetMapping("/files/{id}")
    public Result<DevFileView> getFile(@PathVariable long id) { return Result.ok(service.getFile(id)); }
    @PutMapping("/files/{id}")
    public Result<DevFileView> saveFile(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.SaveFileRequest request) {
        return Result.ok(service.saveFile(id, request), "文件已保存");
    }
    @DeleteMapping("/files/{id}")
    public Result<Void> deleteFile(@PathVariable long id) { service.deleteFile(id); return Result.ok(null); }
    @GetMapping("/files/{id}/versions")
    public Result<List<FileVersionView>> versions(@PathVariable long id) { return Result.ok(service.versions(id)); }
    @PostMapping("/files/{id}/versions")
    public Result<FileVersionView> createVersion(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.VersionRequest request) {
        return Result.ok(service.createVersion(id, request), "文件版本已创建");
    }
}
