package com.company.platform.query;

import com.company.platform.common.Result;
import com.company.platform.system.AuditService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {
    private final QueryService service;
    private final AuditService audit;
    public QueryController(QueryService service, AuditService audit) { this.service = service; this.audit = audit; }
    @PostMapping("/execute") public Result<QueryService.QueryResult> execute(@RequestBody QueryRequest request) {
        QueryService.QueryResult result = service.execute(request.sql(), request.selected(), request.dataSourceId(), request.databaseName());
        audit.record("QUERY_EXECUTE", "QUERY", null, "status=" + result.status() + ", rows=" + result.rowCount(), "admin");
        return Result.ok(result);
    }
    @PostMapping("/{executionId}/cancel") public Result<Void> cancel(@PathVariable String executionId) {
        service.cancel(executionId); return Result.ok(null, "查询已停止");
    }
    public record QueryRequest(@NotBlank String sql, boolean selected, Long dataSourceId, String databaseName) { }
}
