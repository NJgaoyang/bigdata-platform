package com.company.platform.workflow;

import com.company.platform.common.PlatformStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowServiceTest {
    @Test
    void mapsCanvasNodeCodesToPersistedIds() {
        PlatformStore store = new PlatformStore();
        WorkflowService service = new WorkflowService(store, new DagValidator());
        WorkflowView workflow = service.create(new WorkflowRequests.WorkflowRequest(
                "canvas", "x6", List.of(
                new WorkflowRequests.NodeRequest("source", NodeType.SQL, null, null, 10, 20, "source"),
                new WorkflowRequests.NodeRequest("target", NodeType.SQL, null, null, 30, 20, "target")),
                List.of(new WorkflowRequests.EdgeRequest(null, null, "source", "target"))));

        assertEquals(2, workflow.nodes().size());
        assertEquals(workflow.nodes().get(0).id(), workflow.edges().get(0).sourceNodeId());
        assertEquals(workflow.nodes().get(1).id(), workflow.edges().get(0).targetNodeId());
    }
}
