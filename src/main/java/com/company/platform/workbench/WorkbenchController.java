package com.company.platform.workbench;

import com.company.platform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {
    private final WorkbenchService service;

    public WorkbenchController(WorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public Result<WorkbenchService.Summary> summary() {
        return Result.ok(service.summary());
    }

    @GetMapping("/issues")
    public Result<List<WorkbenchService.Issue>> issues() {
        return Result.ok(service.issues());
    }

    @GetMapping("/recent-runs")
    public Result<List<WorkbenchService.RecentRun>> recentRuns() {
        return Result.ok(service.recentRuns());
    }
}
