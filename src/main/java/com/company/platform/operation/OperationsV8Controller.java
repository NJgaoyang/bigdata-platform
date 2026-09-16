package com.company.platform.operation;

import com.company.platform.common.Result;
import com.company.platform.scheduler.SchedulerGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operations")
public class OperationsV8Controller {
    private final OperationsAggregationService service;
    public OperationsV8Controller(OperationsAggregationService service) { this.service = service; }

    @GetMapping("/summary") public Result<OperationsAggregationService.Summary> summary() { return Result.ok(service.summary()); }
    @GetMapping("/tasks") public Result<List<OperationsAggregationService.TaskItem>> tasks() { return Result.ok(service.tasks()); }
    @PostMapping("/tasks/{type}/{id}/start") public Result<OperationsAggregationService.TaskActionResult> start(@PathVariable String type,@PathVariable long id,HttpServletRequest req) { return Result.ok(service.startTask(type,id,operator(req)),"启动请求已提交"); }
    @PostMapping("/tasks/{type}/{id}/rerun") public Result<OperationsAggregationService.TaskActionResult> rerunTask(@PathVariable String type,@PathVariable long id,HttpServletRequest req) { return Result.ok(service.rerunTask(type,id,operator(req)),"重跑请求已提交"); }
    @PostMapping("/tasks/{type}/{id}/kill") public Result<Void> killTask(@PathVariable String type,@PathVariable long id,HttpServletRequest req) { service.killTask(type,id,operator(req)); return Result.ok(null,"杀死请求已执行"); }
    @GetMapping("/tasks/{type}/{id}/log") public Result<String> taskLog(@PathVariable String type,@PathVariable long id) { return Result.ok(service.taskLog(type,id)); }
    @GetMapping("/instances") public Result<List<OperationsAggregationService.InstanceItem>> instances() { return Result.ok(service.instances()); }
    @GetMapping("/failures") public Result<List<OperationsAggregationService.FailureItem>> failures() { return Result.ok(service.failures()); }
    @GetMapping("/alerts") public Result<List<OperationsAggregationService.AlertItem>> alerts() { return Result.ok(service.alerts()); }
    @GetMapping("/instances/{type}/{id}/log") public Result<String> log(@PathVariable String type, @PathVariable String id) { return Result.ok(service.log(type, id)); }
    @PostMapping("/instances/{type}/{id}/stop") public Result<Void> stop(@PathVariable String type, @PathVariable String id) {
        service.stop(type, id); return Result.ok(null, "停止请求已提交");
    }
    @PostMapping("/workflow-instances/{instanceId}/rerun") public Result<SchedulerGateway.RunResult> rerun(@PathVariable String instanceId) {
        return Result.ok(service.rerunWorkflow(instanceId), "重跑实例已提交");
    }
    private String operator(HttpServletRequest req){Object u=req.getAttribute("authenticatedUser");return u==null||u.toString().isBlank()?"admin":u.toString();}
}
