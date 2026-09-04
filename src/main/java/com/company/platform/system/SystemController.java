package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import com.company.platform.common.Result;
import com.company.platform.config.PlatformProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final PlatformProperties properties;
    private final PlatformStore store;
    public SystemController(PlatformProperties properties, PlatformStore store) { this.properties = properties; this.store = store; }
    @GetMapping("/features") public Result<Map<String, Boolean>> features() {
        return Result.ok(Map.of("realtime-sync", properties.getFeatures().isRealtimeSync()));
    }
    @GetMapping("/operation-logs") public Result<List<String>> operationLogs() { return Result.ok(store.operationLogs.values().stream().toList()); }
}
