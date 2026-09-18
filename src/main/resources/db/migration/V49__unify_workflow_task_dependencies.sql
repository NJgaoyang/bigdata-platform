-- Workflow task nodes now bind the development task itself. file_version_id remains for historical compatibility.
UPDATE workflow_node n
JOIN dev_file_version v ON v.id = n.file_version_id
SET n.dev_file_id = v.file_id
WHERE n.dev_file_id IS NULL
  AND n.file_version_id IS NOT NULL;

-- Move SQL -> SQL task dependencies to the shared development dependency graph.
INSERT IGNORE INTO dev_file_schedule_dependency(file_id, upstream_file_id)
SELECT target_node.dev_file_id, source_node.dev_file_id
FROM workflow_edge edge_row
JOIN workflow_node source_node ON source_node.id = edge_row.source_node_id
JOIN workflow_node target_node ON target_node.id = edge_row.target_node_id
WHERE source_node.node_type = 'SQL'
  AND target_node.node_type = 'SQL'
  AND source_node.dev_file_id IS NOT NULL
  AND target_node.dev_file_id IS NOT NULL
  AND source_node.dev_file_id <> target_node.dev_file_id;

-- workflow_edge keeps only workflow-local orchestration edges (conditions / special nodes).
DELETE edge_row
FROM workflow_edge edge_row
JOIN workflow_node source_node ON source_node.id = edge_row.source_node_id
JOIN workflow_node target_node ON target_node.id = edge_row.target_node_id
WHERE source_node.node_type = 'SQL'
  AND target_node.node_type = 'SQL'
  AND source_node.dev_file_id IS NOT NULL
  AND target_node.dev_file_id IS NOT NULL;
