package com.company.platform.cluster;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.common.Result;
import com.company.platform.system.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class ClusterMetricsController {
    private final PlatformStore store;
    private final AuditService audit;

    public ClusterMetricsController(PlatformStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    @GetMapping("/cluster-metrics")
    public Result<Map<String, Object>> metrics(@RequestParam String type, @RequestParam long id) {
        String name;
        String host;
        if ("dolphin".equalsIgnoreCase(type)) {
            var cluster = store.dolphinSchedulerClusters.get(id);
            if (cluster == null) throw new NotFoundException("DolphinScheduler 集群不存在");
            name = cluster.name(); host = cluster.host();
        } else {
            var cluster = store.seaTunnelClusters.get(id);
            if (cluster == null) throw new NotFoundException("SeaTunnel 集群不存在");
            name = cluster.name(); host = cluster.host();
        }
        var bean = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        double cpu = bean == null ? -1 : bean.getSystemCpuLoad();
        long total = bean == null ? -1 : bean.getTotalMemorySize();
        long free = bean == null ? -1 : bean.getFreeMemorySize();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("clusterName", name);
        value.put("host", host);
        value.put("scope", "platform-node");
        value.put("available", cpu >= 0 && total > 0 && free >= 0);
        value.put("cpuUsage", cpu < 0 ? null : Math.round(cpu * 1000.0) / 10.0);
        value.put("memoryTotalBytes", total < 0 ? null : total);
        value.put("memoryUsedBytes", total < 0 ? null : total - free);
        value.put("memoryUsage", total <= 0 ? null : Math.round((total - free) * 1000.0 / total) / 10.0);
        value.put("collectedAt", Instant.now().toString());
        audit.record("VIEW_CLUSTER_METRICS", "CLUSTER", id, name, "admin");
        return Result.ok(value);
    }
}
