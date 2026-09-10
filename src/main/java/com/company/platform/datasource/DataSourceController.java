package com.company.platform.datasource;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/data-sources", "/api/datasources"})
public class DataSourceController {
    private final DataSourceService service;
    private final DataSourceAccessService access;
    public DataSourceController(DataSourceService service, DataSourceAccessService access) { this.service = service; this.access = access; }

    @GetMapping
    public Result<List<DataSourceView>> list(HttpServletRequest request) {
        return Result.ok(access.filterVisible(service.list(), operator(request)));
    }

    @PostMapping
    public Result<DataSourceView> create(@Valid @RequestBody CreateDataSourceRequest request) {
        return Result.ok(service.create(request), "数据源已创建");
    }

    @GetMapping("/{id}")
    public Result<DataSourceView> get(@PathVariable long id, HttpServletRequest request) {
        access.requireView(id, operator(request));
        return Result.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public Result<DataSourceView> update(@PathVariable long id, @Valid @RequestBody CreateDataSourceRequest request, HttpServletRequest servletRequest) {
        access.requireEdit(id, operator(servletRequest));
        return Result.ok(service.update(id, request), "数据源已更新");
    }

    @PutMapping("/{id}/metadata-visibility")
    public Result<DataSourceView> setMetadataVisibility(@PathVariable long id, @RequestBody MetadataVisibilityRequest request,
                                                        HttpServletRequest servletRequest) {
        access.requireEdit(id, operator(servletRequest));
        return Result.ok(service.setMetadataVisible(id, request.visible()), request.visible() ? "已在元数据中展示" : "已从元数据中隐藏");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id, HttpServletRequest servletRequest) {
        access.requireEdit(id, operator(servletRequest));
        service.delete(id);
        return Result.ok(null, "数据源已删除");
    }

    @RequestMapping(value = "/{id}/test", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<DataSourceService.ConnectionTestResult> test(@PathVariable long id, HttpServletRequest servletRequest) {
        access.requireEdit(id, operator(servletRequest));
        return Result.ok(service.test(id));
    }

    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
    public record MetadataVisibilityRequest(boolean visible) { }
}
