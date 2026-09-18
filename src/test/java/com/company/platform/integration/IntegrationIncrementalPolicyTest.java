package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class IntegrationIncrementalPolicyTest {
    @Test void incrementalRequiresHalfOpenWindow() {
        IntegrationService service = new IntegrationService(new PlatformStore(), new SeaTunnelConfigBuilder(new ObjectMapper()),
                mock(SeaTunnelGateway.class), new ObjectMapper(), mock(PasswordCipher.class), null, null, null, null, null);
        var source = new IntegrationRequests.Endpoint("mysql",3306,"app","u","p","orders");
        var target = new IntegrationRequests.Endpoint("sr",9030,"ods","u","p","orders");
        var tables = List.of(new IntegrationRequests.TableRequest("app","orders","ods","orders",""));
        var request = new IntegrationRequests.TaskRequest("orders","MYSQL","STARROCKS","INCREMENTAL",source,target,List.of(),
                Map.of("where","updated_at > '2026-09-14 00:00:00'"),tables);
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("[start, end)"));
    }
}
