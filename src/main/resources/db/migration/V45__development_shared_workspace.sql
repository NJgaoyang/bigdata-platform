ALTER TABLE dev_file
    ADD COLUMN owner_name VARCHAR(128) NOT NULL DEFAULT 'admin' AFTER ever_online;

UPDATE dev_file f
LEFT JOIN dev_project p ON p.id = f.project_id
SET f.owner_name = COALESCE(NULLIF(p.owner_name, ''), 'admin');

CREATE TABLE IF NOT EXISTS dev_file_recent (
    user_name VARCHAR(128) NOT NULL,
    file_id BIGINT NOT NULL,
    last_opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_name, file_id),
    KEY idx_dev_file_recent_user_time (user_name, last_opened_at),
    CONSTRAINT fk_dev_file_recent_file FOREIGN KEY (file_id) REFERENCES dev_file(id) ON DELETE CASCADE
);
