package com.company.platform.release;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/release")
public class ReleaseController {
    private final ReleaseService service;
    public ReleaseController(ReleaseService service){this.service=service;}
    @GetMapping("/policy") public Result<ReleaseService.Policy> policy(){return Result.ok(service.policy());}
    @PutMapping("/policy") public Result<ReleaseService.Policy> policy(@RequestBody PolicyRequest r,HttpServletRequest req){return Result.ok(service.updatePolicy(r.approvalRequired(),operator(req)),"发布策略已更新");}
    @GetMapping("/requests") public Result<List<ReleaseService.RequestView>> requests(@RequestParam(required=false)String status){return Result.ok(service.requests(status));}
    @PostMapping("/requests") public Result<ReleaseService.RequestView> request(@RequestBody ReleaseService.ReleaseRequest r,HttpServletRequest req){return Result.ok(service.request(r,operator(req)),"发布申请已提交");}
    @PostMapping("/requests/{id}/approve") public Result<ReleaseService.RequestView> approve(@PathVariable long id,@RequestBody(required=false) ReleaseService.ReviewRequest r,HttpServletRequest req){return Result.ok(service.approve(id,r==null?null:r.comment(),operator(req)),"审批通过并已发布");}
    @PostMapping("/requests/{id}/reject") public Result<ReleaseService.RequestView> reject(@PathVariable long id,@RequestBody(required=false) ReleaseService.ReviewRequest r,HttpServletRequest req){return Result.ok(service.reject(id,r==null?null:r.comment(),operator(req)),"已驳回发布申请");}
    @GetMapping("/records") public Result<List<ReleaseService.RecordView>> records(){return Result.ok(service.records());}
    private String operator(HttpServletRequest req){Object o=req.getAttribute("platform.operator");return o==null?"admin":String.valueOf(o);}
    public record PolicyRequest(boolean approvalRequired){}
}
