package com.company.platform.scheduler;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/{workflowId}/schedule")
public class WorkflowScheduleController {
    private final SchedulerService service;
    public WorkflowScheduleController(SchedulerService service) { this.service = service; }
    @GetMapping public Result<ScheduleConfigView> get(@PathVariable long workflowId) { return Result.ok(service.get(workflowId)); }
    @PutMapping public Result<ScheduleConfigView> save(@PathVariable long workflowId, @Valid @RequestBody ScheduleRequests.ScheduleRequest request) { return Result.ok(service.save(workflowId, request), "调度配置已保存"); }
    @PostMapping("/online") public Result<ScheduleConfigView> online(@PathVariable long workflowId) { return Result.ok(service.online(workflowId), "工作流已上线"); }
    @PostMapping("/offline") public Result<ScheduleConfigView> offline(@PathVariable long workflowId) { return Result.ok(service.offline(workflowId), "工作流已下线"); }
}
