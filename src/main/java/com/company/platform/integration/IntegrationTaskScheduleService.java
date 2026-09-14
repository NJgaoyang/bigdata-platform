package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

@Service
public class IntegrationTaskScheduleService {
    private final JdbcTemplate jdbc;
    private final Scheduler quartz;

    public IntegrationTaskScheduleService(JdbcTemplate jdbc, Scheduler quartz) {
        this.jdbc = jdbc;
        this.quartz = quartz;
    }

    public ScheduleView get(long taskId) {
        List<ScheduleView> rows = jdbc.query("SELECT task_id,cron_expression,timezone,enabled FROM integration_task_schedule WHERE task_id=?",
                (rs,n) -> new ScheduleView(rs.getLong("task_id"), rs.getString("cron_expression"), rs.getString("timezone"), rs.getBoolean("enabled")), taskId);
        return rows.isEmpty() ? new ScheduleView(taskId, "0 0 2 * * ?", "Asia/Shanghai", true) : rows.getFirst();
    }

    public ScheduleView save(long taskId, ScheduleRequest request) {
        validate(request);
        jdbc.update("INSERT INTO integration_task_schedule(task_id,cron_expression,timezone,enabled) VALUES(?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE cron_expression=VALUES(cron_expression),timezone=VALUES(timezone),enabled=VALUES(enabled),updated_at=CURRENT_TIMESTAMP",
                taskId, request.cronExpression().trim(), request.timezone().trim(), request.enabled());
        syncQuartz(taskId);
        return get(taskId);
    }

    public void activate(long taskId) { syncQuartz(taskId); }

    public void pause(long taskId) {
        try {
            TriggerKey key = triggerKey(taskId);
            if (quartz.checkExists(key)) quartz.pauseTrigger(key);
        } catch (SchedulerException ex) { throw new BadRequestException("暂停离线任务调度失败：" + ex.getMessage()); }
    }

    public void delete(long taskId) {
        try {
            quartz.unscheduleJob(triggerKey(taskId));
            quartz.deleteJob(jobKey(taskId));
        } catch (SchedulerException ignored) { }
        jdbc.update("DELETE FROM integration_task_schedule WHERE task_id=?", taskId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        jdbc.queryForList("SELECT task_id FROM integration_task_schedule").forEach(row -> syncQuartz(((Number)row.get("task_id")).longValue()));
    }

    private void syncQuartz(long taskId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM integration_task_schedule WHERE task_id=?", Integer.class, taskId);
        if (count == null || count == 0) return;
        ScheduleView config = get(taskId);
        boolean online = Boolean.TRUE.equals(jdbc.queryForObject("SELECT lifecycle_status='ONLINE' FROM integration_task WHERE id=?", Boolean.class, taskId));
        try {
            JobKey jobKey = jobKey(taskId);
            JobDetail job = JobBuilder.newJob(IntegrationTaskQuartzJob.class).withIdentity(jobKey)
                    .usingJobData("taskId", taskId).storeDurably(true).build();
            if (quartz.checkExists(jobKey)) quartz.addJob(job, true); else quartz.addJob(job, false);
            TriggerKey triggerKey = triggerKey(taskId);
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).forJob(jobKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(config.cronExpression())
                            .inTimeZone(TimeZone.getTimeZone(config.timezone())).withMisfireHandlingInstructionDoNothing())
                    .build();
            if (quartz.checkExists(triggerKey)) quartz.rescheduleJob(triggerKey, trigger); else quartz.scheduleJob(trigger);
            if (online && config.enabled()) quartz.resumeTrigger(triggerKey); else quartz.pauseTrigger(triggerKey);
        } catch (SchedulerException ex) { throw new BadRequestException("保存离线任务调度失败：" + ex.getMessage()); }
    }

    private void validate(ScheduleRequest request) {
        if (request == null || request.cronExpression() == null || request.cronExpression().isBlank()) throw new BadRequestException("Cron 表达式不能为空");
        if (!org.quartz.CronExpression.isValidExpression(request.cronExpression().trim())) throw new BadRequestException("Cron 表达式无效，请使用 Quartz Cron 格式");
        try { ZoneId.of(request.timezone()); } catch (Exception ex) { throw new BadRequestException("无效的时区：" + request.timezone()); }
    }

    private JobKey jobKey(long taskId) { return new JobKey("integration_task_" + taskId, "datasphere-integration"); }
    private TriggerKey triggerKey(long taskId) { return new TriggerKey("integration_schedule_" + taskId, "datasphere-integration"); }

    public record ScheduleRequest(String cronExpression, String timezone, boolean enabled) { }
    public record ScheduleView(long taskId, String cronExpression, String timezone, boolean enabled) { }
}
