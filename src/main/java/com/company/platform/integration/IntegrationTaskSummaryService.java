package com.company.platform.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class IntegrationTaskSummaryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public IntegrationTaskSummaryService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void recordCreator(long taskId, String operator) {
        String value = operator == null || operator.isBlank() ? "platform" : operator.trim();
        jdbc.update("UPDATE integration_task SET created_by=? WHERE id=?", value, taskId);
    }

    public Summary summary(long taskId) {
        TaskMeta task = jdbc.queryForObject("SELECT created_at,created_by FROM integration_task WHERE id=?",
                (rs, n) -> new TaskMeta(time(rs.getTimestamp("created_at")), rs.getString("created_by")), taskId);
        LatestRun run = latestRun(taskId);
        return new Summary(task == null ? null : task.createdAt(), task == null ? "platform" : task.createdBy(),
                run == null ? null : run.startedAt(), nextRun(taskId), run == null ? null : run.durationMs(),
                run == null ? null : run.dataCount());
    }

    private LatestRun latestRun(long taskId) {
        List<LatestRun> rows = jdbc.query("SELECT b.id,b.started_at,b.finished_at,b.created_at," +
                        "SUM(v.target_count) data_count,COUNT(v.id) validation_count " +
                        "FROM integration_batch b LEFT JOIN integration_validation_result v ON v.batch_id=b.id " +
                        "WHERE b.task_id=? GROUP BY b.id,b.started_at,b.finished_at,b.created_at ORDER BY b.created_at DESC LIMIT 1",
                (rs, n) -> {
                    LocalDateTime started = time(rs.getTimestamp("started_at"));
                    LocalDateTime finished = time(rs.getTimestamp("finished_at"));
                    LocalDateTime created = time(rs.getTimestamp("created_at"));
                    Long duration = started == null ? null : java.time.Duration.between(started,
                            finished == null ? LocalDateTime.now() : finished).toMillis();
                    Long count = rs.getInt("validation_count") > 0 ? rs.getLong("data_count") : null;
                    return new LatestRun(started == null ? created : started, duration, count);
                }, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LocalDateTime nextRun(long taskId) {
        try {
            List<DirectSchedule> direct = jdbc.query("SELECT s.cron_expression,s.timezone FROM integration_task_schedule s WHERE s.task_id=? AND s.enabled=TRUE",
                    (rs,n) -> new DirectSchedule(rs.getString("cron_expression"), rs.getString("timezone")), taskId);
            if (!direct.isEmpty()) {
                DirectSchedule schedule = direct.getFirst();
                ZoneId zone = ZoneId.of(schedule.timezone() == null || schedule.timezone().isBlank() ? "Asia/Shanghai" : schedule.timezone());
                ZonedDateTime next = CronExpression.parse(schedule.cron()).next(ZonedDateTime.now(zone));
                if (next != null) return next.toLocalDateTime();
            }
            List<ScheduleRef> refs = jdbc.query("SELECT wn.config_json,sc.cron_expression,sc.timezone FROM workflow_node wn " +
                            "JOIN schedule_config sc ON sc.workflow_id=wn.workflow_id " +
                            "WHERE wn.node_type='SEATUNNEL' AND sc.enabled=TRUE",
                    (rs, n) -> new ScheduleRef(rs.getString("config_json"), rs.getString("cron_expression"), rs.getString("timezone")));
            ZonedDateTime earliest = null;
            for (ScheduleRef ref : refs) {
                JsonNode config = mapper.readTree(ref.configJson() == null ? "{}" : ref.configJson());
                if (config.path("integrationTaskId").asLong(0) != taskId) continue;
                ZoneId zone = ZoneId.of(ref.timezone() == null || ref.timezone().isBlank() ? "Asia/Shanghai" : ref.timezone());
                ZonedDateTime next = CronExpression.parse(ref.cron()).next(ZonedDateTime.now(zone));
                if (next != null && (earliest == null || next.toInstant().isBefore(earliest.toInstant()))) earliest = next;
            }
            return earliest == null ? null : earliest.toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime time(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }

    public record Summary(LocalDateTime createdAt, String createdBy, LocalDateTime lastRunAt,
                          LocalDateTime nextRunAt, Long durationMs, Long dataCount) { }
    private record TaskMeta(LocalDateTime createdAt, String createdBy) { }
    private record LatestRun(LocalDateTime startedAt, Long durationMs, Long dataCount) { }
    private record ScheduleRef(String configJson, String cron, String timezone) { }
    private record DirectSchedule(String cron, String timezone) { }
}
