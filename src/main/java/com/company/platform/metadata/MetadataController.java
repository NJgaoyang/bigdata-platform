package com.company.platform.metadata;

import com.company.platform.common.Result;
import com.company.platform.datasource.DataSourceAccessService;
import com.company.platform.datasource.DataSourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    @GetMapping("/table-profile")
    public Result<TableProfileResponse> tableProfile(@RequestParam long dataSourceId, @RequestParam String database,
                                                     @RequestParam String table, HttpServletRequest request) {
        String operator = operator(request);
        access.requireView(dataSourceId, operator);
        return Result.ok(profileResponse(service.tableProfile(dataSourceId, database, table),
                access.canAccess(dataSourceId, operator, DataSourceAccessService.Access.EDIT)));
    }

    @PutMapping("/table-profile/owner")
    public Result<TableProfileResponse> updateTableOwner(@Valid @RequestBody TableOwnerRequest request,
                                                         HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        access.requireEdit(request.dataSourceId(), operator);
        MetadataService.TableProfileView profile = service.setTableOwner(request.dataSourceId(), request.database(),
                request.table(), request.owner(), operator);
        return Result.ok(profileResponse(profile, true), "数据表拥有者已更新");
    }

    private TableProfileResponse profileResponse(MetadataService.TableProfileView profile, boolean ownerEditable) {
        return new TableProfileResponse(profile.dataSourceId(), profile.database(), profile.table(), profile.rowCount(),
                profile.estimatedSizeBytes(), profile.owner(), profile.createTime(), profile.updateTime(), ownerEditable);
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


    public record TableOwnerRequest(long dataSourceId, @NotBlank String database, @NotBlank String table, String owner) { }
    public record TableProfileResponse(long dataSourceId, String database, String table, Long rowCount,
                                       Long estimatedSizeBytes, String owner, java.time.LocalDateTime createTime,
                                       java.time.LocalDateTime updateTime, boolean ownerEditable) { }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
}
