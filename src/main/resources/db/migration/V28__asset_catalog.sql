CREATE TABLE IF NOT EXISTS asset_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    asset_ref VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_asset_favorite (username, asset_type, asset_ref)
);
CREATE TABLE IF NOT EXISTS dataset_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dataset_code VARCHAR(128) NOT NULL UNIQUE,
    dataset_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    source_datasource_id BIGINT,
    source_database VARCHAR(255),
    source_table VARCHAR(255),
    owner_name VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
