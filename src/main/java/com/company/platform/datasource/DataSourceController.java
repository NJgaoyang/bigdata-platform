package com.company.platform.datasource;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/data-sources", "/api/datasources"})
public class DataSourceController {
    private final DataSourceService service;
    public DataSourceController(DataSourceService service) { this.service = service; }

    @GetMapping
    public Result<List<DataSourceView>> list() { return Result.ok(service.list()); }

    @PostMapping
    public Result<DataSourceView> create(@Valid @RequestBody CreateDataSourceRequest request) {
        return Result.ok(service.create(request), "数据源已创建");
    }

    @GetMapping("/{id}")
    public Result<DataSourceView> get(@PathVariable long id) { return Result.ok(service.get(id)); }

    @PutMapping("/{id}")
    public Result<DataSourceView> update(@PathVariable long id, @Valid @RequestBody CreateDataSourceRequest request) {
        return Result.ok(service.update(id, request), "数据源已更新");
    }

    @PutMapping("/{id}/metadata-visibility")
    public Result<DataSourceView> setMetadataVisibility(@PathVariable long id,
                                                        @RequestBody MetadataVisibilityRequest request) {
        return Result.ok(service.setMetadataVisible(id, request.visible()),
                request.visible() ? "已在元数据中展示" : "已从元数据中隐藏");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        service.delete(id);
        return Result.ok(null, "数据源已删除");
    }

    @RequestMapping(value = "/{id}/test", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<DataSourceService.ConnectionTestResult> test(@PathVariable long id) {
        return Result.ok(service.test(id));
    }


    public record MetadataVisibilityRequest(boolean visible) { }
}
