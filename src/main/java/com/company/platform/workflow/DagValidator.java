package com.company.platform.workflow;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DagValidator {
    public ValidationResult validate(List<WorkflowNodeView> nodes, List<WorkflowEdgeView> edges) {
        if (nodes == null || nodes.isEmpty()) return new ValidationResult(false, "工作流至少需要一个节点");
        if (edges == null) edges = List.of();
        Set<Long> ids = nodes.stream().map(WorkflowNodeView::id).collect(HashSet::new, Set::add, Set::addAll);
        Map<Long, List<Long>> graph = new HashMap<>();
        for (WorkflowEdgeView edge : edges) {
            if (!ids.contains(edge.sourceNodeId()) || !ids.contains(edge.targetNodeId())) {
                return new ValidationResult(false, "连线引用了不存在的节点");
            }
            graph.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>()).add(edge.targetNodeId());
        }
        Set<Long> visiting = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        for (Long id : ids) if (hasCycle(id, graph, visiting, visited)) {
            return new ValidationResult(false, "工作流不能包含环路");
        }
        return new ValidationResult(true, "DAG 校验通过");
    }
    private boolean hasCycle(Long id, Map<Long, List<Long>> graph, Set<Long> visiting, Set<Long> visited) {
        if (visiting.contains(id)) return true;
        if (visited.contains(id)) return false;
        visiting.add(id);
        for (Long next : graph.getOrDefault(id, List.of())) if (hasCycle(next, graph, visiting, visited)) return true;
        visiting.remove(id);
        visited.add(id);
        return false;
    }
    public record ValidationResult(boolean valid, String message) { }
}
