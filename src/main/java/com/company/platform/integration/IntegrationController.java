package com.company.platform.integration;

import com.company.platform.cluster.SeaTunnelClusterService;
import com.company.platform.cluster.SeaTunnelClusterView;
import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integration/tasks")
public class IntegrationController {
    private final IntegrationService service;
    private final IntegrationMetadataService metadataService;
    private final SeaTunnelClusterService clusterService;
    private final IntegrationTaskSummaryService summaryService;
    private final IntegrationTaskScheduleService taskScheduleService;

    public IntegrationController(IntegrationService service, IntegrationMetadataService metadataService,
                                 SeaTunnelClusterService clusterService, IntegrationTaskSummaryService summaryService,
                                 IntegrationTaskScheduleService taskScheduleService) {
        this.service = service;
        this.metadataService = metadataService;
        this.clusterService = clusterService;
        this.summaryService = summaryService;
        this.taskScheduleService = taskScheduleService;
    }

    @GetMapping public Result<List<IntegrationTaskView>> list() { return Result.ok(service.list()); }
    @PostMapping("/schedule/preview") public Result<List<java.time.LocalDateTime>> previewSchedule(@RequestBody IntegrationTaskScheduleService.ScheduleRequest request) { return Result.ok(taskScheduleService.preview(request)); }
    @GetMapping("/runtime-clusters") public Result<List<SeaTunnelClusterView>> runtimeClusters() { return Result.ok(clusterService.list()); }
    @GetMapping("/source-databases") public Result<List<IntegrationMetadataService.DatabaseOption>> sourceDatabases(@RequestParam long dataSourceId) { return Result.ok(metadataService.mysqlDatabases(dataSourceId)); }
    @GetMapping("/source-tables") public Result<List<IntegrationMetadataService.TableOption>> sourceTables(@RequestParam long dataSourceId, @RequestParam(required = false) String database) { return Result.ok(metadataService.mysqlTables(dataSourceId, database)); }
    @GetMapping("/target-databases") public Result<List<IntegrationMetadataService.DatabaseOption>> targetDatabases(@RequestParam long dataSourceId) { return Result.ok(metadataService.starRocksDatabases(dataSourceId)); }

    @PostMapping public Result<IntegrationTaskView> create(@Valid @RequestBody IntegrationRequests.TaskRequest request, HttpServletRequest httpRequest) {
        IntegrationTaskView created = service.create(request);
        Object operator = httpRequest.getAttribute("platform.operator");
        summaryService.recordCreator(created.id(), operator == null ? "platform" : String.valueOf(operator));
        return Result.ok(created);
    }
    @PutMapping("/{id}") public Result<IntegrationTaskView> update(@PathVariable long id, @Valid @RequestBody IntegrationRequests.TaskRequest request) { return Result.ok(service.update(id, request), "同步任务已更新"); }
    @GetMapping("/{id}") public Result<IntegrationTaskView> get(@PathVariable long id) { return Result.ok(service.get(id)); }
    @GetMapping("/{id}/summary") public Result<IntegrationTaskSummaryService.Summary> summary(@PathVariable long id) { service.get(id); return Result.ok(summaryService.summary(id)); }
    @GetMapping("/{id}/schedule") public Result<IntegrationTaskScheduleService.ScheduleView> schedule(@PathVariable long id) { service.get(id); return Result.ok(taskScheduleService.get(id)); }
    @PutMapping("/{id}/schedule") public Result<IntegrationTaskScheduleService.ScheduleView> saveSchedule(@PathVariable long id, @RequestBody IntegrationTaskScheduleService.ScheduleRequest request) { service.get(id); return Result.ok(taskScheduleService.save(id, request), "调度配置已保存"); }
    @PostMapping("/{id}/online") public Result<IntegrationTaskView> online(@PathVariable long id) { IntegrationTaskView view = service.online(id); taskScheduleService.activate(id); return Result.ok(view, "离线同步任务已上线，等待手动触发或调度时间"); }
    @PostMapping("/{id}/offline") public Result<IntegrationTaskView> offline(@PathVariable long id) { IntegrationTaskView view = service.offline(id); taskScheduleService.pause(id); return Result.ok(view, "离线同步任务已下线"); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id) { taskScheduleService.delete(id); service.delete(id); return Result.ok(null, "同步任务已删除"); }
    @GetMapping("/{id}/tables") public Result<List<IntegrationTableView>> tables(@PathVariable long id) { return Result.ok(service.tables(id)); }
    @DeleteMapping("/{id}/tables/{tableId}") public Result<IntegrationTaskView> deleteTable(@PathVariable long id, @PathVariable long tableId) { return Result.ok(service.deleteTable(id, tableId), "任务表已删除，配置已重新生成"); }

