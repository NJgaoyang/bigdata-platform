package com.company.platform.dashboard;

import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.datasource.DataSourceView;
import com.company.platform.integration.IntegrationInstanceView;
import com.company.platform.integration.IntegrationTableView;
import com.company.platform.integration.IntegrationTaskView;
import com.company.platform.lineage.LineageView;
import com.company.platform.scheduler.SchedulerGateway;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {
    @Test
    void aggregatesPersistedPlatformStateWithoutInventingExternalInstances() {
        PlatformStore store = new PlatformStore();
        store.dataSources.put(1L, new DataSourceView(1, "warehouse", DataSourceType.STARROCKS,
                "starrocks", 9030, "analytics", "reader", "ACTIVE", true, LocalDateTime.now(), "连接成功"));
        store.dataSources.put(2L, new DataSourceView(2, "legacy", DataSourceType.MYSQL,
                "mysql", 3306, "crm", "reader", "UNAVAILABLE", false, LocalDateTime.now(), "连接失败"));
        store.integrationTasks.put(3L, new IntegrationTaskView(3, "user_sync", "MYSQL", "STARROCKS", "FULL", "DRAFT", "{}"));
        store.integrationInstances.put(4L, new IntegrationInstanceView(4, 3, "execution-4", "SUCCESS",
                LocalDateTime.now(), LocalDateTime.now(), "completed"));
        store.lineages.put(5L, new LineageView(5, "ods_user", "dwd_user", "SQL", 1L, 1L));

        DashboardService service = new DashboardService(store, testGateway());
        var overview = service.overview();
        assertEquals(2, overview.sources().total());
        assertEquals(1, overview.sources().healthy());
        assertEquals(1, overview.tasks().success());
        assertEquals(2, service.assets().tables());
        assertTrue(overview.platform().schedulerAvailable());
        assertTrue(overview.trend().stream().mapToLong(DashboardService.TrendPoint::count).sum() >= 1);
    }

    @Test
    void assetSyncStatusIsCalculatedPerIntegrationTask() {
        PlatformStore store = new PlatformStore();
        store.integrationTaskTables.put(10L, List.of(new IntegrationTableView(101, 10, "src", "a", "ods", "a", "")));
        store.integrationTaskTables.put(20L, List.of(new IntegrationTableView(201, 20, "src", "b", "ods", "b", "")));
        store.integrationInstances.put(30L, new IntegrationInstanceView(30, 10, "ok", "FINISHED",
                LocalDateTime.now(), LocalDateTime.now(), "done"));
        DashboardService service = new DashboardService(store, testGateway());

        var assets = service.assets().items();
        assertEquals("已同步", assets.stream().filter(item -> item.name().equals("ods.a")).findFirst().orElseThrow().status());
        assertEquals("待同步", assets.stream().filter(item -> item.name().equals("ods.b")).findFirst().orElseThrow().status());
    }

    private static SchedulerGateway testGateway() {
        return new SchedulerGateway() {
            @Override public PublishResult publish(PublishRequest request) { throw new UnsupportedOperationException(); }
            @Override public RunResult run(String processCode) { throw new UnsupportedOperationException(); }
            @Override public InstanceStatus status(String instanceId) { throw new UnsupportedOperationException(); }
            @Override public void stop(String instanceId) { throw new UnsupportedOperationException(); }
            @Override public RunResult rerun(String instanceId) { throw new UnsupportedOperationException(); }
            @Override public RunResult backfill(String processCode, String start, String end, int parallelism) { throw new UnsupportedOperationException(); }
        };
    }
}
