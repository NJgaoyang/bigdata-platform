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
    public RealtimeSyncController(RealtimeSyncService service){this.service=service;}
    @GetMapping public Result<List<RealtimeViews.Job>> list(){return Result.ok(service.list());}
    @PostMapping public Result<RealtimeViews.Job> create(@Valid @RequestBody RealtimeRequests.CreateJobRequest r,HttpServletRequest req){return Result.ok(service.create(r,operator(req)),"实时任务草稿已创建");}
    @GetMapping("/{id}") public Result<RealtimeViews.Job> get(@PathVariable long id){return Result.ok(service.get(id));}
    @PutMapping("/{id}/draft") public Result<RealtimeViews.Job> draft(@PathVariable long id,@RequestBody RealtimeRequests.DraftRequest r,HttpServletRequest req){return Result.ok(service.saveDraft(id,r,operator(req)),"草稿已保存");}
    @PostMapping("/{id}/validate") public Result<RealtimeSyncService.Validation> validate(@PathVariable long id){return Result.ok(service.validate(id));}
    @PostMapping("/{id}/publish") public Result<RealtimeViews.Job> publish(@PathVariable long id,HttpServletRequest req){return Result.ok(service.publish(id,operator(req)),"实时任务已发布");}
    @PostMapping("/{id}/start") public Result<RealtimeViews.Runtime> start(@PathVariable long id,HttpServletRequest req){return Result.ok(service.start(id,operator(req)),"实时任务已启动");}
    @PostMapping("/{id}/stop") public Result<RealtimeViews.Runtime> stop(@PathVariable long id,HttpServletRequest req){return Result.ok(service.stop(id,operator(req)),"实时任务已停止");}
    @PostMapping("/{id}/restart") public Result<RealtimeViews.Runtime> restart(@PathVariable long id,HttpServletRequest req){return Result.ok(service.restart(id,operator(req)),"实时任务已重启");}
    @PostMapping("/{id}/apply-published-version") public Result<RealtimeViews.Runtime> apply(@PathVariable long id,HttpServletRequest req){return Result.ok(service.applyPublishedVersion(id,operator(req)),"已应用发布版本");}
    @GetMapping("/{id}/runtime") public Result<RealtimeViews.Runtime> runtime(@PathVariable long id){return Result.ok(service.runtime(id));}
    @GetMapping("/{id}/checkpoints") public Result<JsonNode> checkpoints(@PathVariable long id){return Result.ok(service.checkpoints(id));}
    @GetMapping("/{id}/metrics") public Result<JsonNode> metrics(@PathVariable long id){return Result.ok(service.metrics(id));}
    @GetMapping("/{id}/logs") public Result<JsonNode> logs(@PathVariable long id){return Result.ok(service.logs(id));}
    @GetMapping("/{id}/yaml") public Result<String> yaml(@PathVariable long id){return Result.ok(service.yaml(id));}
    private String operator(HttpServletRequest req){Object o=req.getAttribute("platform.operator");return o==null?"admin":String.valueOf(o);}
}
