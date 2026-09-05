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
        assertTrue(controller.tasks().data().isEmpty());
        assertTrue(controller.failed().data().isEmpty());
    }
}
