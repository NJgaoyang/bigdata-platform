package com.company.platform.scheduler;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerController {
    private final SchedulerGateway gateway;
    private final SchedulerService service;
    public SchedulerController(SchedulerGateway gateway, SchedulerService service) { this.gateway = gateway; this.service = service; }
    @GetMapping("/workflows/{workflowId}/schedule")
    public Result<ScheduleConfigView> schedule(@PathVariable long workflowId) { return Result.ok(service.get(workflowId)); }
    @PutMapping("/workflows/{workflowId}/schedule")
    public Result<ScheduleConfigView> saveSchedule(@PathVariable long workflowId, @Valid @RequestBody ScheduleRequests.ScheduleRequest request) {
        return Result.ok(service.save(workflowId, request), "调度配置已保存");
    }
    @PostMapping("/workflows/{workflowId}/online")
    public Result<ScheduleConfigView> online(@PathVariable long workflowId) { return Result.ok(service.online(workflowId), "工作流已上线"); }
    @PostMapping("/workflows/{workflowId}/offline")
    public Result<ScheduleConfigView> offline(@PathVariable long workflowId) { return Result.ok(service.offline(workflowId), "工作流已下线"); }
    @PostMapping("/workflows/{workflowId}/backfill")
    public Result<SchedulerGateway.RunResult> backfillWorkflow(@PathVariable long workflowId, @Valid @RequestBody ScheduleRequests.BackfillRequest request) {
        return Result.ok(service.backfill(workflowId, request), "补数据任务已提交");
    }
    @GetMapping("/instances/{instanceId}")
    public Result<SchedulerGateway.InstanceStatus> status(@PathVariable String instanceId) { return Result.ok(gateway.status(instanceId)); }
    @PostMapping("/instances/{instanceId}/stop")
    public Result<Void> stop(@PathVariable String instanceId) { gateway.stop(instanceId); return Result.ok(null, "实例已停止"); }
    @PostMapping("/instances/{instanceId}/rerun")
    public Result<SchedulerGateway.RunResult> rerun(@PathVariable String instanceId) { return Result.ok(gateway.rerun(instanceId)); }
    @PostMapping("/backfill")
    public Result<SchedulerGateway.RunResult> backfill(@RequestBody BackfillRequest request) {
        return Result.ok(gateway.backfill(request.processCode(), request.start(), request.end(), request.parallelism()));
    }
    public record BackfillRequest(String processCode, String start, String end, int parallelism) { }
}
