package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FlinkCdcConfigBuilderTest {
    private final FlinkCdcConfigBuilder builder = new FlinkCdcConfigBuilder(mock(DataSourceService.class));

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
    void yamlModeRejectsIncompletePipeline() {
        Map<String,Object> spec = Map.of("editorMode", "YAML", "yaml", "source:\n  type: mysql\n");
        assertThrows(BadRequestException.class, () -> builder.validate(spec));
    }
}
