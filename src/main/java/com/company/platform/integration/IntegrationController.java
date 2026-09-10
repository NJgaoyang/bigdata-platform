package com.company.platform.integration;

import com.company.platform.cluster.SeaTunnelClusterService;
import com.company.platform.cluster.SeaTunnelClusterView;
import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integration/tasks")
public class IntegrationController {
    private final IntegrationService service;
    private final IntegrationMetadataService metadataService;
    private final SeaTunnelClusterService clusterService;

    public IntegrationController(IntegrationService service, IntegrationMetadataService metadataService,
                                 SeaTunnelClusterService clusterService) {
        this.service = service;
        this.metadataService = metadataService;
        this.clusterService = clusterService;
    }

    @GetMapping public Result<List<IntegrationTaskView>> list() { return Result.ok(service.list()); }
    @GetMapping("/runtime-clusters") public Result<List<SeaTunnelClusterView>> runtimeClusters() { return Result.ok(clusterService.list()); }
    @GetMapping("/source-databases") public Result<List<IntegrationMetadataService.DatabaseOption>> sourceDatabases(@RequestParam long dataSourceId) { return Result.ok(metadataService.mysqlDatabases(dataSourceId)); }
    @GetMapping("/source-tables") public Result<List<IntegrationMetadataService.TableOption>> sourceTables(@RequestParam long dataSourceId, @RequestParam(required = false) String database) { return Result.ok(metadataService.mysqlTables(dataSourceId, database)); }

    @PostMapping public Result<IntegrationTaskView> create(@Valid @RequestBody IntegrationRequests.TaskRequest request) { return Result.ok(service.create(request)); }
    @PutMapping("/{id}") public Result<IntegrationTaskView> update(@PathVariable long id, @Valid @RequestBody IntegrationRequests.TaskRequest request) { return Result.ok(service.update(id, request), "同步任务已更新"); }
    @GetMapping("/{id}") public Result<IntegrationTaskView> get(@PathVariable long id) { return Result.ok(service.get(id)); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id) { service.delete(id); return Result.ok(null, "同步任务已删除"); }
    @GetMapping("/{id}/tables") public Result<List<IntegrationTableView>> tables(@PathVariable long id) { return Result.ok(service.tables(id)); }
    @DeleteMapping("/{id}/tables/{tableId}") public Result<IntegrationTaskView> deleteTable(@PathVariable long id, @PathVariable long tableId) { return Result.ok(service.deleteTable(id, tableId), "任务表已删除，配置已重新生成"); }

    @PostMapping("/{id}/sync-schema")
    public Result<List<StarRocksSchemaService.SchemaResult>> syncSchema(@PathVariable long id,
                                                                        @RequestParam(defaultValue = "false") boolean recreate) {
        return Result.ok(service.syncSchema(id, recreate), recreate ? "目标表已按源表结构重建" : "目标表结构已同步");
    }

    @PostMapping("/{id}/validate") public Result<SeaTunnelGateway.ValidationResult> validate(@PathVariable long id) { return Result.ok(service.validate(id)); }
    @PostMapping("/{id}/execute") public Result<SeaTunnelGateway.SubmitResult> execute(@PathVariable long id) { return Result.ok(service.execute(id)); }
    @PostMapping("/{id}/run") public Result<SeaTunnelGateway.SubmitResult> run(@PathVariable long id) { return Result.ok(service.execute(id)); }
    @PostMapping("/{id}/stop") public Result<Void> stop(@PathVariable long id) { service.instances(id).stream().findFirst().ifPresent(instance -> service.stop(instance.executionId())); return Result.ok(null, "同步任务已停止"); }
    @GetMapping("/{id}/instances") public Result<List<IntegrationInstanceView>> instances(@PathVariable long id) { return Result.ok(service.instances(id)); }
    @GetMapping("/executions/{executionId}") public Result<SeaTunnelGateway.JobStatus> status(@PathVariable String executionId) { return Result.ok(service.status(executionId)); }
    @GetMapping("/executions/{executionId}/log") public Result<String> log(@PathVariable String executionId) { return Result.ok(service.log(executionId)); }
    @PostMapping("/executions/{executionId}/cancel") public Result<Void> cancel(@PathVariable String executionId) { service.cancel(executionId); return Result.ok(null); }
}
