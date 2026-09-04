package com.company.platform.workflow;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class WorkflowRequests {
    private WorkflowRequests() { }
    public record NodeRequest(@NotBlank String name, NodeType nodeType, Long fileVersionId,
                              String configJson, int x, int y, String nodeCode) {
        public NodeRequest(String name, NodeType nodeType, Long fileVersionId,
                           String configJson, int x, int y) {
            this(name, nodeType, fileVersionId, configJson, x, y, null);
        }
    }
    public record EdgeRequest(Long sourceNodeId, Long targetNodeId,
                              String sourceNodeCode, String targetNodeCode) {
        public EdgeRequest(long sourceNodeId, long targetNodeId) {
            this(sourceNodeId, targetNodeId, null, null);
        }
    }
    public record WorkflowRequest(@NotBlank String name, String description,
                                  List<NodeRequest> nodes, List<EdgeRequest> edges) { }
}
