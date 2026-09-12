package com.company.platform.realtime;

import com.company.platform.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flink/environments")
public class FlinkEnvironmentController {
    private final FlinkEnvironmentService service;
    private final FlinkRestClient rest;
    public FlinkEnvironmentController(FlinkEnvironmentService service,FlinkRestClient rest){this.service=service;this.rest=rest;}
    @GetMapping public Result<List<FlinkEnvironmentView>> list(){return Result.ok(service.list());}
    @PostMapping public Result<FlinkEnvironmentView> create(@Valid @RequestBody RealtimeRequests.EnvironmentRequest r){return Result.ok(service.save(null,r),"Flink 环境已创建");}
    @PutMapping("/{id}") public Result<FlinkEnvironmentView> update(@PathVariable long id,@Valid @RequestBody RealtimeRequests.EnvironmentRequest r){return Result.ok(service.save(id,r),"Flink 环境已更新");}
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id){service.delete(id);return Result.ok(null,"Flink 环境已删除");}
    @PostMapping("/{id}/test") public Result<JsonNode> test(@PathVariable long id){return Result.ok(rest.get(service.get(id).restUrl(),"/overview"),"Flink REST 连接成功");}
}
