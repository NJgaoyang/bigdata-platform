package com.company.platform.development;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.system.UserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentAccessServiceTest {
    private PlatformStore store;
    private DevelopmentService development;
    private DevelopmentAccessService access;

    @BeforeEach
    void setUp() {
        store = new PlatformStore();
        store.projects.clear();
        store.folders.clear();
        store.files.clear();
        store.versions.clear();
        store.users.clear();
        store.userPermissions.clear();
        store.projectPermissions.clear();
        store.users.put(21L, new UserView(21L, "alice", "Alice", "USER", "ACTIVE", LocalDateTime.now(), null));
        store.userPermissions.put(21L, Set.of("DATA_DEVELOPMENT_VIEW", "DATA_DEVELOPMENT_EDIT"));
        development = new DevelopmentService(store);
        access = new DevelopmentAccessService(store, development, new PlatformProperties());
    }

    @Test
    void viewOnlyMemberCannotEditProject() {
        store.projects.put(10L, new DevProjectView(10L, "项目空间", "", "ACTIVE", "alice"));
        store.users.put(20L, new UserView(20L, "bob", "Bob", "USER", "ACTIVE", LocalDateTime.now(), null));
        store.userPermissions.put(20L, Set.of("DATA_DEVELOPMENT_VIEW"));
        store.projectPermissions.put("10:20:VIEW", "VIEW");

        DevelopmentAccessService.ProjectAccess result = access.projectAccess(10L, "bob");
        assertTrue(result.view());
        assertFalse(result.edit());
    }

    @Test
    void editMemberWithModuleEditCanEditProject() {
        store.projects.put(10L, new DevProjectView(10L, "项目空间", "", "ACTIVE", "alice"));
        store.users.put(20L, new UserView(20L, "bob", "Bob", "USER", "ACTIVE", LocalDateTime.now(), null));
        store.userPermissions.put(20L, Set.of("DATA_DEVELOPMENT_VIEW", "DATA_DEVELOPMENT_EDIT"));
        store.projectPermissions.put("10:20:EDIT", "EDIT");

        assertTrue(access.projectAccess(10L, "bob").edit());
    }

    @Test
    void saveToProjectReusesSameFileAndCreatesNextVersion() {
        store.projects.put(10L, new DevProjectView(10L, "项目空间", "", "ACTIVE", "alice"));
        DevelopmentAccessRequests.SaveToProjectRequest first = new DevelopmentAccessRequests.SaveToProjectRequest(
                10L, null, "orders.sql", "SQL", "select 1", "v1");
        DevelopmentAccessRequests.SaveToProjectRequest second = new DevelopmentAccessRequests.SaveToProjectRequest(
                10L, null, "orders.sql", "SQL", "select 2", "v2");

        DevFileView v1 = access.saveToProject(first, "alice");
        DevFileView v2 = access.saveToProject(second, "alice");

        assertEquals(v1.id(), v2.id());
        assertEquals(2, v2.currentVersion());
        assertEquals(1, store.files.size());
        assertEquals(2, store.versions.values().stream().filter(version -> version.fileId() == v2.id()).count());
        assertEquals("select 2", v2.content());
    }

    @Test
    void sameNameInDifferentFolderRemainsDifferentFiles() {
        store.projects.put(10L, new DevProjectView(10L, "项目空间", "", "ACTIVE", "alice"));
        store.folders.put(30L, new DevFolderView(30L, 10L, null, "A", LocalDateTime.now()));
        store.folders.put(31L, new DevFolderView(31L, 10L, null, "B", LocalDateTime.now()));

        DevFileView first = access.saveToProject(new DevelopmentAccessRequests.SaveToProjectRequest(
                10L, 30L, "orders.sql", "SQL", "select 1", ""), "alice");
        DevFileView second = access.saveToProject(new DevelopmentAccessRequests.SaveToProjectRequest(
                10L, 31L, "orders.sql", "SQL", "select 2", ""), "alice");

        assertFalse(first.id() == second.id());
        assertEquals(2, store.files.size());
    }
}
