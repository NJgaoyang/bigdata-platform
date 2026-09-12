package com.company.platform.operation;

import com.company.platform.common.Result;
import com.company.platform.scheduler.SchedulerGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private static final Logger log = LoggerFactory.getLogger(OperationsController.class);
    private final SchedulerGateway gateway;
    public OperationsController(SchedulerGateway gateway) { this.gateway = gateway; }

    @GetMapping("/process-instances")
    public Result<List<Map<String, Object>>> processes() {
        return Result.ok(safeRead("process-instances", gateway::listProcessInstances));
    }

    @GetMapping("/task-instances")
    public Result<List<Map<String, Object>>> tasks(@RequestParam(required = false) String processInstanceId) {
        return Result.ok(safeRead("task-instances", () -> gateway.listTaskInstances(processInstanceId)));
    }

    @GetMapping("/failed-tasks")
    public Result<List<Map<String, Object>>> failed() {
        List<Map<String, Object>> actual = safeRead("failed-tasks", gateway::listTaskInstances).stream()
                .filter(item -> isFailureState(item.get("status")))
                .toList();
        return Result.ok(actual);
    }

    @GetMapping("/task-instances/{id}/log")
    public Result<String> log(@PathVariable String id) { return Result.ok(gateway.taskLog(id)); }

    @PostMapping("/process-instances/{id}/stop")
    public Result<Void> stop(@PathVariable String id) { gateway.stop(id); return Result.ok(null, "实例已停止"); }

    @PostMapping("/process-instances/{id}/rerun")
    public Result<SchedulerGateway.RunResult> rerun(@PathVariable String id) { return Result.ok(gateway.rerun(id)); }

    private List<Map<String, Object>> safeRead(String operation, Supplier<List<Map<String, Object>>> supplier) {
        try {
            List<Map<String, Object>> value = supplier.get();
            return value == null ? List.of() : value;
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            log.warn("DolphinScheduler {} 查询不可用，返回空结果而不是模拟数据: {}", operation,
                    message == null || message.isBlank() ? ex.getClass().getSimpleName() : message);
            return List.of();
        }
    }

    private boolean isFailureState(Object value) {
        String status = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        return status.contains("FAIL") || status.contains("ERROR") || status.contains("KILL") || status.contains("STOP");
    }
}
