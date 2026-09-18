CREATE TABLE IF NOT EXISTS metric_domain (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_code VARCHAR(128) NOT NULL UNIQUE,
    domain_name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000),
    owner_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS metric_theme (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id BIGINT NOT NULL,
    theme_code VARCHAR(128) NOT NULL UNIQUE,
    theme_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    owner_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_metric_theme_domain (domain_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

ALTER TABLE metric_domain CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER TABLE metric_theme CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='metric_definition' AND column_name='domain_id')=0,
              'ALTER TABLE metric_definition ADD COLUMN domain_id BIGINT NULL AFTER business_domain', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='metric_definition' AND column_name='theme_id')=0,
              'ALTER TABLE metric_definition ADD COLUMN theme_id BIGINT NULL AFTER domain_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='metric_definition' AND index_name='idx_metric_definition_domain')=0,
              'ALTER TABLE metric_definition ADD INDEX idx_metric_definition_domain (domain_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='metric_definition' AND index_name='idx_metric_definition_theme')=0,
              'ALTER TABLE metric_definition ADD INDEX idx_metric_definition_theme (theme_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO metric_domain(domain_code,domain_name,description,status,sort_order)
SELECT CONCAT('DOMAIN_',UPPER(SUBSTRING(MD5(TRIM(business_domain)),1,12))),TRIM(business_domain),'由历史指标自动迁移','ENABLED',10
FROM metric_definition
WHERE business_domain IS NOT NULL AND TRIM(business_domain)<>''
GROUP BY TRIM(business_domain)
ON DUPLICATE KEY UPDATE domain_name=VALUES(domain_name);

UPDATE metric_definition m
JOIN metric_domain d ON d.domain_name COLLATE utf8mb4_general_ci = m.business_domain COLLATE utf8mb4_general_ci
SET m.domain_id=d.id
WHERE m.domain_id IS NULL;
