CREATE TABLE IF NOT EXISTS project_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, user_id, permission_code)
);

CREATE TABLE IF NOT EXISTS datasource_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (datasource_id, user_id, permission_code)
);

CREATE TABLE IF NOT EXISTS operation_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_id BIGINT,
    detail TEXT,
    operator_name VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
