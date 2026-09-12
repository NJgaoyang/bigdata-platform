package com.company.platform.query;

import com.company.platform.common.Result;
import com.company.platform.datasource.DataSourceAccessService;
import com.company.platform.system.AuditService;
import com.company.platform.system.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/query")
public class QueryController {
    private final QueryService service;
    private final AuditService audit;
    private final AuthService auth;
    private final DataSourceAccessService dataSourceAccess;

    public QueryController(QueryService service, AuditService audit, AuthService auth, DataSourceAccessService dataSourceAccess) {
        this.service = service; this.audit = audit; this.auth = auth; this.dataSourceAccess = dataSourceAccess;
    }

    @PostMapping("/execute")
    public Result<QueryService.QueryResult> execute(@RequestBody QueryRequest request, HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        requireDataSource(request.dataSourceId(), operator);
        QueryService.QueryResult result = service.execute(request.sql(), request.selected(), request.dataSourceId(), request.databaseName(), operator);
        audit.record("QUERY_EXECUTE", "QUERY", null, "status=" + result.status() + ", rows=" + result.rowCount(), operator);
        return Result.ok(result);
    }

    @PostMapping("/submit")
    public Result<QueryService.QueryHandle> submit(@RequestBody QueryRequest request, HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        requireDataSource(request.dataSourceId(), operator);
        QueryService.QueryHandle result = service.submit(request.sql(), request.selected(), request.dataSourceId(), request.databaseName(), operator);
        audit.record("QUERY_SUBMIT", "QUERY", null, "executionId=" + result.executionId(), operator);
        return Result.ok(result);
    }

    @GetMapping("/{executionId}")
    public Result<QueryService.QueryResult> status(@PathVariable String executionId, HttpServletRequest request) {
        String operator = operator(request);
        return Result.ok(service.status(executionId, operator, auth.isAdministrator(operator)));
    }

    @GetMapping("/history")
    public Result<List<QueryHistoryView>> history(HttpServletRequest request) {
        String operator = operator(request);
        return Result.ok(service.history(operator, auth.isAdministrator(operator)));
    }

    @PostMapping("/{executionId}/cancel")
    public Result<Void> cancel(@PathVariable String executionId, HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        service.cancel(executionId, operator, auth.isAdministrator(operator));
        audit.record("QUERY_CANCEL", "QUERY", null, "executionId=" + executionId, operator);
        return Result.ok(null, "查询已停止");
    }

    private void requireDataSource(Long dataSourceId, String operator) {
        if (dataSourceId != null) dataSourceAccess.requireQuery(dataSourceId, operator);
    }
    private String operator(HttpServletRequest request) {
        Object value = request.getAttribute("platform.operator");
        return value == null ? "admin" : String.valueOf(value);
    }
    public record QueryRequest(@NotBlank String sql, boolean selected, Long dataSourceId, String databaseName) { }
}
