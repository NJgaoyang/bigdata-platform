package com.company.platform.scheduler;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.workflow.DagValidator;
import com.company.platform.workflow.NodeType;
import com.company.platform.workflow.WorkflowRequests;
import com.company.platform.workflow.WorkflowService;
import com.company.platform.lineage.LineageService;
import com.company.platform.lineage.SqlLineageParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerServiceTest {
    @Test
    void savesScheduleAndRequiresPublishedWorkflowForOnline() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflows = new WorkflowService(store, new DagValidator());
        var workflow = workflows.create(new WorkflowRequests.WorkflowRequest("daily_sales", "demo",
                List.of(new WorkflowRequests.NodeRequest("query", NodeType.SQL, null, null, 0, 0)), List.of()));
        var gateway = new DolphinSchedulerGatewayImpl(new PlatformProperties());
        SchedulerService service = new SchedulerService(store, workflows, gateway);

        ScheduleConfigView saved = service.save(workflow.id(), new ScheduleRequests.ScheduleRequest(
                "0 0 2 * * ?", "Asia/Shanghai", false, "END", 1));
        assertFalse(saved.enabled());
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.online(workflow.id()));
    }

    @Test
    void onlineScheduleIsEnabledAfterWorkflowPublish() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflows = new WorkflowService(store, new DagValidator());
        var workflow = workflows.create(new WorkflowRequests.WorkflowRequest("daily_sales", "demo",
                List.of(new WorkflowRequests.NodeRequest("query", NodeType.SQL, null, null, 0, 0)), List.of()));
        var gateway = new DolphinSchedulerGatewayImpl(new PlatformProperties());
        long versionId = store.versions.values().iterator().next().id();
        var published = workflows.create(new WorkflowRequests.WorkflowRequest("published_sales", "demo",
                List.of(new WorkflowRequests.NodeRequest("query", NodeType.SQL, versionId, null, 0, 0)), List.of()));
        new com.company.platform.workflow.WorkflowPublishService(workflows, gateway, store,
                new LineageService(store, new SqlLineageParser())).publish(published.id());
        SchedulerService service = new SchedulerService(store, workflows, gateway);

        assertTrue(service.online(published.id()).enabled());
    }
}
