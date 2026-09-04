package com.company.platform.scheduler;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.workflow.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SchedulerService {
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

    public ScheduleConfigView save(long workflowId, ScheduleRequests.ScheduleRequest request) {
        workflows.get(workflowId);
        validateCron(request.cronExpression());
        if (request.parallelism() < 1 || request.parallelism() > 100) {
            throw new BadRequestException("补数据并行度必须在 1 到 100 之间");
        }
        ScheduleConfigView current = get(workflowId);
        long id = current.id() == 0 ? store.nextId() : current.id();
        ScheduleConfigView saved = new ScheduleConfigView(id, workflowId, request.cronExpression(),
                request.timezone(), request.enabled(), request.failureStrategy(), request.parallelism());
        store.scheduleConfigs.put(id, saved);
        store.persistSchedule(saved);
        return saved;
    }

    public ScheduleConfigView online(long workflowId) {
        var workflow = workflows.get(workflowId);
        if (!"PUBLISHED".equals(workflow.status())) throw new BadRequestException("工作流必须发布后才能上线调度");
        ScheduleConfigView current = get(workflowId);
        gateway.release(engineCode(workflow), true);
        ScheduleConfigView configured = new ScheduleConfigView(current.id() == 0 ? store.nextId() : current.id(), workflowId,
                current.cronExpression(), current.timezone(), true, current.failureStrategy(), current.parallelism());
        String scheduleId = gateway.upsertSchedule(engineCode(workflow), configured.cronExpression(), configured.timezone(),
                true, configured.failureStrategy(), configured.parallelism());
        if (!scheduleId.isBlank()) gateway.scheduleState(scheduleId, true);
        ScheduleConfigView online = configured;
        store.scheduleConfigs.put(online.id(), online);
        store.persistSchedule(online);
        return online;
    }

    public ScheduleConfigView offline(long workflowId) {
        var workflow = workflows.get(workflowId);
        ScheduleConfigView current = get(workflowId);
        if (current.id() == 0) return current;
        String scheduleId = gateway.upsertSchedule(engineCode(workflow), current.cronExpression(), current.timezone(),
                false, current.failureStrategy(), current.parallelism());
        if (!scheduleId.isBlank()) gateway.scheduleState(scheduleId, false);
        gateway.release(engineCode(workflow), false);
        ScheduleConfigView offline = new ScheduleConfigView(current.id(), workflowId, current.cronExpression(),
                current.timezone(), false, current.failureStrategy(), current.parallelism());
        store.scheduleConfigs.put(offline.id(), offline);
        store.persistSchedule(offline);
        return offline;
    }

    public SchedulerGateway.RunResult backfill(long workflowId, ScheduleRequests.BackfillRequest request) {
        var workflow = workflows.get(workflowId);
        if (!"PUBLISHED".equals(workflow.status())) throw new BadRequestException("工作流必须发布后才能补数据");
        if (request.parallelism() < 1 || request.parallelism() > 100) {
            throw new BadRequestException("补数据并行度必须在 1 到 100 之间");
        }
        return gateway.backfill(engineCode(workflow), request.start(), request.end(), request.parallelism());
    }

    private String engineCode(com.company.platform.workflow.WorkflowView workflow) {
        return workflow.dsProcessCode() == null || workflow.dsProcessCode().isBlank()
                ? workflow.workflowCode() : workflow.dsProcessCode();
    }

    private void validateCron(String cron) {
        if (cron == null || cron.isBlank() || cron.trim().split("\\s+").length < 5) {
            throw new BadRequestException("Cron 表达式至少需要 5 个字段");
        }
    }
}
