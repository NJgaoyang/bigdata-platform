package com.company.platform.operation;

import com.company.platform.common.Result;
import com.company.platform.scheduler.SchedulerGateway;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final SchedulerGateway gateway;
    public OperationsController(SchedulerGateway gateway) { this.gateway = gateway; }
    @GetMapping("/process-instances")
    public Result<List<Map<String, Object>>> processes() {
        List<Map<String, Object>> actual = gateway.listProcessInstances();
        return Result.ok(actual);
    }
    @GetMapping("/task-instances")
    public Result<List<Map<String, Object>>> tasks() {
        List<Map<String, Object>> actual = gateway.listTaskInstances();
        return Result.ok(actual);
    }
    @GetMapping("/failed-tasks")
    public Result<List<Map<String, Object>>> failed() {
        List<Map<String, Object>> actual = gateway.listTaskInstances().stream()
                .filter(item -> String.valueOf(item.getOrDefault("status", "")).contains("FAIL"))
                .toList();
        return Result.ok(actual);
    }
    @GetMapping("/task-instances/{id}/log")
    public Result<String> log(@PathVariable String id) { return Result.ok(gateway.taskLog(id)); }
    @PostMapping("/process-instances/{id}/stop")
    public Result<Void> stop(@PathVariable String id) { gateway.stop(id); return Result.ok(null, "实例已停止"); }
    @PostMapping("/process-instances/{id}/rerun")
    public Result<SchedulerGateway.RunResult> rerun(@PathVariable String id) { return Result.ok(gateway.rerun(id)); }
}
