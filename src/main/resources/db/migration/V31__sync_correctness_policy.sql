ALTER TABLE data_source
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER database_name;

CREATE TABLE IF NOT EXISTS cdc_server_id_allocation (
    job_id BIGINT PRIMARY KEY,
    start_id INT NOT NULL,
    end_id INT NOT NULL,
    parallelism INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_cdc_server_id_job FOREIGN KEY (job_id) REFERENCES realtime_sync_definition(id) ON DELETE CASCADE,
    UNIQUE KEY uk_cdc_server_id_start (start_id),
    UNIQUE KEY uk_cdc_server_id_end (end_id)
);
