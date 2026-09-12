package com.company.platform.cluster;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/dolphinscheduler-clusters")
public class DolphinSchedulerClusterController {
    private final DolphinSchedulerClusterService service;
    public DolphinSchedulerClusterController(DolphinSchedulerClusterService service) { this.service = service; }

    @GetMapping public Result<List<DolphinSchedulerClusterView>> list() { return Result.ok(service.list()); }
    @PostMapping public Result<DolphinSchedulerClusterView> create(@Valid @RequestBody DolphinSchedulerClusterRequests.ClusterRequest request,
                                                                    HttpServletRequest servletRequest) {
        return Result.ok(service.create(request, operator(servletRequest)), "DolphinScheduler 集群已创建");
    }
    @PutMapping("/{id}") public Result<DolphinSchedulerClusterView> update(@PathVariable long id,
                                                                            @Valid @RequestBody DolphinSchedulerClusterRequests.ClusterRequest request,
                                                                            HttpServletRequest servletRequest) {
        return Result.ok(service.update(id, request, operator(servletRequest)), "DolphinScheduler 集群已更新");
    }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id, HttpServletRequest servletRequest) {
        service.delete(id, operator(servletRequest)); return Result.ok(null, "DolphinScheduler 集群已删除");
    }
    @PostMapping("/{id}/check") public Result<DolphinSchedulerClusterView> check(@PathVariable long id, HttpServletRequest servletRequest) {
        return Result.ok(service.check(id, operator(servletRequest)));
    }
    @PostMapping("/check-all") public Result<List<DolphinSchedulerClusterView>> checkAll(HttpServletRequest servletRequest) {
        return Result.ok(service.checkAll(operator(servletRequest)));
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
