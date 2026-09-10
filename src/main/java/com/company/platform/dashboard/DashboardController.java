package com.company.platform.dashboard;

import com.company.platform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read models tailored to the final platform workbench and module dashboards. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/overview") public Result<DashboardService.OverviewView> overview() {
        return Result.ok(service.overview());
    }

    @GetMapping("/sources") public Result<DashboardService.SourceDashboardView> sources() {
        return Result.ok(service.sources());
    }

    @GetMapping("/integration") public Result<DashboardService.IntegrationDashboardView> integration(
            @RequestParam(defaultValue = "seven") String range) {
        return Result.ok(service.integration(range));
    }

    @GetMapping("/operations") public Result<DashboardService.OperationsDashboardView> operations() {
        return Result.ok(service.operations());
    }

    @GetMapping("/assets") public Result<DashboardService.AssetDashboardView> assets() {
        return Result.ok(service.assets());
    }
}
