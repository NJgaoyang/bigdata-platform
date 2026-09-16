CREATE TABLE IF NOT EXISTS alert_delivery_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT,
    channel_name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    message TEXT,
    delivery_status VARCHAR(16) NOT NULL,
    response_message TEXT,
    pushed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_delivery_pushed_at(pushed_at),
    INDEX idx_alert_delivery_task(task_name)
);
