CREATE TABLE IF NOT EXISTS integration_task_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    source_database VARCHAR(128) NOT NULL,
    source_table VARCHAR(128) NOT NULL,
    target_database VARCHAR(128) NOT NULL,
    target_table VARCHAR(128) NOT NULL,
    partition_column VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (task_id, source_database, source_table, target_database, target_table),
    CONSTRAINT fk_integration_task_table_task FOREIGN KEY (task_id)
        REFERENCES integration_task(id) ON DELETE CASCADE
);
