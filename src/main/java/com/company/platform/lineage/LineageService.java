package com.company.platform.lineage;

import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LineageService {
    private final PlatformStore store;
    private final SqlLineageParser parser;
    public LineageService(PlatformStore store, SqlLineageParser parser) { this.store = store; this.parser = parser; }
    public List<LineageView> parseAndStore(long fileId, String sql) {
        return parseAndStore(fileId, null, sql);
    }
    public List<LineageView> parseAndStore(long fileId, Long fileVersionId, String sql) {
        SqlLineageParser.ParseResult result = parser.parse(sql);
        for (String source : result.sources()) for (String target : result.targets()) {
            long id = store.nextId();
            LineageView lineage = new LineageView(id, source, target, "SQL", fileId, fileVersionId);
            store.lineages.put(id, lineage);
            store.persistLineage(lineage);
        }
        return list();
    }
    public List<LineageView> list() { return store.lineages.values().stream().toList(); }
    public List<LineageView> byFile(long fileId) { return store.lineages.values().stream().filter(item -> item.fileId() != null && item.fileId() == fileId).toList(); }
    public List<LineageView> byTable(String table) {
        return store.lineages.values().stream().filter(item -> item.sourceTable().equalsIgnoreCase(table) || item.targetTable().equalsIgnoreCase(table)).toList();
    }
    public List<LineageView> byWorkflow(long workflowId) {
        var workflow = store.workflows.get(workflowId);
        if (workflow == null) return List.of();
        var fileIds = workflow.nodes().stream().map(node -> node.fileVersionId() == null ? null : store.versions.get(node.fileVersionId()))
                .filter(java.util.Objects::nonNull).map(version -> version.fileId()).collect(java.util.stream.Collectors.toSet());
        return store.lineages.values().stream().filter(item -> item.fileId() != null && fileIds.contains(item.fileId())).toList();
    }
    public void removeForFile(long fileId) {
        store.lineages.values().removeIf(item -> item.fileId() != null && item.fileId() == fileId);
        store.deleteLineageForFile(fileId);
    }
}
