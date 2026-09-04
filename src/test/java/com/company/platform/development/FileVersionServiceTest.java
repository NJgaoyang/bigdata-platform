package com.company.platform.development;

import com.company.platform.common.PlatformStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileVersionServiceTest {
    @Test
    void savingFileCreatesImmutableVersion() {
        PlatformStore store = new PlatformStore();
        DevelopmentService service = new DevelopmentService(store);
        long fileId = store.files.keySet().iterator().next();
        String original = service.getFile(fileId).content();
        service.saveFile(fileId, new DevelopmentRequests.SaveFileRequest("SELECT 2"));
        assertEquals(2, service.versions(fileId).size());
        assertEquals(original, service.versions(fileId).get(1).content());
    }
}
