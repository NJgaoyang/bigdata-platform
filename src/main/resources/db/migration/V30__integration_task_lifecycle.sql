ALTER TABLE integration_task
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ONLINE' AFTER status;
