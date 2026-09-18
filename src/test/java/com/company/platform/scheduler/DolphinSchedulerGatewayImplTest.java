package com.company.platform.scheduler;

import com.company.platform.config.DataSphereProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DolphinSchedulerGatewayImplTest {
    @Test
    void realModeProbeDoesNotRequireCredentials() {
        DataSphereProperties properties = new DataSphereProperties();
        properties.getScheduler().getDolphinscheduler().setRealEnabled(true);
        DolphinSchedulerGatewayImpl gateway = new DolphinSchedulerGatewayImpl(properties, new ObjectMapper());

        assertTrue(gateway.isRealMode());
        IllegalStateException error = assertThrows(IllegalStateException.class, gateway::listProcessInstances);
        assertTrue(error.getMessage().contains("密码") || error.getMessage().contains("Token"));
    }

    @Test
    void realModeProbeReportsDisabledWithoutThrowing() {
        DataSphereProperties properties = new DataSphereProperties();
        properties.getScheduler().getDolphinscheduler().setRealEnabled(false);
        DolphinSchedulerGatewayImpl gateway = new DolphinSchedulerGatewayImpl(properties, new ObjectMapper());

        assertFalse(gateway.isRealMode());
    }
}
