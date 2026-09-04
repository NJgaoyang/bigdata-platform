package com.company.platform.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagValidatorTest {
    private final DagValidator validator = new DagValidator();
    @Test
    void acceptsAcyclicGraph() {
        List<WorkflowNodeView> nodes = List.of(new WorkflowNodeView(1, "a", NodeType.SQL, null, null, 0, 0), new WorkflowNodeView(2, "b", NodeType.SHELL, null, null, 0, 0));
        assertTrue(validator.validate(nodes, List.of(new WorkflowEdgeView(3, 1, 2))).valid());
    }
    @Test
    void rejectsCycle() {
        List<WorkflowNodeView> nodes = List.of(new WorkflowNodeView(1, "a", NodeType.SQL, null, null, 0, 0), new WorkflowNodeView(2, "b", NodeType.SHELL, null, null, 0, 0));
        assertFalse(validator.validate(nodes, List.of(new WorkflowEdgeView(3, 1, 2), new WorkflowEdgeView(4, 2, 1))).valid());
    }
}
