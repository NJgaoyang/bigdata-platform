CREATE TABLE IF NOT EXISTS dev_file_schedule (
    file_id BIGINT PRIMARY KEY,
    current_version INT NOT NULL DEFAULT 0,
    published_version INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cycle_type VARCHAR(32) NOT NULL DEFAULT 'DAILY',
    execution_time VARCHAR(16) NOT NULL DEFAULT '02:00',
    cron_expression VARCHAR(128) NOT NULL DEFAULT '0 0 2 * * ?',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    data_source_id BIGINT,
    database_name VARCHAR(255),
    biz_date_param VARCHAR(64) NOT NULL DEFAULT '${system.biz.date-1}',
    retry_times INT NOT NULL DEFAULT 3,
    retry_interval_minutes INT NOT NULL DEFAULT 5,
    timeout_minutes INT NOT NULL DEFAULT 120,
    updated_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dev_schedule_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dev_file_schedule_dependency (
    file_id BIGINT NOT NULL,
    upstream_file_id BIGINT NOT NULL,
    PRIMARY KEY(file_id, upstream_file_id),
    CONSTRAINT fk_dev_sched_dep_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE,
    CONSTRAINT fk_dev_sched_dep_upstream FOREIGN KEY (upstream_file_id) REFERENCES dev_file(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dev_file_schedule_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    config_json TEXT NOT NULL,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, version_no),
    CONSTRAINT fk_dev_sched_ver_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dev_file_release_bundle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    release_no INT NOT NULL,
    sql_version INT NOT NULL,
    schedule_version INT NOT NULL DEFAULT 0,
    current_flag BOOLEAN NOT NULL DEFAULT TRUE,
    operator_name VARCHAR(128),
    remark VARCHAR(1000),
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, release_no),
    CONSTRAINT fk_dev_release_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dev_file_schedule_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    release_no INT NOT NULL DEFAULT 0,
    sql_version INT NOT NULL,
    schedule_version INT NOT NULL,
    execution_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    error_message TEXT,
    INDEX idx_dev_sched_exec_file(file_id, started_at),
    CONSTRAINT fk_dev_sched_exec_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE
);
