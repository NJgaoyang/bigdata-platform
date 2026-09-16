ALTER TABLE dev_file
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' AFTER status,
    ADD COLUMN ever_online BOOLEAN NOT NULL DEFAULT FALSE AFTER lifecycle_status;

UPDATE dev_file f
SET lifecycle_status='ONLINE', ever_online=TRUE
WHERE f.status='PUBLISHED'
   OR EXISTS (SELECT 1 FROM dev_file_release_bundle b WHERE b.file_id=f.id AND b.current_flag=TRUE);