    @PostMapping("/{id}/sync-schema")
    public Result<List<StarRocksSchemaService.SchemaResult>> syncSchema(@PathVariable long id,
                                                                        @RequestParam(defaultValue = "false") boolean recreate) {
        return Result.ok(service.syncSchema(id, recreate), recreate ? "目标表已按源表结构重建" : "目标表结构已同步");
    }

    @PostMapping("/{id}/validate") public Result<SeaTunnelGateway.ValidationResult> validate(@PathVariable long id) { return Result.ok(service.validate(id)); }
    @PostMapping("/{id}/precheck") public Result<IntegrationPreCheckService.Report> precheck(@PathVariable long id) { return Result.ok(service.precheck(id)); }
    @PostMapping("/preview-config") public Result<String> previewConfig(@Valid @RequestBody IntegrationRequests.TaskRequest request) { return Result.ok(service.previewConfig(request)); }
    @PostMapping("/{id}/execute") public Result<SeaTunnelGateway.SubmitResult> execute(@PathVariable long id) { return Result.ok(service.execute(id)); }
    @PostMapping("/{id}/run") public Result<SeaTunnelGateway.SubmitResult> run(@PathVariable long id) { return Result.ok(service.execute(id)); }
    @PostMapping("/{id}/run-confirmed") public Result<SeaTunnelGateway.SubmitResult> runConfirmed(@PathVariable long id) { return Result.ok(service.executeConfirmed(id), "已确认重新运行"); }
    @PostMapping("/{id}/stop") public Result<Void> stop(@PathVariable long id) { service.instances(id).stream().findFirst().ifPresent(instance -> service.stop(instance.executionId())); return Result.ok(null, "同步任务已停止"); }
    @GetMapping("/{id}/instances") public Result<List<IntegrationInstanceView>> instances(@PathVariable long id) { return Result.ok(service.instances(id)); }
    @GetMapping("/{id}/batches") public Result<List<IntegrationBatchView>> batches(@PathVariable long id) { return Result.ok(service.batches(id)); }
    @GetMapping("/batches/{batchId}/attempts") public Result<List<IntegrationAttemptView>> attempts(@PathVariable long batchId) { return Result.ok(service.attempts(batchId)); }
    @PostMapping("/batches/{batchId}/retry") public Result<IntegrationBatchView> retry(@PathVariable long batchId) { return Result.ok(service.retryBatch(batchId), "离线同步批次已重试"); }
    @PostMapping("/batches/{batchId}/reconcile") public Result<IntegrationBatchView> reconcile(@PathVariable long batchId) { return Result.ok(service.reconcileBatch(batchId), "离线同步批次状态已核对"); }
    @PostMapping("/{id}/backfill") public Result<IntegrationBatchView> backfill(@PathVariable long id, @Valid @RequestBody IntegrationRequests.BackfillRequest request) { return Result.ok(service.backfill(id, request), "补数批次已提交"); }
    @GetMapping("/{id}/cursor") public Result<IntegrationCursorView> cursor(@PathVariable long id) { return Result.ok(service.cursor(id)); }
    @PutMapping("/{id}/cursor") public Result<IntegrationCursorView> saveCursor(@PathVariable long id, @RequestBody IntegrationRequests.CursorRequest request) { return Result.ok(service.saveCursor(id, request), "增量游标已更新"); }
    @GetMapping("/executions/{executionId}") public Result<SeaTunnelGateway.JobStatus> status(@PathVariable String executionId) { return Result.ok(service.status(executionId)); }
    @GetMapping("/executions/{executionId}/log") public Result<String> log(@PathVariable String executionId) { return Result.ok(service.log(executionId)); }
    @PostMapping("/executions/{executionId}/cancel") public Result<Void> cancel(@PathVariable String executionId) { service.cancel(executionId); return Result.ok(null); }
}
