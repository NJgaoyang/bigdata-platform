package com.company.platform.development;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/development")
public class DevelopmentAccessController {
    private final DevelopmentAccessService service;

    public DevelopmentAccessController(DevelopmentAccessService service) {
        this.service = service;
    }

    @GetMapping("/access")
    public Result<DevelopmentAccessService.ModuleAccess> moduleAccess(HttpServletRequest request) {
        return Result.ok(service.moduleAccess(operator(request)));
    }

    @GetMapping("/projects/{id}/access")
    public Result<DevelopmentAccessService.ProjectAccess> projectAccess(@PathVariable long id, HttpServletRequest request) {
        return Result.ok(service.projectAccess(id, operator(request)));
    }

    @PostMapping("/files/save-to-project")
    public Result<DevFileView> saveToProject(@Valid @RequestBody DevelopmentAccessRequests.SaveToProjectRequest request,
                                             HttpServletRequest servletRequest) {
        return Result.ok(service.saveToProject(request, operator(servletRequest)), "已保存到项目空间");
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
