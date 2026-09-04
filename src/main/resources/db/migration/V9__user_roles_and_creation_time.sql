ALTER TABLE platform_user
    ADD COLUMN role_code VARCHAR(64) NOT NULL DEFAULT 'USER';

UPDATE platform_user
SET role_code = 'ADMIN'
WHERE LOWER(username) = 'admin';
