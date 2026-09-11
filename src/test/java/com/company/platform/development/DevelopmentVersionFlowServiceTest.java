package com.company.platform.development;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentVersionFlowServiceTest {
    private PlatformStore store;
    private DevelopmentService development;
    private DevelopmentVersionFlowService flow;
    private DevProjectView mine;
    private DevProjectView project;

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
        development = new DevelopmentService(store);
        DevelopmentAccessService access = new DevelopmentAccessService(store, development, new PlatformProperties());
        flow = new DevelopmentVersionFlowService(store, development, access);
        mine = development.createProject(new DevelopmentRequests.ProjectRequest("我的开发", "个人工作区"), "admin");
        project = development.createProject(new DevelopmentRequests.ProjectRequest("项目空间", "团队公共开发空间"), "admin");
    }

    @Test
    void preservesLogicalVersionFromDevelopmentThroughProjectAndOnline() {
        DevFileView source = development.createFile(new DevelopmentRequests.FileRequest(
                mine.id(), null, "orders.sql", "SQL", "select 1", ""), "admin");
        source = development.saveFile(source.id(), new DevelopmentRequests.SaveFileRequest("select 2"), "admin");
        source = development.saveFile(source.id(), new DevelopmentRequests.SaveFileRequest("select 3"), "admin");
        assertEquals(3, source.currentVersion());

        DevFileView pushed = flow.pushToProject(new DevelopmentVersionFlowRequests.PushToProjectRequest(
                source.id(), project.id(), null, source.name(), ""), "admin");
        assertEquals(3, pushed.currentVersion());
        assertEquals("PENDING_PUBLISH", pushed.status());
        DevelopmentVersionFlowService.DevelopmentVersionState pending = flow.state(pushed.id(), "admin");
        assertEquals(3, pending.developmentVersion());
        assertEquals(3, pending.pushedVersion());
        assertEquals(null, pending.onlineVersion());
        assertTrue(pending.pendingPublish());

        DevFileView online = flow.publish(pushed.id(), "admin");
        assertEquals(3, online.currentVersion());
        assertEquals("PUBLISHED", online.status());
        DevelopmentVersionFlowService.DevelopmentVersionState published = flow.state(source.id(), "admin");
        assertEquals(3, published.pushedVersion());
        assertEquals(3, published.onlineVersion());
        assertFalse(published.pendingPublish());

        source = development.saveFile(source.id(), new DevelopmentRequests.SaveFileRequest("select 4"), "admin");
        assertEquals(4, source.currentVersion());
        DevelopmentVersionFlowService.DevelopmentVersionState localChanged = flow.state(source.id(), "admin");
        assertTrue(localChanged.pendingPush());

        pushed = flow.pushToProject(new DevelopmentVersionFlowRequests.PushToProjectRequest(
                source.id(), project.id(), null, source.name(), ""), "admin");
        assertEquals(4, pushed.currentVersion());
        DevelopmentVersionFlowService.DevelopmentVersionState next = flow.state(pushed.id(), "admin");
        assertEquals(4, next.pushedVersion());
        assertEquals(3, next.onlineVersion());
        assertTrue(next.pendingPublish());
    }

    @Test
    void onlineVersionCanBeOpenedBackInPersonalDevelopmentWithoutGoingOffline() {
        DevFileView source = development.createFile(new DevelopmentRequests.FileRequest(
                mine.id(), null, "customer.sql", "SQL", "select 1", ""), "admin");
        DevFileView pushed = flow.pushToProject(new DevelopmentVersionFlowRequests.PushToProjectRequest(
                source.id(), project.id(), null, source.name(), ""), "admin");
        flow.publish(pushed.id(), "admin");

        DevFileView developmentCopy = flow.createDevelopmentVersion(pushed.id(), "admin");
        assertEquals(source.id(), developmentCopy.id());
        assertEquals(1, developmentCopy.currentVersion());
        assertEquals("DRAFT", developmentCopy.status());

        DevFileView next = development.saveFile(developmentCopy.id(), new DevelopmentRequests.SaveFileRequest("select 2"), "admin");
        assertEquals(2, next.currentVersion());
        DevelopmentVersionFlowService.DevelopmentVersionState state = flow.state(next.id(), "admin");
        assertEquals(1, state.onlineVersion());
        assertTrue(state.pendingPush());
    }
}
