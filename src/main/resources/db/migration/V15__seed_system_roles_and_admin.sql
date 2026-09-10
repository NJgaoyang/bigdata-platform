INSERT INTO platform_role (role_code, role_name)
VALUES ('ADMIN', '管理员'), ('USER', '普通用户')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO platform_user (id, username, display_name, role_code, status, created_at, password_hash)
VALUES (1, 'admin', '平台管理员', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, NULL)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), role_code = VALUES(role_code), status = VALUES(status);
