package com.company.platform.realtime;

import com.company.platform.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/realtime/jobs")
public class RealtimeSyncController {
    private final RealtimeSyncService service;
    private final RealtimeManagementService management;
    private final RealtimeVersionService versions;
    public RealtimeSyncController(RealtimeSyncService service, RealtimeManagementService management, RealtimeVersionService versions){this.service=service;this.management=management;this.versions=versions;}
    @GetMapping public Result<List<RealtimeViews.Job>> list(){return Result.ok(service.list());}
    @GetMapping("/management") public Result<List<RealtimeManagementService.ManagementRow>> management(){service.refreshAllRuntimeStates();return Result.ok(management.management());}
    @GetMapping("/metadata/tables") public Result<List<RealtimeManagementService.TableOption>> tables(@RequestParam long dataSourceId,@RequestParam String database){return Result.ok(management.tables(dataSourceId,database));}
    @GetMapping("/metadata/columns") public Result<List<RealtimeManagementService.ColumnOption>> columns(@RequestParam long dataSourceId,@RequestParam String database,@RequestParam String table){return Result.ok(management.columns(dataSourceId,database,table));}
    @PostMapping public Result<RealtimeViews.Job> create(@Valid @RequestBody RealtimeRequests.CreateJobRequest r,HttpServletRequest req){return Result.ok(service.create(r,operator(req)),"实时任务草稿已创建");}
    @PostMapping("/preview-validate") public Result<RealtimeSyncService.Validation> previewValidate(@Valid @RequestBody RealtimeRequests.CreateJobRequest r){return Result.ok(service.validateDraft(r));}
    @GetMapping("/{id}") public Result<RealtimeViews.Job> get(@PathVariable long id){return Result.ok(service.get(id));}
    @PutMapping("/{id}/draft") public Result<RealtimeViews.Job> draft(@PathVariable long id,@RequestBody RealtimeRequests.DraftRequest r,HttpServletRequest req){return Result.ok(service.saveDraft(id,r,operator(req)),"草稿已保存");}
    @PostMapping("/{id}/validate") public Result<RealtimeSyncService.Validation> validate(@PathVariable long id){return Result.ok(service.validate(id));}
    @PostMapping("/{id}/publish") public Result<RealtimeViews.Job> publish(@PathVariable long id,HttpServletRequest req){return Result.ok(service.publish(id,operator(req)),"实时任务已发布");}
    @PostMapping("/{id}/start") public Result<RealtimeViews.Runtime> start(@PathVariable long id,HttpServletRequest req){return Result.ok(service.start(id,operator(req)),"实时任务已启动");}
    @PostMapping("/{id}/stop") public Result<RealtimeViews.Runtime> stop(@PathVariable long id,HttpServletRequest req){return Result.ok(service.stop(id,operator(req)),"实时任务已停止");}
    @DeleteMapping("/{id}") public Result<Void> remove(@PathVariable long id,HttpServletRequest req){service.remove(id,operator(req));return Result.ok(null,"实时任务已删除");}
    @PostMapping("/{id}/restart") public Result<RealtimeViews.Runtime> restart(@PathVariable long id,HttpServletRequest req){return Result.ok(service.restart(id,operator(req)),"实时任务已重启");}
    @PostMapping("/{id}/apply-published-version") public Result<RealtimeViews.Runtime> apply(@PathVariable long id,HttpServletRequest req){return Result.ok(service.applyPublishedVersion(id,operator(req)),"已应用发布版本");}
    @GetMapping("/{id}/runtime") public Result<RealtimeViews.Runtime> runtime(@PathVariable long id){return Result.ok(service.runtime(id));}
    @GetMapping("/{id}/checkpoints") public Result<JsonNode> checkpoints(@PathVariable long id){return Result.ok(service.checkpoints(id));}
    @GetMapping("/{id}/metrics") public Result<JsonNode> metrics(@PathVariable long id){return Result.ok(service.metrics(id));}
    @GetMapping("/{id}/logs") public Result<String> logs(@PathVariable long id){return Result.ok(service.logs(id));}
    @GetMapping("/{id}/yaml") public Result<String> yaml(@PathVariable long id){return Result.ok(service.yaml(id));}
    @GetMapping("/{id}/versions") public Result<List<RealtimeVersionService.VersionRow>> versions(@PathVariable long id){service.get(id);return Result.ok(versions.versions(id));}
    @GetMapping("/{id}/executions") public Result<List<RealtimeManagementService.ExecutionRow>> executions(@PathVariable long id){service.get(id);return Result.ok(management.executions(id));}
    @GetMapping("/{id}/events") public Result<List<RealtimeManagementService.EventRow>> events(@PathVariable long id){service.get(id);return Result.ok(management.events(id));}
    @GetMapping("/{id}/checkpoint-history") public Result<List<RealtimeManagementService.CheckpointRow>> checkpointHistory(@PathVariable long id){service.get(id);return Result.ok(management.checkpointHistory(id));}
    @GetMapping("/{id}/schema-changes") public Result<List<RealtimeManagementService.SchemaChangeRow>> schemaChanges(@PathVariable long id){service.get(id);return Result.ok(management.schemaChanges(id));}
    @PostMapping("/{id}/schema-changes/scan") public Result<List<RealtimeManagementService.SchemaChangeRow>> scanSchemaChanges(@PathVariable long id){return Result.ok(management.scanSchemaChanges(service.get(id)),"Schema 变更检测完成");}
    @GetMapping("/{id}/validation-results") public Result<List<RealtimeManagementService.ValidationRow>> validationResults(@PathVariable long id){service.get(id);return Result.ok(management.validationResults(id));}
    @PostMapping("/{id}/data-validation") public Result<List<RealtimeManagementService.ValidationRow>> validateData(@PathVariable long id){return Result.ok(management.runValidation(service.get(id)),"实时数据校验完成");}
    private String operator(HttpServletRequest req){Object o=req.getAttribute("platform.operator");return o==null?"admin":String.valueOf(o);}
}
