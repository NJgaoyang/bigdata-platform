package com.company.platform.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DolphinSchedulerProcessConverterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertsSeatunnelNodesAndEdgesToDsPayload() throws Exception {
        String snapshot = "{\"workflowCode\":\"wf_orders\",\"name\":\"订单同步\",\"description\":\"demo\",\"version\":2," +
                "\"nodes\":[" +
                "{\"id\":11,\"name\":\"同步\",\"type\":\"SEATUNNEL\",\"configJson\":\"\",\"x\":120,\"y\":80," +
                "\"contentBase64\":\"ZW52IHsgam9iLm1vZGUgPSBcIkJBVENIXCIgfQ==\"}," +
                "{\"id\":12,\"name\":\"完成\",\"type\":\"SHELL\",\"configJson\":\"\",\"x\":360,\"y\":80," +
                "\"contentBase64\":\"ZWNobyBvaw==\"}]," +
                "\"edges\":[{\"sourceNodeId\":11,\"targetNodeId\":12}]}";

        var payload = new DolphinSchedulerProcessConverter(mapper)
                .convert(snapshot, 22919517565792L, "bigdata", "admin");
        JsonNode tasks = mapper.readTree(payload.taskDefinitions());
        JsonNode relations = mapper.readTree(payload.relations());

        assertEquals(2, tasks.size());
        assertEquals("SEATUNNEL", tasks.get(0).get("taskType").asText());
        assertEquals("env { job.mode = \\\"BATCH\\\" }", tasks.get(0).get("taskParams").get("rawScript").asText());
        assertEquals("SHELL", tasks.get(1).get("taskType").asText());
        assertEquals(2, relations.size());
        assertTrue(relations.toString().contains("\"preTaskCode\":0"));
        assertTrue(payload.locations().contains("\"x\":120"));
    }
}
