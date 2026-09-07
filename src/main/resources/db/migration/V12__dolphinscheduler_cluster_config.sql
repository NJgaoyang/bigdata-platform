CREATE TABLE dolphinscheduler_cluster (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 12345,
    base_path VARCHAR(255) NOT NULL DEFAULT '/dolphinscheduler',
    version VARCHAR(32) NOT NULL DEFAULT '3.1.9',
    username VARCHAR(128),
    password_encrypted TEXT,
    install_dir VARCHAR(512),
    description VARCHAR(512),
    health_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
