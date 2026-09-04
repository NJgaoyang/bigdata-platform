package com.company.platform.cluster;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/clusters")
public class SeaTunnelClusterController {
    private final SeaTunnelClusterService service;
    public SeaTunnelClusterController(SeaTunnelClusterService service) { this.service = service; }

    @GetMapping public Result<List<SeaTunnelClusterView>> list() { return Result.ok(service.list()); }
    @PostMapping public Result<SeaTunnelClusterView> create(@Valid @RequestBody SeaTunnelClusterRequests.ClusterRequest request) { return Result.ok(service.create(request), "集群已创建"); }
    @PutMapping("/{id}") public Result<SeaTunnelClusterView> update(@PathVariable long id, @Valid @RequestBody SeaTunnelClusterRequests.ClusterRequest request) { return Result.ok(service.update(id, request), "集群已更新"); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id) { service.delete(id); return Result.ok(null, "集群已删除"); }
    @PostMapping("/{id}/check") public Result<SeaTunnelClusterView> check(@PathVariable long id) { return Result.ok(service.check(id)); }
    @PostMapping("/check-all") public Result<List<SeaTunnelClusterView>> checkAll() { return Result.ok(service.checkAll()); }
}
