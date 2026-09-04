CREATE TABLE seatunnel_cluster (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 5801,
    ssh_username VARCHAR(128),
    ssh_port INT NOT NULL DEFAULT 22,
    ssh_password_encrypted TEXT,
    seatunnel_home VARCHAR(512) NOT NULL,
    description VARCHAR(512),
    health_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
