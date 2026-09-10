package com.company.platform.workflow;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    private final PlatformStore store;
    private final DagValidator validator;
    public WorkflowService(PlatformStore store, DagValidator validator) { this.store = store; this.validator = validator; }
    public List<WorkflowView> list() { return store.workflows.values().stream().toList(); }

    @Transactional
    public WorkflowView create(WorkflowRequests.WorkflowRequest request) {
        long workflowId = store.nextId();
        GraphDraft graph = buildGraph(request, null);
        DagValidator.ValidationResult validation = validator.validate(graph.nodes(), graph.edges());
        if (!validation.valid()) throw new BadRequestException(validation.message());
        WorkflowView view = new WorkflowView(workflowId, request.name(), "wf_" + UUID.randomUUID().toString().replace("-", ""),
                request.description(), "DRAFT", 0, graph.nodes(), graph.edges());
        store.persistWorkflow(view);
        store.workflows.put(workflowId, view);
        return view;
    }

    @Transactional
    public WorkflowView update(long id, WorkflowRequests.WorkflowRequest request) {
        WorkflowView current = get(id);
        GraphDraft graph = buildGraph(request, current);
        DagValidator.ValidationResult validation = validator.validate(graph.nodes(), graph.edges());
        if (!validation.valid()) throw new BadRequestException(validation.message());
        WorkflowView view = new WorkflowView(id, request.name(), current.workflowCode(), request.description(), "DRAFT",
                current.publishedVersion(), graph.nodes(), graph.edges(), current.dsProcessCode());
        store.persistWorkflow(view);
        store.workflows.put(id, view);
        return view;
    }

    @Transactional
    public void delete(long id) {
        WorkflowView workflow = get(id);
        if ("PUBLISHED".equals(workflow.status())) throw new BadRequestException("已发布工作流不能直接删除，请先下线");
        store.deleteWorkflow(id);
        store.workflows.remove(id);
    }

    public WorkflowView get(long id) {
        WorkflowView view = store.workflows.get(id);
        if (view == null) throw new NotFoundException("工作流不存在：" + id);
        return view;
    }
    public DagValidator.ValidationResult validate(long id) {
        WorkflowView view = get(id);
        return validator.validate(view.nodes(), view.edges());
    }

    private GraphDraft buildGraph(WorkflowRequests.WorkflowRequest request, WorkflowView current) {
        Map<String, Long> nodeIds = new HashMap<>();
        List<WorkflowNodeView> nodes = request.nodes() == null ? List.of() : request.nodes().stream()
                .map(node -> {
                    String nodeCode = node.nodeCode() == null || node.nodeCode().isBlank()
                            ? "node_" + UUID.randomUUID().toString().replace("-", "") : node.nodeCode();
                    long nodeId = current == null ? store.nextId() : current.nodes().stream()
                            .filter(existing -> nodeCode.equals(existing.nodeCode())).map(WorkflowNodeView::id)
                            .findFirst().orElseGet(store::nextId);
                    if (nodeIds.putIfAbsent(nodeCode, nodeId) != null) throw new BadRequestException("工作流节点编码重复：" + nodeCode);
                    return new WorkflowNodeView(nodeId, node.name(), node.nodeType() == null ? NodeType.SQL : node.nodeType(),
                            node.fileVersionId(), node.configJson(), node.x(), node.y(), nodeCode);
                }).toList();
        List<WorkflowEdgeView> edges = request.edges() == null ? List.of() : request.edges().stream()
                .map(edge -> new WorkflowEdgeView(store.nextId(), resolveNodeId(edge.sourceNodeId(), edge.sourceNodeCode(), nodeIds),
                        resolveNodeId(edge.targetNodeId(), edge.targetNodeCode(), nodeIds))).toList();
        return new GraphDraft(nodes, edges);
    }

    private long resolveNodeId(Long nodeId, String nodeCode, Map<String, Long> nodeIds) {
        if (nodeCode != null && !nodeCode.isBlank()) return nodeIds.getOrDefault(nodeCode, 0L);
        return nodeId == null ? 0L : nodeId;
    }
    private record GraphDraft(List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges) { }
}
