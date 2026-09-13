package com.company.platform.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlinkCdcGatewayTest {
    @Test
    void metricsAggregatesJobAndVertexMetrics() throws Exception {
        FlinkRestClient rest = mock(FlinkRestClient.class);
        FlinkCdcGateway gateway = new FlinkCdcGateway(rest);
        ObjectMapper mapper = new ObjectMapper();
        String base = "http://flink:8081";
        String jobId = "abc";
        FlinkEnvironmentView env = new FlinkEnvironmentView(1L,"prod","FLINK_CDC","STANDALONE","SSH",base,"/flink","/cdc","/java","1.20.5","3.3.0","host",22,"root",true,true, LocalDateTime.now(),LocalDateTime.now());
        JsonNode job = mapper.readTree("{\"vertices\":[{\"id\":\"v1\",\"name\":\"source\",\"status\":\"RUNNING\"}]}");
        JsonNode jobMetrics = mapper.readTree("[{\"id\":\"uptime\",\"value\":\"123\"}]");
        JsonNode vertexMetrics = mapper.readTree("[{\"id\":\"0.numRecordsOut\",\"value\":\"7\"}]");
        when(rest.get(base,"/jobs/"+jobId)).thenReturn(job);
        when(rest.get(eq(base),contains("/jobs/"+jobId+"/metrics?get="))).thenReturn(jobMetrics);
        when(rest.get(eq(base),contains("/jobs/"+jobId+"/vertices/v1/metrics?get="))).thenReturn(vertexMetrics);
        JsonNode result = gateway.metrics(env,jobId);
        assertEquals("uptime",result.path("job").get(0).path("id").asText());
        assertEquals("source",result.path("vertices").get(0).path("name").asText());
        assertEquals("7",result.path("vertices").get(0).path("metrics").get(0).path("value").asText());
    }
}
