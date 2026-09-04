package com.company.platform.lineage;

import com.company.platform.common.Result;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lineage")
public class LineageController {
    private final LineageService service;
    public LineageController(LineageService service) { this.service = service; }
    @GetMapping public Result<List<LineageView>> list() { return Result.ok(service.list()); }
    @GetMapping("/table") public Result<List<LineageView>> table(@RequestParam String name) { return Result.ok(service.byTable(name)); }
    @GetMapping("/file/{fileId}") public Result<List<LineageView>> file(@PathVariable long fileId) { return Result.ok(service.byFile(fileId)); }
    @GetMapping("/workflow/{workflowId}") public Result<List<LineageView>> workflow(@PathVariable long workflowId) { return Result.ok(service.byWorkflow(workflowId)); }
    @PostMapping("/parse") public Result<List<LineageView>> parse(@RequestBody ParseRequest request) {
        return Result.ok(service.parseAndStore(request.fileId(), request.sql()));
    }
    public record ParseRequest(long fileId, @NotBlank String sql) { }
}
