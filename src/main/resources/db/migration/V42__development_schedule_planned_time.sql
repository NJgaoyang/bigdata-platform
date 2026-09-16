ALTER TABLE dev_file_schedule_execution
    ADD COLUMN planned_at TIMESTAMP NULL AFTER status;

CREATE INDEX idx_dev_sched_exec_planned_at
    ON dev_file_schedule_execution(file_id, planned_at);
