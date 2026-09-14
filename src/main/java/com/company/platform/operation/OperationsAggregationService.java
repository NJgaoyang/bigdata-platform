package com.company.platform.operation;

import com.company.platform.common.BadRequestException;
import com.company.platform.integration.IntegrationService;
import com.company.platform.realtime.RealtimeSyncService;
import com.company.platform.scheduler.SchedulerGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OperationsAggregationService {
    private final JdbcTemplate jdbc;
    private final SchedulerGateway scheduler;
    private final IntegrationService integration;
    private final RealtimeSyncService realtime;

    public OperationsAggregationService(JdbcTemplate jdbc, SchedulerGateway scheduler,
                                        IntegrationService integration, RealtimeSyncService realtime) {
        this.jdbc = jdbc;
        this.scheduler = scheduler;
        this.integration = integration;
        this.realtime = realtime;
    }

    public Summary summary() {
        List<InstanceItem> values = instances();
        long running = values.stream().filter(item -> running(item.status())).count();
        long success = values.stream().filter(item -> success(item.status())).count();
        long failed = values.stream().filter(item -> failed(item.status())).count();
        long stopped = values.stream().filter(item -> stopped(item.status())).count();
        return new Summary(values.size(), running, success, failed, stopped);
    }

    public List<InstanceItem> instances() {
        List<InstanceItem> result = new ArrayList<>();
        result.addAll(jdbc.query("SELECT wi.id,wi.instance_code,w.name,wi.status,wi.run_type,wi.started_at,wi.finished_at,wi.error_message,wi.created_at " +
                "FROM workflow_instance wi LEFT JOIN workflow w ON w.workflow_code=wi.workflow_code ORDER BY wi.created_at DESC LIMIT 200",
                (rs, n) -> item("WORKFLOW", String.valueOf(rs.getLong("id")), rs.getString("instance_code"),
                        rs.getString("name"), rs.getString("status"), rs.getString("run_type"), "platform", rs.getTimestamp("started_at"),
                        rs.getTimestamp("finished_at"), rs.getString("error_message"), rs.getTimestamp("created_at"))));
        result.addAll(jdbc.query("SELECT ii.id,ii.execution_id,it.name,ii.status,ii.started_at,ii.finished_at,ii.error_message,ii.created_at," +
                "COALESCE(ib.created_by,it.created_by,'platform') AS created_by " +
                "FROM integration_instance ii LEFT JOIN integration_task it ON it.id=ii.task_id " +
                "LEFT JOIN integration_attempt ia ON ia.execution_id=ii.execution_id LEFT JOIN integration_batch ib ON ib.id=ia.batch_id " +
                "ORDER BY ii.created_at DESC LIMIT 200",
                (rs, n) -> item("OFFLINE", String.valueOf(rs.getLong("id")), rs.getString("execution_id"),
                        rs.getString("name"), rs.getString("status"), "SEATUNNEL", rs.getString("created_by"), rs.getTimestamp("started_at"),
                        rs.getTimestamp("finished_at"), rs.getString("error_message"), rs.getTimestamp("created_at"))));
        result.addAll(jdbc.query("SELECT re.id,re.engine_job_id,rd.name,re.status,re.started_at,re.finished_at,re.error_message,re.created_at,rd.created_by " +
                "FROM realtime_sync_execution re LEFT JOIN realtime_sync_definition rd ON rd.id=re.job_id ORDER BY re.created_at DESC LIMIT 200",
                (rs, n) -> item("REALTIME", String.valueOf(rs.getLong("id")), rs.getString("engine_job_id"),
                        rs.getString("name"), rs.getString("status"), "FLINK_CDC", rs.getString("created_by"), rs.getTimestamp("started_at"),
                        rs.getTimestamp("finished_at"), rs.getString("error_message"), rs.getTimestamp("created_at"))));
        return result.stream().sorted(Comparator.comparing(InstanceItem::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(300).toList();
    }

    public List<FailureItem> failures() {
        List<FailureItem> result = new ArrayList<>();
        result.addAll(jdbc.query("SELECT ti.id,wi.instance_code,ti.node_name,ti.node_type,ti.status,ti.attempt_no,ti.error_message,ti.started_at " +
                "FROM task_instance ti JOIN workflow_instance wi ON wi.id=ti.workflow_instance_id " +
                "WHERE ti.status IN ('FAILED','STOPPED') ORDER BY ti.created_at DESC LIMIT 200",
                (rs, n) -> new FailureItem("WORKFLOW_TASK", String.valueOf(rs.getLong("id")), rs.getString("instance_code"),
                        rs.getString("node_name"), rs.getString("node_type"), rs.getString("status"), rs.getInt("attempt_no"),
                        rs.getString("error_message"), time(rs.getTimestamp("started_at")))));
        result.addAll(instances().stream().filter(item -> failed(item.status()))
                .map(item -> new FailureItem(item.type(), item.id(), item.externalId(), item.name(), item.engine(), item.status(), 1,
                        item.errorMessage(), item.startedAt())).toList());
        return result.stream().sorted(Comparator.comparing(FailureItem::startedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(300).toList();
    }

    public List<AlertItem> alerts() {
        return failures().stream().map(item -> new AlertItem("RUN_FAILURE", item.type(), item.id(), item.name(),
                item.status(), item.errorMessage(), item.startedAt(), "OPEN")).toList();
    }

    public String log(String type, String id) {
        return switch (normalizeType(type)) {
            case "OFFLINE" -> {
                Map<String,Object> row = jdbc.queryForMap("SELECT execution_id FROM integration_instance WHERE id=?", Long.parseLong(id));
                String executionId = String.valueOf(row.get("execution_id"));
                yield integration.log(executionId);
            }
            case "REALTIME" -> {
                Long jobId = jdbc.queryForObject("SELECT job_id FROM realtime_sync_execution WHERE id=?", Long.class, Long.parseLong(id));
                if (jobId == null) throw new BadRequestException("实时运行实例不存在：" + id);
                yield realtime.logs(jobId).toPrettyString();
            }
            default -> throw new BadRequestException("当前实例类型暂不支持查看日志：" + type);
        };
    }

    public void stop(String type, String id) {
        switch (normalizeType(type)) {
            case "WORKFLOW" -> scheduler.stop(id);
            case "OFFLINE" -> {
                Map<String,Object> row = jdbc.queryForMap("SELECT execution_id FROM integration_instance WHERE id=?", Long.parseLong(id));
                integration.cancel(String.valueOf(row.get("execution_id")));
            }
            case "REALTIME" -> {
                Long jobId = jdbc.queryForObject("SELECT job_id FROM realtime_sync_execution WHERE id=?", Long.class, Long.parseLong(id));
                if (jobId == null) throw new BadRequestException("实时运行实例不存在：" + id);
                realtime.stop(jobId, "operations");
            }
            default -> throw new BadRequestException("不支持的实例类型：" + type);
        }
    }

    public SchedulerGateway.RunResult rerunWorkflow(String instanceId) { return scheduler.rerun(instanceId); }

    private String normalizeType(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private boolean running(String status) { String s = norm(status); return s.contains("RUNNING") || s.contains("STARTING") || s.contains("QUEUED"); }
    private boolean success(String status) { String s = norm(status); return s.contains("SUCCESS") || s.contains("FINISHED"); }
    private boolean failed(String status) { String s = norm(status); return s.contains("FAIL") || s.contains("ERROR") || s.contains("LOST") || s.contains("UNKNOWN"); }
    private boolean stopped(String status) { String s = norm(status); return s.contains("STOP") || s.contains("CANCEL"); }
    private String norm(String status) { return status == null ? "" : status.toUpperCase(Locale.ROOT); }
    private LocalDateTime time(java.sql.Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private InstanceItem item(String type, String id, String externalId, String name, String status, String engine,
                              String createdBy, java.sql.Timestamp started, java.sql.Timestamp finished, String error, java.sql.Timestamp created) {
        return new InstanceItem(type, id, externalId, name == null || name.isBlank() ? "未命名任务" : name,
                status, engine, createdBy == null || createdBy.isBlank() ? "platform" : createdBy,
                time(started), time(finished), error, time(created));
    }

    public record Summary(long total, long running, long success, long failed, long stopped) { }
    public record InstanceItem(String type, String id, String externalId, String name, String status, String engine,
                               String createdBy, LocalDateTime startedAt, LocalDateTime finishedAt, String errorMessage, LocalDateTime createdAt) { }
    public record FailureItem(String type, String id, String parentInstanceId, String name, String engine, String status,
                              int attemptNo, String errorMessage, LocalDateTime startedAt) { }
    public record AlertItem(String alertType, String resourceType, String resourceId, String name, String status,
                            String message, LocalDateTime occurredAt, String handlingState) { }
}
