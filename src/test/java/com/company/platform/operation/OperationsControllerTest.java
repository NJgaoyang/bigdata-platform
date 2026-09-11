package com.company.platform.operation;

import com.company.platform.scheduler.SchedulerGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationsControllerTest {
    @Test void emptySchedulerDoesNotGenerateDemoInstances() {
        SchedulerGateway gateway = mock(SchedulerGateway.class);
        when(gateway.listProcessInstances()).thenReturn(List.of());
        when(gateway.listTaskInstances()).thenReturn(List.of());
        OperationsController controller = new OperationsController(gateway);
        assertTrue(controller.processes().data().isEmpty());
        assertTrue(controller.tasks(null).data().isEmpty());
        assertTrue(controller.failed().data().isEmpty());
    }

    @Test void unavailableSchedulerReadEndpointsDegradeToEmptyResults() {
        SchedulerGateway gateway = mock(SchedulerGateway.class);
        when(gateway.listProcessInstances()).thenThrow(new IllegalStateException("DolphinScheduler 未配置认证"));
        when(gateway.listTaskInstances()).thenThrow(new IllegalStateException("DolphinScheduler 未配置认证"));
        OperationsController controller = new OperationsController(gateway);
        assertTrue(controller.processes().success());
        assertTrue(controller.processes().data().isEmpty());
        assertTrue(controller.tasks(null).success());
        assertTrue(controller.tasks(null).data().isEmpty());
        assertTrue(controller.failed().success());
        assertTrue(controller.failed().data().isEmpty());
    }
    @Test void taskInstancesCanBeScopedToOneProcessInstance() {
        SchedulerGateway gateway = mock(SchedulerGateway.class);
        when(gateway.listTaskInstances("17")).thenReturn(List.of(java.util.Map.of("id", "18", "processInstanceId", "17")));
        OperationsController controller = new OperationsController(gateway);
        assertEquals(1, controller.tasks("17").data().size());
        assertEquals("18", controller.tasks("17").data().get(0).get("id"));
    }

}
