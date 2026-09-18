package com.company.platform.integration;

import com.company.platform.cluster.SeaTunnelClusterView;
import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntegrationBatchRuntimeTest {
    @Test
    void failedSubmissionStillRecordsBatchAndAttempt() {
        PlatformStore store = new PlatformStore();
        store.integrationTasks.put(7L, new IntegrationTaskView(
                7L, "orders", "MYSQL", "STARROCKS", "FULL", "GENERATED", "env {}"));

        SeaTunnelConfigBuilder builder = mock(SeaTunnelConfigBuilder.class);
        SeaTunnelGateway gateway = mock(SeaTunnelGateway.class);
        IntegrationRuntimeRepository runtime = mock(IntegrationRuntimeRepository.class);
        IntegrationBatchView batch = new IntegrationBatchView(
                99L, 7L, "B99", "MANUAL", "QUEUED", null, "{}", null,
                "platform", null, null, null, LocalDateTime.now());
        when(runtime.createBatch(eq(7L), eq("MANUAL"), anyString(), isNull(), eq("{}"), isNull(), eq("platform")))
                .thenReturn(batch);
        when(gateway.validate(anyString(), isNull()))
                .thenReturn(new SeaTunnelGateway.ValidationResult(false, "SeaTunnel 真实执行未启用"));

        IntegrationService service = new IntegrationService(
                store, builder, gateway, new ObjectMapper(), mock(PasswordCipher.class),
                mock(DataSourceService.class), null, runtime, null, null);

        assertThrows(BadRequestException.class, () -> service.execute(7L));
        verify(runtime).createBatch(eq(7L), eq("MANUAL"), anyString(), isNull(), eq("{}"), isNull(), eq("platform"));
        verify(runtime).recordAttempt(99L, null, "FAILED", "SeaTunnel 真实执行未启用");
    }
    @Test
    void defaultSeaTunnelClusterPrefersHealthyRuntime() {
        PlatformStore store = new PlatformStore();
        store.seaTunnelClusters.put(11L, new SeaTunnelClusterView(11L, "new-unknown", "host-a", 5801,
                "root", 22, "/seatunnel", null, "UNKNOWN", LocalDateTime.now()));
        store.seaTunnelClusters.put(7L, new SeaTunnelClusterView(7L, "healthy", "host-b", 5801,
                "root", 22, "/seatunnel", null, "HEALTHY", LocalDateTime.now().minusDays(1)));
        IntegrationService service = new IntegrationService(
                store, mock(SeaTunnelConfigBuilder.class), mock(SeaTunnelGateway.class), new ObjectMapper(),
                mock(PasswordCipher.class), mock(DataSourceService.class), null, mock(IntegrationRuntimeRepository.class), null, null);

        assertEquals(7L, service.runtimeClusterId());
    }

    @Test
    void offlineTaskCannotRunAndOnlineTaskCannotDelete() {
        PlatformStore store = new PlatformStore();
        IntegrationTaskView offline = new IntegrationTaskView(8L, "offline-orders", "MYSQL", "STARROCKS",
                "FULL", "GENERATED", "OFFLINE", "{}", "{}", "{}", "env {}", List.of());
        IntegrationTaskView online = new IntegrationTaskView(9L, "online-orders", "MYSQL", "STARROCKS",
                "FULL", "GENERATED", "ONLINE", "{}", "{}", "{}", "env {}", List.of());
        store.integrationTasks.put(8L, offline);
        store.integrationTasks.put(9L, online);

        SeaTunnelGateway gateway = mock(SeaTunnelGateway.class);
        IntegrationService service = new IntegrationService(
                store, mock(SeaTunnelConfigBuilder.class), gateway, new ObjectMapper(),
                mock(PasswordCipher.class), mock(DataSourceService.class), null, mock(IntegrationRuntimeRepository.class), null, null);

        assertThrows(BadRequestException.class, () -> service.execute(8L));
        assertThrows(BadRequestException.class, () -> service.backfill(8L, new IntegrationRequests.BackfillRequest("id > 0", null, null)));
        assertThrows(BadRequestException.class, () -> service.delete(9L));
        verifyNoInteractions(gateway);
    }

}
