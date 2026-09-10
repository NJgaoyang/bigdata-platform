package com.company.platform.cluster;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.common.Result;
import com.company.platform.system.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public Result<Map<String, Object>> metrics(@RequestParam String type, @RequestParam long id,
                                                HttpServletRequest servletRequest) {
        String name;
        String host;
        if ("dolphin".equalsIgnoreCase(type)) {
            var cluster = store.dolphinSchedulerClusters.get(id);
            if (cluster == null) throw new NotFoundException("DolphinScheduler 集群不存在");
            name = cluster.name();
            host = cluster.host();
        } else if ("seatunnel".equalsIgnoreCase(type)) {
            var cluster = store.seaTunnelClusters.get(id);
            if (cluster == null) throw new NotFoundException("SeaTunnel 集群不存在");
            name = cluster.name();
            host = cluster.host();
        } else {
            throw new NotFoundException("不支持的集群类型");
        }

        // The platform currently has no remote host/Prometheus collector for cluster CPU and memory.
        // Returning the Spring Boot host's OperatingSystemMXBean values here would misrepresent the
        // application server as the selected remote cluster. Until a real collector is configured,
        // explicitly report metrics as unavailable instead of fabricating or proxying local values.
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("clusterName", name);
        value.put("host", host);
        value.put("scope", "remote-cluster");
        value.put("available", false);
        value.put("message", "未配置远程集群指标采集，暂不展示 CPU/内存数据");
        value.put("cpuUsage", null);
        value.put("memoryTotalBytes", null);
        value.put("memoryUsedBytes", null);
        value.put("memoryUsage", null);
        value.put("collectedAt", Instant.now().toString());
        audit.record("VIEW_CLUSTER_METRICS", "CLUSTER", id, name, operator(servletRequest));
        return Result.ok(value);
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
