CREATE TABLE IF NOT EXISTS dev_file_delivery (
    project_file_id BIGINT NOT NULL PRIMARY KEY,
    source_file_id BIGINT NOT NULL,
    pushed_version_no INT NOT NULL,
    online_version_no INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dev_file_delivery_source (source_file_id)
);
