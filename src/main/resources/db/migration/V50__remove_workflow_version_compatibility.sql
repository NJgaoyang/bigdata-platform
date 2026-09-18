ALTER TABLE workflow_node
  DROP FOREIGN KEY fk_workflow_node_version,
  DROP COLUMN file_version_id;
