CREATE TABLE IF NOT EXISTS realtime_schema_change (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    execution_id BIGINT,
    source_table VARCHAR(255) NOT NULL,
    change_type VARCHAR(64) NOT NULL,
    ddl_text TEXT,
    policy_action VARCHAR(128),
    target_result VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'DETECTED',
    detail TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rt_schema_change_job (job_id, occurred_at)
);

CREATE TABLE IF NOT EXISTS realtime_validation_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    execution_id BIGINT,
    validation_type VARCHAR(64) NOT NULL,
    source_table VARCHAR(255),
    source_value VARCHAR(255),
    target_value VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    detail TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rt_validation_job (job_id, checked_at)
);

CREATE TABLE IF NOT EXISTS realtime_schema_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    source_table VARCHAR(255) NOT NULL,
    schema_json LONGTEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rt_schema_snapshot (job_id, source_table)
);
