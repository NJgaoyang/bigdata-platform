package com.company.platform.workflow;

import com.company.platform.common.PlatformStore;
import com.company.platform.scheduler.DolphinSchedulerGatewayImpl;
import com.company.platform.config.PlatformProperties;
import com.company.platform.lineage.LineageService;
import com.company.platform.lineage.SqlLineageParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowPublishServiceTest {
    @Test
    void publishesThroughGatewayWithoutExternalService() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflowService = new WorkflowService(store, new DagValidator());
        long versionId = store.versions.values().iterator().next().id();
        WorkflowView workflow = workflowService.create(new WorkflowRequests.WorkflowRequest("sales_daily", "demo",
                List.of(new WorkflowRequests.NodeRequest("daily sales", NodeType.SQL, versionId, null, 0, 0)), List.of()));
        WorkflowPublishService service = new WorkflowPublishService(workflowService, new DolphinSchedulerGatewayImpl(new PlatformProperties()), store,
                new LineageService(store, new SqlLineageParser()));
        assertEquals("MOCK_PUBLISHED@DS-3.1.9", service.publish(workflow.id()).status());
    }
}
