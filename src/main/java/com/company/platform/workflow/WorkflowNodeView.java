package com.company.platform.workflow;

public record WorkflowNodeView(long id, String name, NodeType nodeType, Long devFileId,
                               String configJson, int x, int y, String nodeCode) {
    public WorkflowNodeView(long id, String name, NodeType nodeType, Long devFileId,
                            String configJson, int x, int y) {
        this(id, name, nodeType, devFileId, configJson, x, y, "node_" + id);
    }
}
