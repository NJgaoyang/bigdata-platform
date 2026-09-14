package com.company.platform.integration;

import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class IntegrationStagingServiceTest {
    @Test void fullOverwriteIsRedirectedToUniqueStagingTables() {
        IntegrationStagingService service = new IntegrationStagingService(mock(PlatformStore.class), new ObjectMapper(), mock(PasswordCipher.class));
        var source = new IntegrationRequests.Endpoint("mysql",3306,"app","u","p",null);
        var target = new IntegrationRequests.Endpoint("sr",9030,"ods","u","p",null);
        var tables = List.of(new IntegrationRequests.TableRequest("app","orders","ods","orders",""));
        IntegrationTask task = new IntegrationTask("orders","MYSQL","STARROCKS","FULL",source,target,List.of(),Map.of("targetPolicy","FULL_OVERWRITE"),tables);
        assertTrue(service.requiresStaging(task));
        var prepared = service.prepare(task);
        assertEquals(1, prepared.mappings().size());
        assertEquals("orders", prepared.mappings().getFirst().officialTable());
        assertTrue(prepared.mappings().getFirst().stagingTable().startsWith("orders__ds_stage_"));
        assertEquals(prepared.mappings().getFirst().stagingTable(), prepared.task().tables().getFirst().targetTable());
        assertEquals("DROP_DATA", prepared.task().options().get("dataSaveMode"));
        assertEquals("CREATE_SCHEMA_WHEN_NOT_EXIST", prepared.task().options().get("schemaSaveMode"));
        assertEquals(prepared.mappings(), service.stageTables(service.parametersJson(prepared.mappings(), "FULL_OVERWRITE")));
    }
}
