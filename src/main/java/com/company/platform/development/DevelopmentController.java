package com.company.platform.development;

import com.company.platform.common.ForbiddenException;
import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping({"/api/development", "/api"})
public class DevelopmentController {
    private static final String FAVORITES_PROJECT_NAME = "我的收藏";
    private final DevelopmentService service;
    private final DevelopmentVersionFlowService versionFlow;

    public DevelopmentController(DevelopmentService service, DevelopmentVersionFlowService versionFlow) {
        this.service = service;
        this.versionFlow = versionFlow;
    }

    @GetMapping("/projects")
    public Result<List<DevProjectView>> projects(@RequestParam(defaultValue = "mine") String scope,
                                                  HttpServletRequest servletRequest) {
        String normalizedScope = scope == null ? "mine" : scope.trim().toLowerCase(Locale.ROOT);
        String operator = operator(servletRequest);
        List<DevProjectView> projects;
        if ("favorites".equals(normalizedScope)) {
            // Favorites is a real, user-owned workspace stored in the same persistent
            // dev_project/dev_folder/dev_file tables. Keep it out of both "mine" and
            // shared project-space listings so it never leaks into another segment.
            projects = service.projects(operator, false).stream().filter(this::isFavoritesWorkspace).toList();
        } else {
            projects = service.projects(operator, "all".equals(normalizedScope)).stream()
                    .filter(project -> !isFavoritesWorkspace(project))
                    .toList();
        }
        return Result.ok(projects);
    }

    @PostMapping("/projects")
    public Result<DevProjectView> createProject(@Valid @RequestBody DevelopmentRequests.ProjectRequest request,
                                                 HttpServletRequest servletRequest) {
        DevelopmentRequests.ProjectRequest normalized = normalizeProjectRequest(request);
        return Result.ok(service.createProject(normalized, operator(servletRequest)));
    }

    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@PathVariable long id, HttpServletRequest servletRequest) {
        service.deleteProject(id, operator(servletRequest));
        return Result.ok(null);
    }

    @PutMapping("/projects/{id}")
    public Result<DevProjectView> updateProject(@PathVariable long id,
                                                 @Valid @RequestBody DevelopmentRequests.ProjectRequest request,
                                                 HttpServletRequest servletRequest) {
        return Result.ok(service.updateProject(id, normalizeProjectRequest(request), operator(servletRequest)));
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
        if (versionFlow.isManagedProjectFile(id)) {
            throw new ForbiddenException("项目空间中的受控版本为只读，请在“我的开发”中修改后重新推送");
        }
        return Result.ok(service.saveFile(id, request, operator(servletRequest)), "文件已保存");
    }

    @DeleteMapping("/files/{id}")
    public Result<Void> deleteFile(@PathVariable long id, HttpServletRequest servletRequest) {
        service.deleteFile(id, operator(servletRequest));
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
        if (versionFlow.isManagedProjectFile(id)) {
            throw new ForbiddenException("项目空间中的受控版本为只读，请先创建开发版本");
        }
        return Result.ok(service.createVersion(id, request, operator(servletRequest)), "文件版本已创建");
    }

    @PostMapping("/files/{id}/publish")
    public Result<DevFileView> publishFile(@PathVariable long id, HttpServletRequest servletRequest) {
        if (versionFlow.isManagedProjectFile(id)) {
            return Result.ok(versionFlow.publish(id, operator(servletRequest)), "项目版本已发布上线");
        }
        return Result.ok(service.publishFile(id, operator(servletRequest)), "文件已发布上线");
    }

    private DevelopmentRequests.ProjectRequest normalizeProjectRequest(DevelopmentRequests.ProjectRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        String description = request.description() == null ? "" : request.description().trim();
        // Older frontend builds used “数仓” as an implementation-only container
        // for the shared project space. It is not a real user folder and must not
        // be exposed as one. Normalize new writes while keeping old data intact.
        if ("数仓".equals(name) && "团队公共开发空间".equals(description)) {
            name = "项目空间";
        }
        return new DevelopmentRequests.ProjectRequest(name, description);
    }

    private boolean isFavoritesWorkspace(DevProjectView project) {
        return project != null && FAVORITES_PROJECT_NAME.equalsIgnoreCase(project.name() == null ? "" : project.name().trim());
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
