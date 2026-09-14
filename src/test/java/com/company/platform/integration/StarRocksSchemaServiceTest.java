package com.company.platform.integration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StarRocksSchemaServiceTest {
    private final StarRocksSchemaService service = new StarRocksSchemaService();

    @Test
    void createTableUsesSingleReplicaByDefault() {
        String ddl = service.createTableDdl("ods", "orders",
                List.of(new StarRocksSchemaService.SourceColumn(
                        "id", "bigint", "BIGINT", true, false, "")), Map.of());
        assertTrue(ddl.contains("PRIMARY KEY(`id`)"));
        assertTrue(ddl.contains("PROPERTIES (\"replication_num\" = \"1\")"));
    }

    @Test
    void createTableAllowsConfiguredReplicaCount() {
        String ddl = service.createTableDdl("ods", "orders",
                List.of(new StarRocksSchemaService.SourceColumn(
                        "id", "bigint", "BIGINT", true, false, "")),
                Map.of("starrocksReplicationNum", 3));
        assertTrue(ddl.contains("PROPERTIES (\"replication_num\" = \"3\")"));
    }
}
