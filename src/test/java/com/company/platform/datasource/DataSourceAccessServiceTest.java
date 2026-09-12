package com.company.platform.datasource;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.system.UserView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceAccessServiceTest {
    @Test
    void requiresExplicitResourcePermissionForNonAdminUsers() {
        PlatformStore store = new PlatformStore();
        store.dataSources.put(10L, new DataSourceView(10, "warehouse", DataSourceType.STARROCKS, "host", 9030,
                "ods", "reader", "ACTIVE", true, LocalDateTime.now(), "ok"));
        store.users.put(20L, new UserView(20, "bob", "Bob", "USER", "ACTIVE", LocalDateTime.now(), null));
        DataSourceAccessService service = new DataSourceAccessService(store, new PlatformProperties());
        assertFalse(service.canAccess(10L, "bob", DataSourceAccessService.Access.VIEW));
        store.datasourcePermissions.put("10:20:QUERY", "QUERY");
        assertTrue(service.canAccess(10L, "bob", DataSourceAccessService.Access.VIEW));
        assertTrue(service.canAccess(10L, "bob", DataSourceAccessService.Access.QUERY));
        assertFalse(service.canAccess(10L, "bob", DataSourceAccessService.Access.EDIT));
        assertTrue(service.canAccess(10L, "admin", DataSourceAccessService.Access.EDIT));
    }
}
