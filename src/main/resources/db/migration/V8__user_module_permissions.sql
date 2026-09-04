CREATE TABLE IF NOT EXISTS user_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, permission_code),
    CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES platform_user(id) ON DELETE CASCADE
);
