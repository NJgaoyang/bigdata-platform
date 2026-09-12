package com.company.platform.metric;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricController {
    private final MetricService service; public MetricController(MetricService service){this.service=service;}
    @GetMapping("/overview") public Result<MetricService.Overview> overview(){return Result.ok(service.overview());}
    @GetMapping public Result<List<MetricService.MetricView>> list(@RequestParam(required=false)String type,@RequestParam(required=false)String status){return Result.ok(service.list(type,status));}
    @GetMapping("/{id}") public Result<MetricService.MetricView> get(@PathVariable long id){return Result.ok(service.get(id));}
    @PostMapping public Result<MetricService.MetricView> create(@RequestBody MetricService.MetricRequest r,HttpServletRequest req){return Result.ok(service.create(r,operator(req)),"指标已创建");}
    @PutMapping("/{id}") public Result<MetricService.MetricView> update(@PathVariable long id,@RequestBody MetricService.MetricRequest r,HttpServletRequest req){return Result.ok(service.update(id,r,operator(req)),"指标草稿已更新");}
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable long id){service.delete(id);return Result.ok(null,"指标已删除");}
    @PostMapping("/{id}/certify") public Result<MetricService.MetricView> certify(@PathVariable long id,HttpServletRequest req){return Result.ok(service.certify(id,operator(req)),"指标已认证");}
    @PostMapping("/{id}/uncertify") public Result<MetricService.MetricView> uncertify(@PathVariable long id){return Result.ok(service.revokeCertification(id),"指标认证已取消");}
    @GetMapping("/{id}/versions") public Result<List<MetricService.VersionView>> versions(@PathVariable long id){return Result.ok(service.versions(id));}
    @GetMapping("/lineage") public Result<List<MetricService.LineageView>> lineage(@RequestParam(required=false)Long metricId){return Result.ok(service.lineage(metricId));}
    @GetMapping("/dimensions") public Result<List<MetricService.DimensionView>> dimensions(){return Result.ok(service.dimensions());}
    @PostMapping("/dimensions") public Result<MetricService.DimensionView> createDimension(@RequestBody MetricService.DimensionRequest r){return Result.ok(service.saveDimension(null,r),"维度已创建");}
    @PutMapping("/dimensions/{id}") public Result<MetricService.DimensionView> updateDimension(@PathVariable long id,@RequestBody MetricService.DimensionRequest r){return Result.ok(service.saveDimension(id,r),"维度已更新");}
    @DeleteMapping("/dimensions/{id}") public Result<Void> deleteDimension(@PathVariable long id){service.deleteDimension(id);return Result.ok(null,"维度已删除");}
    private String operator(HttpServletRequest req){Object o=req.getAttribute("platform.operator");return o==null?"admin":String.valueOf(o);}
}
