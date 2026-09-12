package com.company.platform.development;

import com.company.platform.common.PlatformStore;
import com.company.platform.system.UserView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevelopmentProjectAccessTest {
    @Test
    void projectReadsRequireOwnershipOrExplicitProjectPermission() {
        PlatformStore store = new PlatformStore();
        store.projects.clear();
        store.folders.clear();
        store.files.clear();
        store.versions.clear();
        store.projects.put(10L, new DevProjectView(10L, "private-project", "", "ACTIVE", "alice"));
        store.users.put(20L, new UserView(20L, "bob", "Bob", "USER", "ACTIVE", LocalDateTime.now(), null));
        DevelopmentService service = new DevelopmentService(store);

        assertThrows(RuntimeException.class, () -> service.tree(10L, "bob"));
        store.projectPermissions.put("10:20:VIEW", "VIEW");
        assertEquals(10L, service.tree(10L, "bob").get("projectId"));
    }
}
