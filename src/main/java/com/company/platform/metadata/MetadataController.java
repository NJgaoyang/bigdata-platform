package com.company.platform.metadata;

import com.company.platform.common.Result;
import com.company.platform.datasource.DataSourceType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {
    private final MetadataService service;
    public MetadataController(MetadataService service) { this.service = service; }
    @GetMapping("/databases") public Result<List<MetadataService.DatabaseView>> databases(
            @RequestParam long dataSourceId, @RequestParam(defaultValue = "STARROCKS") DataSourceType type) {
        return Result.ok(service.databases(dataSourceId, type));
    }
    @GetMapping("/tables") public Result<List<MetadataService.TableView>> tables(
            @RequestParam long dataSourceId, @RequestParam String database) { return Result.ok(service.tables(dataSourceId, database)); }
    @GetMapping("/columns") public Result<List<MetadataService.ColumnView>> columns(
            @RequestParam long dataSourceId, @RequestParam String database, @RequestParam String table) {
        return Result.ok(service.columns(dataSourceId, database, table));
    }
    @GetMapping("/datasources/{dataSourceId}/databases")
    public Result<List<MetadataService.DatabaseView>> databasesByDataSource(@PathVariable long dataSourceId,
                                                                              @RequestParam(defaultValue = "STARROCKS") DataSourceType type) {
        return Result.ok(service.databases(dataSourceId, type));
    }
    @GetMapping("/datasources/{dataSourceId}/tables")
    public Result<List<MetadataService.TableView>> tablesByDataSource(@PathVariable long dataSourceId,
                                                                       @RequestParam String database) {
        return Result.ok(service.tables(dataSourceId, database));
    }
    @GetMapping("/datasources/{dataSourceId}/columns")
    public Result<List<MetadataService.ColumnView>> columnsByDataSource(@PathVariable long dataSourceId,
                                                                          @RequestParam String database, @RequestParam String table) {
        return Result.ok(service.columns(dataSourceId, database, table));
    }
}
