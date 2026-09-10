package com.company.platform.scheduler;

import com.company.platform.common.PlatformStore;
import com.company.platform.workflow.DagValidator;
import com.company.platform.workflow.NodeType;
import com.company.platform.workflow.WorkflowRequests;
import com.company.platform.workflow.WorkflowService;
import com.company.platform.lineage.LineageService;
import com.company.platform.lineage.SqlLineageParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerServiceTest {
    @Test
    void savesScheduleAndRequiresPublishedWorkflowForOnline() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflows = new WorkflowService(store, new DagValidator());
        var workflow = workflows.create(new WorkflowRequests.WorkflowRequest("daily_sales", "demo",
                List.of(new WorkflowRequests.NodeRequest("query", NodeType.SQL, null, null, 0, 0)), List.of()));
        SchedulerService service = new SchedulerService(store, workflows, testGateway());

        ScheduleConfigView saved = service.save(workflow.id(), new ScheduleRequests.ScheduleRequest(
                "0 0 2 * * ?", "Asia/Shanghai", true, "END", 1));
        assertFalse(saved.enabled(), "保存配置不能伪造远端已上线状态");
        assertThrows(RuntimeException.class, () -> service.online(workflow.id()));
    }

    @Test
    void rejectsNonQuartzCronBeforeCallingDolphinScheduler() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflows = new WorkflowService(store, new DagValidator());
        var workflow = workflows.create(new WorkflowRequests.WorkflowRequest("daily_sales", "demo",
                List.of(new WorkflowRequests.NodeRequest("query", NodeType.SQL, null, null, 0, 0)), List.of()));
        SchedulerService service = new SchedulerService(store, workflows, testGateway());
        assertThrows(RuntimeException.class, () -> service.save(workflow.id(), new ScheduleRequests.ScheduleRequest(
                "0 2 * * *", "Asia/Shanghai", false, "END", 1)));
    }

    @Test
    void onlineScheduleIsEnabledAfterWorkflowPublish() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflows = new WorkflowService(store, new DagValidator());
        var gateway = testGateway();
        long versionId = store.versions.values().iterator().next().id();
        var published = workflows.create(new WorkflowRequests.WorkflowRequest("published_sales", "demo",
                List.of(new WorkflowRequests.NodeRequest("query", NodeType.SQL, versionId, null, 0, 0)), List.of()));
        new com.company.platform.workflow.WorkflowPublishService(workflows, gateway, store,
                new LineageService(store, new SqlLineageParser())).publish(published.id());
        SchedulerService service = new SchedulerService(store, workflows, gateway);
        assertTrue(service.online(published.id()).enabled());
    }

    private static SchedulerGateway testGateway() {
        return new SchedulerGateway() {
            @Override public PublishResult publish(PublishRequest request) { return new PublishResult("test-process-1", request.version(), "PUBLISHED"); }
            @Override public RunResult run(String processCode) { return new RunResult("test-instance-1", "RUNNING"); }
            @Override public InstanceStatus status(String instanceId) { return new InstanceStatus(instanceId, "RUNNING", ""); }
            @Override public void stop(String instanceId) { }
            @Override public RunResult rerun(String instanceId) { return new RunResult(instanceId, "RUNNING"); }
            @Override public RunResult backfill(String processCode, String start, String end, int parallelism) { return new RunResult("test-instance-1", "RUNNING"); }
            @Override public String upsertSchedule(String processCode, String cronExpression, String timezone, boolean enabled,
                                                   String failureStrategy, int parallelism) { return "test-schedule-1"; }
            @Override public void scheduleState(String scheduleId, boolean online) { }
            @Override public void release(String processCode, boolean online) { }
        };
    }
}
