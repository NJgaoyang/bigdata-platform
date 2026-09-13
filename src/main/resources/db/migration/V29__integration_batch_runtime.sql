CREATE TABLE IF NOT EXISTS integration_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    batch_code VARCHAR(64) NOT NULL UNIQUE,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    runtime_config_encrypted LONGTEXT NOT NULL,
    cluster_id BIGINT,
    parameters_json TEXT,
    source_batch_id BIGINT,
    created_by VARCHAR(128) NOT NULL DEFAULT 'platform',
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_integration_batch_task_created (task_id, created_at),
    INDEX idx_integration_batch_status (status),
    INDEX idx_integration_batch_source (source_batch_id),
    CONSTRAINT fk_integration_batch_task FOREIGN KEY (task_id)
        REFERENCES integration_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_batch_source FOREIGN KEY (source_batch_id)
        REFERENCES integration_batch(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS integration_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    execution_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (batch_id, attempt_no),
    INDEX idx_integration_attempt_execution (execution_id),
    CONSTRAINT fk_integration_attempt_batch FOREIGN KEY (batch_id)
        REFERENCES integration_batch(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_cursor (
    task_id BIGINT PRIMARY KEY,
    cursor_column VARCHAR(128),
    cursor_value VARCHAR(512),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_cursor_task FOREIGN KEY (task_id)
        REFERENCES integration_task(id) ON DELETE CASCADE
);
