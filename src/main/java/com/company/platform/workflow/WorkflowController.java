package com.company.platform.workflow;

import com.company.platform.common.Result;
import com.company.platform.scheduler.ScheduleRequests;
import com.company.platform.scheduler.SchedulerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService service;
    private final WorkflowPublishService publishService;
    private final SchedulerService schedulerService;
    public WorkflowController(WorkflowService service, WorkflowPublishService publishService, SchedulerService schedulerService) {
        this.service = service; this.publishService = publishService; this.schedulerService = schedulerService;
    }
    @GetMapping public Result<List<WorkflowView>> list() { return Result.ok(service.list()); }
    @PostMapping public Result<WorkflowView> create(@Valid @RequestBody WorkflowRequests.WorkflowRequest request) { return Result.ok(service.create(request)); }
    @PutMapping("/{id}") public Result<WorkflowView> update(@PathVariable long id, @Valid @RequestBody WorkflowRequests.WorkflowRequest request) { return Result.ok(service.update(id, request), "工作流已保存"); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id) { service.delete(id); return Result.ok(null, "工作流已删除"); }
    @PutMapping("/{id}/graph") public Result<WorkflowView> updateGraph(@PathVariable long id, @Valid @RequestBody WorkflowRequests.WorkflowRequest request) { return Result.ok(service.update(id, request), "工作流画布已保存"); }
    @GetMapping("/{id}") public Result<WorkflowView> get(@PathVariable long id) { return Result.ok(service.get(id)); }
    @PostMapping("/{id}/validate") public Result<DagValidator.ValidationResult> validate(@PathVariable long id) { return Result.ok(service.validate(id)); }
    @PostMapping("/{id}/publish") public Result<WorkflowPublishService.PublishResult> publish(@PathVariable long id) { return Result.ok(publishService.publish(id)); }
    @PostMapping("/{id}/run") public Result<WorkflowPublishService.RunResult> run(@PathVariable long id) { return Result.ok(publishService.run(id)); }
    @PostMapping("/{id}/backfill") public Result<com.company.platform.scheduler.SchedulerGateway.RunResult> backfill(@PathVariable long id, @Valid @RequestBody ScheduleRequests.BackfillRequest request) { return Result.ok(schedulerService.backfill(id, request), "补数据任务已提交"); }
}
