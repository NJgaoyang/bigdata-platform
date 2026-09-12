package com.company.platform.scheduler;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.workflow.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Service
public class SchedulerService {
    private static final Set<String> FAILURE_STRATEGIES = Set.of("END", "CONTINUE");
    private final PlatformStore store;
    private final WorkflowService workflows;
    private final SchedulerGateway gateway;

    public SchedulerService(PlatformStore store, WorkflowService workflows, SchedulerGateway gateway) {
        this.store = store;
        this.workflows = workflows;
        this.gateway = gateway;
    }

    public ScheduleConfigView get(long workflowId) {
        workflows.get(workflowId);
        return store.scheduleConfigs.values().stream()
                .filter(config -> config.workflowId() == workflowId)
                .findFirst()
                .orElseGet(() -> new ScheduleConfigView(0, workflowId, "0 0 2 * * ?", "Asia/Shanghai", false, "END", 1));
    }

    @Transactional
    public ScheduleConfigView save(long workflowId, ScheduleRequests.ScheduleRequest request) {
        workflows.get(workflowId);
        validateCron(request.cronExpression());
        validateTimezone(request.timezone());
        if (request.parallelism() < 1 || request.parallelism() > 100) {
            throw new BadRequestException("补数据并行度必须在 1 到 100 之间");
        }
        String failureStrategy = normalizeFailureStrategy(request.failureStrategy());
        String workerGroup = request.workerGroup() == null || request.workerGroup().isBlank() ? "default" : request.workerGroup().trim();
        ScheduleConfigView current = get(workflowId);
        long id = current.id() == 0 ? store.nextId() : current.id();
        // PUT only edits configuration. Online/offline state is changed exclusively
        // by the explicit endpoints so local state can never claim a remote schedule is enabled.
        ScheduleConfigView saved = new ScheduleConfigView(id, workflowId, request.cronExpression().trim(),
                request.timezone().trim(), current.enabled(), failureStrategy, request.parallelism(), workerGroup,
                request.alertGroup(), current.dsScheduleId());
        store.persistSchedule(saved);
        store.scheduleConfigs.put(id, saved);
        return saved;
    }

    public ScheduleConfigView online(long workflowId) {
        var workflow = workflows.get(workflowId);
        if (!"PUBLISHED".equals(workflow.status())) throw new BadRequestException("工作流必须发布后才能上线调度");
        ScheduleConfigView current = get(workflowId);
        validateCron(current.cronExpression());
        validateTimezone(current.timezone());
        String processCode = engineCode(workflow);
        gateway.release(processCode, true);
        try {
            String scheduleId = gateway.upsertSchedule(processCode, current.cronExpression(), current.timezone(),
                    true, normalizeFailureStrategy(current.failureStrategy()), current.parallelism(), current.workerGroup(), current.alertGroup());
            if (scheduleId == null || scheduleId.isBlank()) throw new IllegalStateException("DolphinScheduler 未返回有效调度编号");
            gateway.scheduleState(scheduleId, true);
            ScheduleConfigView online = new ScheduleConfigView(current.id() == 0 ? store.nextId() : current.id(), workflowId,
                    current.cronExpression(), current.timezone(), true, normalizeFailureStrategy(current.failureStrategy()),
                    current.parallelism(), current.workerGroup(), current.alertGroup(), scheduleId);
            store.persistSchedule(online);
            store.scheduleConfigs.put(online.id(), online);
            return online;
        } catch (RuntimeException ex) {
            try { gateway.release(processCode, false); } catch (RuntimeException ignored) { }
            throw ex;
        }
    }

    public ScheduleConfigView offline(long workflowId) {
        var workflow = workflows.get(workflowId);
        ScheduleConfigView current = get(workflowId);
        if (current.id() == 0) return current;
        String processCode = engineCode(workflow);
        String scheduleId = gateway.upsertSchedule(processCode, current.cronExpression(), current.timezone(),
                false, normalizeFailureStrategy(current.failureStrategy()), current.parallelism(), current.workerGroup(), current.alertGroup());
        String resolvedScheduleId = scheduleId == null || scheduleId.isBlank() ? current.dsScheduleId() : scheduleId;
        if (resolvedScheduleId != null && !resolvedScheduleId.isBlank()) gateway.scheduleState(resolvedScheduleId, false);
        gateway.release(processCode, false);
        ScheduleConfigView offline = new ScheduleConfigView(current.id(), workflowId, current.cronExpression(),
                current.timezone(), false, normalizeFailureStrategy(current.failureStrategy()), current.parallelism(),
                current.workerGroup(), current.alertGroup(), resolvedScheduleId);
        store.persistSchedule(offline);
        store.scheduleConfigs.put(offline.id(), offline);
        return offline;
    }

    public SchedulerGateway.RunResult backfill(long workflowId, ScheduleRequests.BackfillRequest request) {
        var workflow = workflows.get(workflowId);
        if (!"PUBLISHED".equals(workflow.status())) throw new BadRequestException("工作流必须发布后才能补数据");
        if (request.parallelism() < 1 || request.parallelism() > 100) {
            throw new BadRequestException("补数据并行度必须在 1 到 100 之间");
        }
        if (request.start().compareTo(request.end()) > 0) throw new BadRequestException("补数据开始时间不能晚于结束时间");
        return gateway.backfill(engineCode(workflow), request.start(), request.end(), request.parallelism());
    }

    private String engineCode(com.company.platform.workflow.WorkflowView workflow) {
        return workflow.dsProcessCode() == null || workflow.dsProcessCode().isBlank()
                ? workflow.workflowCode() : workflow.dsProcessCode();
    }

    private void validateCron(String cron) {
        if (cron == null || cron.isBlank()) throw new BadRequestException("Cron 表达式不能为空");
        int fields = cron.trim().split("\\s+").length;
        if (fields < 6 || fields > 7) throw new BadRequestException("DolphinScheduler Cron 必须使用 6 或 7 个字段的 Quartz 格式");
    }

    private void validateTimezone(String timezone) {
        try { ZoneId.of(timezone); }
        catch (DateTimeException | NullPointerException ex) { throw new BadRequestException("无效的时区：" + timezone); }
    }

    private String normalizeFailureStrategy(String value) {
        String normalized = value == null || value.isBlank() ? "END" : value.trim().toUpperCase(Locale.ROOT);
        if (!FAILURE_STRATEGIES.contains(normalized)) throw new BadRequestException("失败策略仅支持 END 或 CONTINUE");
        return normalized;
    }
}
