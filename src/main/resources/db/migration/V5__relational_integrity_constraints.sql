ALTER TABLE dev_folder
    ADD CONSTRAINT fk_dev_folder_project FOREIGN KEY (project_id) REFERENCES dev_project(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_dev_folder_parent FOREIGN KEY (parent_id) REFERENCES dev_folder(id) ON DELETE SET NULL;

ALTER TABLE dev_file
    ADD CONSTRAINT fk_dev_file_project FOREIGN KEY (project_id) REFERENCES dev_project(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_dev_file_folder FOREIGN KEY (folder_id) REFERENCES dev_folder(id) ON DELETE SET NULL;

ALTER TABLE dev_file_version
    ADD CONSTRAINT fk_dev_file_version_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE;

ALTER TABLE query_execution
    ADD CONSTRAINT fk_query_execution_datasource FOREIGN KEY (datasource_id) REFERENCES data_source(id) ON DELETE SET NULL;

ALTER TABLE workflow_node
    ADD CONSTRAINT fk_workflow_node_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_workflow_node_version FOREIGN KEY (file_version_id) REFERENCES dev_file_version(id) ON DELETE SET NULL;

ALTER TABLE workflow_edge
    ADD CONSTRAINT fk_workflow_edge_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE;

ALTER TABLE schedule_config
    ADD CONSTRAINT fk_schedule_config_workflow FOREIGN KEY (workflow_id) REFERENCES workflow(id) ON DELETE CASCADE;

ALTER TABLE integration_instance
    ADD CONSTRAINT fk_integration_instance_task FOREIGN KEY (task_id) REFERENCES integration_task(id) ON DELETE CASCADE;

ALTER TABLE data_lineage
    ADD CONSTRAINT fk_data_lineage_file FOREIGN KEY (dev_file_id) REFERENCES dev_file(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_data_lineage_version FOREIGN KEY (file_version_id) REFERENCES dev_file_version(id) ON DELETE SET NULL;

ALTER TABLE role_permission
    ADD CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES platform_role(id) ON DELETE CASCADE;

ALTER TABLE project_member
    ADD CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES dev_project(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_project_member_user FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE;

ALTER TABLE datasource_permission
    ADD CONSTRAINT fk_datasource_permission_datasource FOREIGN KEY (datasource_id) REFERENCES data_source(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_datasource_permission_user FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE;
