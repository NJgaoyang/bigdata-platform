package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/alert-settings")
public class AlertSettingController {
    private final AlertSettingService service;
    public AlertSettingController(AlertSettingService service) { this.service = service; }

    @GetMapping public Result<List<AlertSettingView>> list() { return Result.ok(service.list()); }
    @PostMapping public Result<AlertSettingView> create(@Valid @RequestBody AlertSettingRequest request) { return Result.ok(service.create(request), "告警配置已创建"); }
    @PutMapping("/{id}") public Result<AlertSettingView> update(@PathVariable long id, @Valid @RequestBody AlertSettingRequest request) { return Result.ok(service.update(id, request), "告警配置已更新"); }
    @PostMapping("/{id}/enabled") public Result<AlertSettingView> enabled(@PathVariable long id, @RequestBody Map<String, Boolean> body) { return Result.ok(service.setEnabled(id, Boolean.TRUE.equals(body.get("enabled"))), "告警状态已更新"); }
    @PostMapping("/{id}/test") public Result<String> test(@PathVariable long id) { return Result.ok(service.test(id)); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id) { service.delete(id); return Result.ok(null, "告警配置已删除"); }
}
