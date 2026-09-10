ALTER TABLE workflow_node
    ADD CONSTRAINT fk_workflow_node_file FOREIGN KEY (dev_file_id) REFERENCES dev_file(id) ON DELETE SET NULL;

ALTER TABLE workflow_edge
    ADD CONSTRAINT fk_workflow_edge_source_node FOREIGN KEY (source_node_id) REFERENCES workflow_node(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_workflow_edge_target_node FOREIGN KEY (target_node_id) REFERENCES workflow_node(id) ON DELETE CASCADE;

ALTER TABLE data_lineage
    ADD CONSTRAINT fk_data_lineage_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE SET NULL;

ALTER TABLE dev_file_version
    ADD CONSTRAINT fk_dev_file_version_creator FOREIGN KEY (created_by) REFERENCES platform_user(id) ON DELETE SET NULL;

ALTER TABLE platform_user
    ADD CONSTRAINT fk_platform_user_role FOREIGN KEY (role_code) REFERENCES platform_role(role_code) ON DELETE RESTRICT;
