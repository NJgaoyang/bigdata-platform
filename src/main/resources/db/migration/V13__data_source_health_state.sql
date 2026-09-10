ALTER TABLE data_source
    ADD COLUMN last_checked_at TIMESTAMP NULL AFTER metadata_visible,
    ADD COLUMN last_check_message VARCHAR(1000) NULL AFTER last_checked_at;

-- Earlier versions marked a source ACTIVE immediately after creation, without
-- opening a connection. Reset those unverified values once; a real connection
-- test will set ACTIVE or UNAVAILABLE and record when it was checked.
UPDATE data_source
SET status = 'UNKNOWN', last_checked_at = NULL, last_check_message = NULL;

CREATE UNIQUE INDEX uk_data_source_name ON data_source (name);
