ALTER TABLE integration_task
    ADD COLUMN created_by VARCHAR(128) NOT NULL DEFAULT 'platform' AFTER lifecycle_status;
