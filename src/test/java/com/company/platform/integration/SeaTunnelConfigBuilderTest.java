package com.company.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaTunnelConfigBuilderTest {
    @Test
    void buildsMysqlToStarrocksConfig() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "ods", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "dw", "root", "secret", "orders");
        String config = new SeaTunnelConfigBuilder(new ObjectMapper()).build(new IntegrationTask("orders", "MYSQL", "STARROCKS", "FULL", source, target, List.of(), null));
        assertTrue(config.contains("Jdbc"));
        assertTrue(config.contains("StarRocks"));
        assertTrue(config.contains("orders"));
    }

    @Test
    void includesMappingsFiltersAndRuntimeOptions() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "ods", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "dw", "root", "secret", "orders");
        String config = new SeaTunnelConfigBuilder(new ObjectMapper()).build(new IntegrationTask("orders", "MYSQL", "STARROCKS", "FULL", source, target,
                List.of(new IntegrationRequests.FieldMapping("id", "order_id")),
                Map.of("where", "status = 1", "parallelism", 4, "batchSize", 2000)));
        assertTrue(config.contains("parallelism = 4"));
        assertTrue(config.contains("SELECT id AS order_id"));
        assertTrue(config.contains("status = 1"));
        assertTrue(config.contains("fetch_size = 2000"));
    }

    @Test
    void buildsOneInputAndOneOutputForEachTable() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "ods", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "dw", "root", "secret", "orders");
        List<IntegrationRequests.TableRequest> tables = List.of(
                new IntegrationRequests.TableRequest("ods", "orders", "dw", "orders", ""),
                new IntegrationRequests.TableRequest("ods", "customers", "dw", "customers", ""));
        String config = new SeaTunnelConfigBuilder(new ObjectMapper()).build(new IntegrationTask("multi", "MYSQL", "STARROCKS", "FULL", source, target,
                List.of(), Map.of(), tables));
        assertTrue(config.contains("plugin_output = \"table_0\""));
        assertTrue(config.contains("plugin_output = \"table_1\""));
        assertTrue(config.contains("plugin_input = \"table_0\""));
        assertTrue(config.contains("plugin_input = \"table_1\""));
        assertTrue(config.contains("customers"));
        assertTrue(config.contains("table = \"customers\""));
    }
}
