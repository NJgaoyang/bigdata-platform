package com.company.platform.operation;

import com.company.platform.common.Result;
import com.company.platform.scheduler.SchedulerGateway;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operations")
public class OperationsV8Controller {
    private final OperationsAggregationService service;
    public OperationsV8Controller(OperationsAggregationService service) { this.service = service; }

    @GetMapping("/summary") public Result<OperationsAggregationService.Summary> summary() { return Result.ok(service.summary()); }
    @GetMapping("/instances") public Result<List<OperationsAggregationService.InstanceItem>> instances() { return Result.ok(service.instances()); }
    @GetMapping("/failures") public Result<List<OperationsAggregationService.FailureItem>> failures() { return Result.ok(service.failures()); }
    @GetMapping("/alerts") public Result<List<OperationsAggregationService.AlertItem>> alerts() { return Result.ok(service.alerts()); }
    @PostMapping("/instances/{type}/{id}/stop") public Result<Void> stop(@PathVariable String type, @PathVariable String id) {
        service.stop(type, id); return Result.ok(null, "停止请求已提交");
    }
    @PostMapping("/workflow-instances/{instanceId}/rerun") public Result<SchedulerGateway.RunResult> rerun(@PathVariable String instanceId) {
        return Result.ok(service.rerunWorkflow(instanceId), "重跑实例已提交");
    }
}
