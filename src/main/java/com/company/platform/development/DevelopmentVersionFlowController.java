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
public class DevelopmentVersionFlowController {
    private final DevelopmentVersionFlowService service;

    public DevelopmentVersionFlowController(DevelopmentVersionFlowService service) {
        this.service = service;
    }

    @PostMapping("/files/push-to-project")
    public Result<DevFileView> pushToProject(@Valid @RequestBody DevelopmentVersionFlowRequests.PushToProjectRequest request,
                                             HttpServletRequest servletRequest) {
        return Result.ok(service.pushToProject(request, operator(servletRequest)), "版本已推送到项目，线上版本未受影响");
    }

    @PostMapping("/files/{id}/publish-pushed")
    public Result<DevFileView> publish(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.publish(id, operator(servletRequest)), "项目版本已发布上线");
    }

    @PostMapping("/files/{id}/unpublish")
    public Result<DevFileView> unpublish(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.unpublish(id, operator(servletRequest)), "线上版本已下线");
    }

    @PostMapping("/files/{id}/create-development-version")
    public Result<DevFileView> createDevelopmentVersion(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.createDevelopmentVersion(id, operator(servletRequest)), "已在我的开发中准备开发版本");
    }

    @GetMapping("/files/{id}/version-flow")
    public Result<DevelopmentVersionFlowService.DevelopmentVersionState> state(@PathVariable long id,
                                                                                HttpServletRequest servletRequest) {
        return Result.ok(service.state(id, operator(servletRequest)));
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
