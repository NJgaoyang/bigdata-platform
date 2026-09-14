CREATE TABLE IF NOT EXISTS integration_validation_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT,
    task_id BIGINT NOT NULL,
    execution_id VARCHAR(255),
    source_database VARCHAR(255) NOT NULL,
    source_table VARCHAR(255) NOT NULL,
    target_database VARCHAR(255) NOT NULL,
    target_table VARCHAR(255) NOT NULL,
    source_count BIGINT,
    target_count BIGINT,
    status VARCHAR(32) NOT NULL,
    detail VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_integration_validation_task (task_id, created_at),
    INDEX idx_integration_validation_batch (batch_id),
    CONSTRAINT fk_integration_validation_task FOREIGN KEY (task_id) REFERENCES integration_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_validation_batch FOREIGN KEY (batch_id) REFERENCES integration_batch(id) ON DELETE CASCADE
);
