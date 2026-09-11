package com.company.platform.health;

import com.company.platform.cluster.SeaTunnelSshClient;
import com.company.platform.common.PlatformStore;
import com.company.platform.common.Result;
import com.company.platform.config.PlatformProperties;
import com.company.platform.scheduler.SchedulerGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final PlatformProperties properties;
    private final JdbcTemplate jdbc;
    private final SchedulerGateway schedulerGateway;
    private final PlatformStore store;
    private final SeaTunnelSshClient seaTunnelSshClient;

    public HealthController(PlatformProperties properties, JdbcTemplate jdbc, SchedulerGateway schedulerGateway,
                            PlatformStore store, SeaTunnelSshClient seaTunnelSshClient) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.schedulerGateway = schedulerGateway;
        this.store = store;
        this.seaTunnelSshClient = seaTunnelSshClient;
    }

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        var scheduler = properties.getScheduler().getDolphinscheduler();
        Map<String, Object> components = new LinkedHashMap<>();
        boolean databaseUp = checkDatabase();
        boolean schedulerUp = !scheduler.isRealEnabled() || checkScheduler();
        boolean seaTunnelUp = !properties.getSeatunnel().isRealEnabled() || checkSeaTunnel();
        components.put("database", databaseUp ? "UP" : "DOWN");
        components.put("dolphinScheduler", scheduler.isRealEnabled() ? (schedulerUp ? "UP" : "DOWN") : "DISABLED");
        components.put("seaTunnel", properties.getSeatunnel().isRealEnabled() ? (seaTunnelUp ? "UP" : "DOWN") : "DISABLED");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", databaseUp && schedulerUp && seaTunnelUp ? "UP" : "DEGRADED");
        response.put("mode", scheduler.isRealEnabled() || properties.getSeatunnel().isRealEnabled() ? "real" : "unavailable");
        response.put("realtimeSync", properties.getFeatures().isRealtimeSync());
        response.put("components", components);
        response.put("dolphinSchedulerVersion", scheduler.getVersion());
        response.put("seaTunnelVersion", properties.getSeatunnel().getVersion());
        return Result.ok(response);
    }

    private boolean checkDatabase() {
        try { return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class)); }
        catch (RuntimeException ignored) { return false; }
    }

    private boolean checkScheduler() {
        try { schedulerGateway.listProcessInstances(); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    private boolean checkSeaTunnel() {
        Path executable = Path.of(properties.getSeatunnel().getHome(), "bin", "seatunnel.sh");
        if (Files.isRegularFile(executable) && Files.isExecutable(executable)) return true;
        return store.seaTunnelClusters.values().stream().anyMatch(cluster -> {
            try { return seaTunnelSshClient.executableAvailable(cluster.id()); }
            catch (RuntimeException ignored) { return false; }
        });
    }
}
