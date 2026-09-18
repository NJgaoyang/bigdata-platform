package com.company.platform.workflow;

public record WorkflowNodeView(long id, String name, NodeType nodeType, Long devFileId, Long fileVersionId,
                               String configJson, int x, int y, String nodeCode) {
    /** Backward-compatible constructor for older tests and callers that only knew fileVersionId. */
    public WorkflowNodeView(long id, String name, NodeType nodeType, Long fileVersionId,
                            String configJson, int x, int y, String nodeCode) {
        this(id, name, nodeType, null, fileVersionId, configJson, x, y, nodeCode);
    }

    /** Backward-compatible constructor for older tests and callers. */
    public WorkflowNodeView(long id, String name, NodeType nodeType, Long fileVersionId,
                            String configJson, int x, int y) {
        this(id, name, nodeType, null, fileVersionId, configJson, x, y, "node_" + id);
    }
}
