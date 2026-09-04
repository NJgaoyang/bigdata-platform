package com.company.platform.workflow;

import java.util.List;

public record WorkflowView(long id, String name, String workflowCode, String description, String status,
                           int publishedVersion, List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges,
                           String dsProcessCode) {
    public WorkflowView(long id, String name, String workflowCode, String description, String status,
                        int publishedVersion, List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges) {
        this(id, name, workflowCode, description, status, publishedVersion, nodes, edges, null);
    }
}
