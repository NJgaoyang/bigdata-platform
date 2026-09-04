package com.company.platform.health;

import com.company.platform.common.Result;
import com.company.platform.config.PlatformProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final PlatformProperties properties;
    public HealthController(PlatformProperties properties) { this.properties = properties; }

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        var scheduler = properties.getScheduler().getDolphinscheduler();
        String mode = scheduler.isRealEnabled() || properties.getSeatunnel().isRealEnabled()
                ? "real" : "development-mock";
        return Result.ok(Map.of("status", "UP", "mode", mode,
                "realtimeSync", properties.getFeatures().isRealtimeSync(),
                "dolphinScheduler", scheduler.getVersion(),
                "seaTunnel", properties.getSeatunnel().getVersion()));
    }
}
