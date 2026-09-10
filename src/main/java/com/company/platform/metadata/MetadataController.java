package com.company.platform.metadata;

import com.company.platform.common.Result;
import com.company.platform.datasource.DataSourceAccessService;
import com.company.platform.datasource.DataSourceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {
    private final MetadataService service;
    private final DataSourceAccessService access;
    public MetadataController(MetadataService service, DataSourceAccessService access) { this.service = service; this.access = access; }

    @GetMapping("/databases")
    public Result<List<MetadataService.DatabaseView>> databases(@RequestParam long dataSourceId,
            @RequestParam(defaultValue = "STARROCKS") DataSourceType type, HttpServletRequest request) {
        access.requireView(dataSourceId, operator(request));
        return Result.ok(service.databases(dataSourceId, type));
    }
    @GetMapping("/tables")
    public Result<List<MetadataService.TableView>> tables(@RequestParam long dataSourceId, @RequestParam String database,
                                                           HttpServletRequest request) {
        access.requireView(dataSourceId, operator(request));
        return Result.ok(service.tables(dataSourceId, database));
    }
    @GetMapping("/columns")
    public Result<List<MetadataService.ColumnView>> columns(@RequestParam long dataSourceId, @RequestParam String database,
                                                             @RequestParam String table, HttpServletRequest request) {
        access.requireView(dataSourceId, operator(request));
        return Result.ok(service.columns(dataSourceId, database, table));
    }
    @GetMapping("/datasources/{dataSourceId}/databases")
    public Result<List<MetadataService.DatabaseView>> databasesByDataSource(@PathVariable long dataSourceId,
            @RequestParam(defaultValue = "STARROCKS") DataSourceType type, HttpServletRequest request) {
        access.requireView(dataSourceId, operator(request));
        return Result.ok(service.databases(dataSourceId, type));
    }
    @GetMapping("/datasources/{dataSourceId}/tables")
    public Result<List<MetadataService.TableView>> tablesByDataSource(@PathVariable long dataSourceId,
            @RequestParam String database, HttpServletRequest request) {
        access.requireView(dataSourceId, operator(request));
        return Result.ok(service.tables(dataSourceId, database));
    }
    @GetMapping("/datasources/{dataSourceId}/columns")
    public Result<List<MetadataService.ColumnView>> columnsByDataSource(@PathVariable long dataSourceId,
            @RequestParam String database, @RequestParam String table, HttpServletRequest request) {
        access.requireView(dataSourceId, operator(request));
        return Result.ok(service.columns(dataSourceId, database, table));
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
