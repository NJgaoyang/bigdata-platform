CREATE TABLE IF NOT EXISTS query_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_id VARCHAR(64) NOT NULL UNIQUE,
    datasource_id BIGINT,
    database_name VARCHAR(255),
    sql_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    elapsed_ms BIGINT,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS workflow_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    node_code VARCHAR(64) NOT NULL,
    node_name VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    dev_file_id BIGINT,
    file_version_id BIGINT,
    config_json TEXT,
    x INT,
    y INT,
    timeout_seconds INT,
    retry_times INT DEFAULT 0,
    ds_task_code VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workflow_id, node_code)
);

CREATE TABLE IF NOT EXISTS workflow_edge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    source_node_id BIGINT NOT NULL,
    target_node_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workflow_id, source_node_id, target_node_id)
);

CREATE TABLE IF NOT EXISTS schedule_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    failure_strategy VARCHAR(32) DEFAULT 'END',
    worker_group VARCHAR(128),
    alert_group VARCHAR(128),
    ds_schedule_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS integration_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    execution_id VARCHAR(128),
    ds_process_instance_code VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    elapsed_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    UNIQUE (role_id, permission_code)
);

CREATE TABLE IF NOT EXISTS alert_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
