package com.company.platform.workflow;

import com.company.platform.common.PlatformStore;
import com.company.platform.scheduler.SchedulerGateway;
import com.company.platform.lineage.LineageService;
import com.company.platform.lineage.SqlLineageParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowPublishServiceTest {
    @Test
    void publishesThroughGatewayWithoutExternalService() {
        PlatformStore store = new PlatformStore();
        WorkflowService workflowService = new WorkflowService(store, new DagValidator());
        long versionId = store.versions.values().iterator().next().id();
        // Intentionally send the wrong UI node type. The bound development file is .sql/SQL,
        // so publishing must derive the scheduler task type from the file instead of trusting UI state.
        WorkflowView workflow = workflowService.create(new WorkflowRequests.WorkflowRequest("sales_daily", "demo",
                List.of(new WorkflowRequests.NodeRequest("daily sales", NodeType.SHELL, versionId, null, 0, 0)), List.of()));
        AtomicReference<SchedulerGateway.PublishRequest> published = new AtomicReference<>();
        AtomicBoolean releasedOnline = new AtomicBoolean(false);
        SchedulerGateway gateway = new SchedulerGateway() {
            @Override public PublishResult publish(PublishRequest request) {
                published.set(request);
                return new PublishResult("test-process-1", request.version(), "PUBLISHED");
            }
            @Override public void release(String processCode, boolean online) {
                if ("test-process-1".equals(processCode) && online) releasedOnline.set(true);
            }
            @Override public RunResult run(String processCode) { return new RunResult("test-instance-1", "RUNNING"); }
            @Override public InstanceStatus status(String instanceId) { return new InstanceStatus(instanceId, "RUNNING", ""); }
            @Override public void stop(String instanceId) { }
            @Override public RunResult rerun(String instanceId) { return new RunResult(instanceId, "RUNNING"); }
            @Override public RunResult backfill(String processCode, String start, String end, int parallelism) {
                return new RunResult("test-instance-1", "RUNNING");
            }
        };
        WorkflowPublishService service = new WorkflowPublishService(workflowService, gateway, store,
                new LineageService(store, new SqlLineageParser()));
        assertEquals("PUBLISHED", service.publish(workflow.id()).status());
        assertTrue(published.get().definitionJson().contains("\"type\":\"SQL\""));
        assertTrue(releasedOnline.get(), "publish must release the DolphinScheduler definition ONLINE");
    }
}
