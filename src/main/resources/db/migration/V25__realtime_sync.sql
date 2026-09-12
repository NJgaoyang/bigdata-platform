CREATE TABLE IF NOT EXISTS flink_environment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    engine_type VARCHAR(32) NOT NULL DEFAULT 'FLINK_CDC',
    deployment_mode VARCHAR(32) NOT NULL DEFAULT 'REMOTE',
    submitter_type VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
    rest_url VARCHAR(512),
    flink_home VARCHAR(512),
    flink_cdc_home VARCHAR(512),
    java_home VARCHAR(512),
    flink_version VARCHAR(64),
    flink_cdc_version VARCHAR(64),
    ssh_host VARCHAR(255), ssh_port INT DEFAULT 22, ssh_username VARCHAR(128), ssh_password_ciphertext VARCHAR(2000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_environment BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS realtime_sync_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    runtime_environment_id BIGINT,
    release_state VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    desired_state VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
    observed_state VARCHAR(32) NOT NULL DEFAULT 'STOPPED',
    definition_version INT NOT NULL DEFAULT 1,
    published_version INT,
    spec_json LONGTEXT NOT NULL,
    config_digest VARCHAR(64),
    last_error TEXT,
    created_by VARCHAR(128) NOT NULL DEFAULT 'admin',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS realtime_sync_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    spec_json LONGTEXT NOT NULL,
    config_digest VARCHAR(64),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(128) NOT NULL DEFAULT 'admin',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_realtime_version (job_id, version_no)
);
CREATE TABLE IF NOT EXISTS realtime_sync_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    definition_version INT NOT NULL,
    engine_job_id VARCHAR(128),
    runtime_revision VARCHAR(128),
    runtime_environment_snapshot TEXT,
    status VARCHAR(32) NOT NULL,
    result_uncertain BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_realtime_execution_job (job_id, created_at)
);
CREATE TABLE IF NOT EXISTS realtime_sync_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    execution_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    detail TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_realtime_event_job (job_id, created_at)
);
CREATE TABLE IF NOT EXISTS realtime_checkpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    execution_id BIGINT,
    checkpoint_id BIGINT,
    status VARCHAR(32),
    duration_ms BIGINT,
    state_size_bytes BIGINT,
    completed_at TIMESTAMP NULL,
    raw_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_realtime_checkpoint_job (job_id, created_at)
);
