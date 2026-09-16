ALTER TABLE dev_file
    ADD COLUMN recycled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN recycled_at TIMESTAMP NULL,
    ADD COLUMN recycled_by VARCHAR(128) NULL;

CREATE INDEX idx_dev_file_recycled_project ON dev_file(project_id, recycled, recycled_at);
