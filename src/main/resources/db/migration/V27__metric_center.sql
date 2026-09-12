CREATE TABLE IF NOT EXISTS metric_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_code VARCHAR(128) NOT NULL UNIQUE,
    metric_name VARCHAR(255) NOT NULL,
    metric_type VARCHAR(32) NOT NULL,
    description VARCHAR(1000),
    business_domain VARCHAR(128),
    owner_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    current_version INT NOT NULL DEFAULT 1,
    source_datasource_id BIGINT,
    source_database VARCHAR(255), source_table VARCHAR(255), source_field VARCHAR(255),
    aggregation VARCHAR(64), filter_expression TEXT, time_field VARCHAR(255), expression_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS metric_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    definition_json LONGTEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL DEFAULT 'admin',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_metric_version (metric_id, version_no)
);
CREATE TABLE IF NOT EXISTS metric_dimension (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dimension_code VARCHAR(128) NOT NULL UNIQUE,
    dimension_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    source_datasource_id BIGINT, source_database VARCHAR(255), source_table VARCHAR(255), source_field VARCHAR(255),
    owner_name VARCHAR(128), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS metric_dimension_relation (
    metric_id BIGINT NOT NULL, dimension_id BIGINT NOT NULL,
    PRIMARY KEY (metric_id, dimension_id)
);
CREATE TABLE IF NOT EXISTS metric_lineage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_id BIGINT NOT NULL,
    upstream_type VARCHAR(32) NOT NULL,
    upstream_ref VARCHAR(512) NOT NULL,
    downstream_type VARCHAR(32), downstream_ref VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_metric_lineage_metric (metric_id)
);
