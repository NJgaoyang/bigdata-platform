package com.company.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaTunnelConfigBuilderTest {
    private final SeaTunnelConfigBuilder builder = new SeaTunnelConfigBuilder(new ObjectMapper());

    @Test
    void buildsMysqlBatchToNativeStarrocksSink() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "yzl_prd", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "ods", "root", "secret", "orders");
        String config = builder.build(new IntegrationTask("orders", "MYSQL", "STARROCKS", "FULL", source, target, List.of(),
                Map.of("schemaSaveMode", "CREATE_SCHEMA_WHEN_NOT_EXIST", "dataSaveMode", "APPEND_DATA", "starrocksHttpPort", 8030)));

        assertTrue(config.contains("job.mode = \"BATCH\""));
        assertTrue(config.contains("Jdbc {"));
        assertTrue(config.contains("StarRocks {"));
        assertTrue(config.contains("nodeUrls = [\"starrocks:8030\"]"));
        assertTrue(config.contains("base-url = \"jdbc:mysql://starrocks:9030\""));
        assertTrue(config.contains("schema_save_mode = \"CREATE_SCHEMA_WHEN_NOT_EXIST\""));
        assertTrue(config.contains("data_save_mode = \"APPEND_DATA\""));
        assertTrue(config.contains("table = \"orders\""));
    }

    @Test
    void includesMappingsIncrementalFilterAndRuntimeOptions() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "yzl_prd", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "ods", "root", "secret", "orders");
        String config = builder.build(new IntegrationTask("orders", "MYSQL", "STARROCKS", "INCREMENTAL", source, target,
                List.of(new IntegrationRequests.FieldMapping("id", "order_id")),
                Map.of("where", "status = 1", "parallelism", 4, "batchSize", 2000)));

        assertTrue(config.contains("parallelism = 4"));
        assertTrue(config.contains("SELECT `id` AS `order_id`"));
        assertTrue(config.contains("WHERE status = 1"));
        assertTrue(config.contains("fetch_size = 2000"));
    }

    @Test
    void buildsOneBatchPipelineForEachSelectedTable() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "yzl_prd", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "ods", "root", "secret", "orders");
        List<IntegrationRequests.TableRequest> tables = List.of(
                new IntegrationRequests.TableRequest("yzl_prd", "orders", "ods", "orders", ""),
                new IntegrationRequests.TableRequest("yzl_prd", "customers", "ods", "customers", ""));
        String config = builder.build(new IntegrationTask("multi", "MYSQL", "STARROCKS", "FULL", source, target,
                List.of(), Map.of(), tables));

        assertTrue(config.contains("plugin_output = \"table_0\""));
        assertTrue(config.contains("plugin_output = \"table_1\""));
        assertTrue(config.contains("plugin_input = \"table_0\""));
        assertTrue(config.contains("plugin_input = \"table_1\""));
        assertTrue(config.contains("table = \"customers\""));
    }

    @Test
    void buildsMysqlCdcStreamingToStarrocks() {
        IntegrationRequests.Endpoint source = new IntegrationRequests.Endpoint("mysql", 3306, "yzl_prd", "root", "secret", "orders");
        IntegrationRequests.Endpoint target = new IntegrationRequests.Endpoint("starrocks", 9030, "ods", "root", "secret", "orders");
        List<IntegrationRequests.TableRequest> tables = List.of(
                new IntegrationRequests.TableRequest("yzl_prd", "orders", "ods", "orders", ""),
                new IntegrationRequests.TableRequest("yzl_prd", "customers", "ods", "customers", ""));
        String config = builder.build(new IntegrationTask("cdc", "MYSQL", "STARROCKS", "REALTIME", source, target,
                List.of(), Map.of("startupMode", "initial", "checkpointSeconds", 5, "serverId", "5656-5660"), tables));

        assertTrue(config.contains("job.mode = \"STREAMING\""));
        assertTrue(config.contains("checkpoint.interval = 5000"));
        assertTrue(config.contains("MySQL-CDC {"));
        assertTrue(config.contains("table-names = [\"yzl_prd.orders\", \"yzl_prd.customers\"]"));
        assertTrue(config.contains("startup.mode = \"initial\""));
        assertTrue(config.contains("server-id = \"5656-5660\""));
        assertTrue(config.contains("StarRocks {"));
        assertTrue(config.contains("table = \"${table_name}\""));
        assertTrue(config.contains("enable_upsert_delete = true"));
    }
}
