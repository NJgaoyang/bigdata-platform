CREATE TABLE IF NOT EXISTS release_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_key VARCHAR(64) NOT NULL UNIQUE,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by VARCHAR(128) NOT NULL DEFAULT 'admin',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
INSERT INTO release_policy (policy_key, approval_required) VALUES ('production', FALSE)
ON DUPLICATE KEY UPDATE policy_key=VALUES(policy_key);
CREATE TABLE IF NOT EXISTS release_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_name VARCHAR(255),
    requested_version INT,
    payload_json LONGTEXT,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    reviewed_by VARCHAR(128),
    review_comment VARCHAR(1000),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    INDEX idx_release_request_status (status, requested_at)
);
CREATE TABLE IF NOT EXISTS release_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_name VARCHAR(255),
    released_version INT,
    result_status VARCHAR(32) NOT NULL,
    detail TEXT,
    operator_name VARCHAR(128) NOT NULL,
    released_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_release_record_resource (resource_type, resource_id, released_at)
);
