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
);

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
);

ALTER TABLE metric_definition ADD COLUMN domain_id BIGINT NULL AFTER business_domain;
ALTER TABLE metric_definition ADD COLUMN theme_id BIGINT NULL AFTER domain_id;
ALTER TABLE metric_definition ADD INDEX idx_metric_definition_domain (domain_id);
ALTER TABLE metric_definition ADD INDEX idx_metric_definition_theme (theme_id);

INSERT INTO metric_domain(domain_code,domain_name,description,status,sort_order)
SELECT CONCAT('DOMAIN_',UPPER(SUBSTRING(MD5(TRIM(business_domain)),1,12))),TRIM(business_domain),'由历史指标自动迁移','ENABLED',10
FROM metric_definition
WHERE business_domain IS NOT NULL AND TRIM(business_domain)<>''
GROUP BY TRIM(business_domain)
ON DUPLICATE KEY UPDATE domain_name=VALUES(domain_name);

UPDATE metric_definition m
JOIN metric_domain d ON d.domain_name=m.business_domain
SET m.domain_id=d.id
WHERE m.domain_id IS NULL;
