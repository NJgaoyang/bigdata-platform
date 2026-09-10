package com.company.platform.query;

import com.company.platform.common.Result;
import com.company.platform.system.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {
    private final QueryService service;
    private final AuditService audit;
    public QueryController(QueryService service, AuditService audit) { this.service = service; this.audit = audit; }
    @PostMapping("/execute") public Result<QueryService.QueryResult> execute(@RequestBody QueryRequest request, HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        QueryService.QueryResult result = service.execute(request.sql(), request.selected(), request.dataSourceId(), request.databaseName(), operator);
        audit.record("QUERY_EXECUTE", "QUERY", null, "status=" + result.status() + ", rows=" + result.rowCount(), operator);
        return Result.ok(result);
    }
    @PostMapping("/submit") public Result<QueryService.QueryHandle> submit(@RequestBody QueryRequest request) {
        return Result.ok(service.submit(request.sql(), request.selected(), request.dataSourceId(), request.databaseName()));
    }
    @GetMapping("/{executionId}") public Result<QueryService.QueryResult> status(@PathVariable String executionId) {
        return Result.ok(service.status(executionId));
    }
    @GetMapping("/history") public Result<java.util.List<QueryHistoryView>> history() {
        return Result.ok(service.history());
    }
    @PostMapping("/{executionId}/cancel") public Result<Void> cancel(@PathVariable String executionId) {
        service.cancel(executionId); return Result.ok(null, "查询已停止");
    }
    private String operator(HttpServletRequest request) { Object value = request.getAttribute("platform.operator"); return value == null ? "admin" : String.valueOf(value); }
    public record QueryRequest(@NotBlank String sql, boolean selected, Long dataSourceId, String databaseName) { }
}
