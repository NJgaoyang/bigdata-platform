CREATE TABLE IF NOT EXISTS metadata_table_owner (
    data_source_id BIGINT NOT NULL,
    database_name VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    owner_name VARCHAR(128),
    updated_by VARCHAR(128),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (data_source_id, database_name, table_name),
    CONSTRAINT fk_metadata_table_owner_source
        FOREIGN KEY (data_source_id) REFERENCES data_source(id) ON DELETE CASCADE
);
