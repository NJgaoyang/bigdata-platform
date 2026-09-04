package com.company.platform.workflow;

public record WorkflowNodeView(long id, String name, NodeType nodeType, Long fileVersionId,
                               String configJson, int x, int y) { }
