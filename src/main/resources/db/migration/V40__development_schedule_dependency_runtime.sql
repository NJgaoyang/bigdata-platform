ALTER TABLE dev_file_schedule_execution
    ADD COLUMN business_date DATE NULL AFTER schedule_version,
    ADD COLUMN attempt_no INT NOT NULL DEFAULT 0 AFTER business_date,
    ADD INDEX idx_dev_sched_exec_biz(file_id, business_date, status);
