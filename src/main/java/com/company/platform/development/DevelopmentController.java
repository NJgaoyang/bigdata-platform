package com.company.platform.development;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/development", "/api"})
public class DevelopmentController {
    private final DevelopmentService service;
    public DevelopmentController(DevelopmentService service) { this.service = service; }

    @GetMapping("/projects")
    public Result<List<DevProjectView>> projects(@RequestParam(defaultValue = "mine") String scope, HttpServletRequest servletRequest) {
        return Result.ok(service.projects(operator(servletRequest), "all".equalsIgnoreCase(scope)));
    }
    @PostMapping("/projects")
    public Result<DevProjectView> createProject(@Valid @RequestBody DevelopmentRequests.ProjectRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.createProject(request, operator(servletRequest)));
    }
    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable long id, HttpServletRequest servletRequest) { service.deleteProject(id, operator(servletRequest)); return Result.ok(null); }
    @PutMapping("/projects/{id}")
    public Result<DevProjectView> updateProject(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.ProjectRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.updateProject(id, request, operator(servletRequest)));
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
    public Result<DevFolderView> createFolder(@Valid @RequestBody DevelopmentRequests.FolderRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.createFolder(request, operator(servletRequest)));
    }
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable long id, HttpServletRequest servletRequest) { service.deleteFolder(id, operator(servletRequest)); return Result.ok(null); }
    @PutMapping("/folders/{id}")
    public Result<DevFolderView> updateFolder(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.FolderUpdateRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.updateFolder(id, request, operator(servletRequest)));
    }
    @GetMapping("/files")
    public Result<List<DevFileView>> files(@RequestParam long projectId, HttpServletRequest servletRequest) {
        return Result.ok(service.files(projectId, operator(servletRequest)));
    }
    @PostMapping("/files")
    public Result<DevFileView> createFile(@Valid @RequestBody DevelopmentRequests.FileRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.createFile(request, operator(servletRequest)));
    }
    @GetMapping("/files/{id}")
    public Result<DevFileView> getFile(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.getFile(id, operator(servletRequest)));
    }
    @PutMapping("/files/{id}")
    public Result<DevFileView> saveFile(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.SaveFileRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.saveFile(id, request, operator(servletRequest)), "文件已保存");
    }
    @DeleteMapping("/files/{id}")
    public Result<Void> deleteFile(@PathVariable long id, HttpServletRequest servletRequest) { service.deleteFile(id, operator(servletRequest)); return Result.ok(null); }
    @GetMapping("/files/{id}/versions")
    public Result<List<FileVersionView>> versions(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.versions(id, operator(servletRequest)));
    }
    @PostMapping("/files/{id}/versions")
    public Result<FileVersionView> createVersion(@PathVariable long id, @Valid @RequestBody DevelopmentRequests.VersionRequest request, HttpServletRequest servletRequest) {
        return Result.ok(service.createVersion(id, request, operator(servletRequest)), "文件版本已创建");
    }
    @PostMapping("/files/{id}/publish")
    public Result<DevFileView> publishFile(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.publishFile(id, operator(servletRequest)), "文件已发布上线");
    }
    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
