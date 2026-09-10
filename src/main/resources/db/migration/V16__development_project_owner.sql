ALTER TABLE dev_project
    ADD COLUMN owner_name VARCHAR(128) NOT NULL DEFAULT 'admin';

UPDATE dev_project
SET owner_name = 'admin'
WHERE owner_name IS NULL OR owner_name = '';
