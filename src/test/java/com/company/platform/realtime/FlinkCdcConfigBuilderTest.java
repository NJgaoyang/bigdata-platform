package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DataSourceView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlinkCdcConfigBuilderTest {
    private final DataSourceService dataSources = mock(DataSourceService.class);
    private final FlinkCdcConfigBuilder builder = new FlinkCdcConfigBuilder(dataSources);

    @Test
    void yamlModeMasksPasswordsAndValidatesRequiredSections() {
        String yaml = "source:\n  type: mysql\n  password: source-secret\n"
                + "sink:\n  type: starrocks\n  password: sink-secret\n"
                + "pipeline:\n  name: orders\n";
        Map<String,Object> spec = Map.of("editorMode", "YAML", "yaml", yaml);
        builder.validate(spec);
        String masked = builder.build(spec, true);
        assertFalse(masked.contains("source-secret"));
        assertFalse(masked.contains("sink-secret"));
        assertEquals(2, masked.split("'\\*\\*\\*'", -1).length - 1);
    }

    @Test
    void guideModeUsesStarRocksPipeline330SupportedSinkOptions() {
        when(dataSources.connectionInfo(1L)).thenReturn(new DataSourceService.ConnectionInfo(1L, DataSourceType.MYSQL, "jdbc:mysql://src", "src", "secret", "app"));
        when(dataSources.connectionInfo(2L)).thenReturn(new DataSourceService.ConnectionInfo(2L, DataSourceType.STARROCKS, "jdbc:mysql://sink", "sink", "secret", "ods"));
        when(dataSources.get(1L)).thenReturn(new DataSourceView(1L,"mysql",DataSourceType.MYSQL,"10.0.0.1",3306,"app","src","ACTIVE",true,null,null));
        when(dataSources.get(2L)).thenReturn(new DataSourceView(2L,"starrocks",DataSourceType.STARROCKS,"10.0.0.2",9030,"ods","sink","ACTIVE",true,null,null));
        Map<String,Object> spec = Map.of(
                "sourceDataSourceId",1L,"sinkDataSourceId",2L,"sourceDatabase","app","sinkDatabase","ods",
                "tables", List.of(Map.of("sourceTable","orders","targetTable","orders")),
                "sink", Map.of("maxBytes",1024L,"flushIntervalMs",1500,"transactionStreamLoad",true));
        String yaml = builder.build(spec, true);
        assertTrue(yaml.contains("sink.buffer-flush.max-bytes: 67108864"));
        assertTrue(yaml.contains("sink.buffer-flush.interval-ms: 1500"));
        assertTrue(yaml.contains("sink.semantic: 'exactly-once'"));
        assertTrue(yaml.contains("sink.version: 'V2'"));
        assertTrue(yaml.contains("sink.at-least-once.use-transaction-stream-load: false"));
        assertTrue(yaml.contains("sink.properties.max_filter_ratio: '0'"));
        assertTrue(yaml.contains("sink.properties.strict_mode: 'true'"));
        assertTrue(yaml.contains("scan.incremental.snapshot.backfill.skip: false"));
        assertTrue(yaml.contains("treat-tinyint1-as-boolean.enabled: false"));
        assertTrue(yaml.contains("debezium.bigint.unsigned.handling.mode: 'precise'"));
        assertFalse(yaml.contains("sink.buffer-flush.max-rows"));
        assertFalse(yaml.contains("sink.max-retries"));
    }

    @Test
    void yamlModeRejectsIncompletePipeline() {
        Map<String,Object> spec = Map.of("editorMode", "YAML", "yaml", "source:\n  type: mysql\n");
        assertThrows(BadRequestException.class, () -> builder.validate(spec));
    }
}
