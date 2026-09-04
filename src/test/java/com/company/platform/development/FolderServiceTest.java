package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FolderServiceTest {
    @Test
    void nonEmptyFolderCannotBeDeleted() {
        PlatformStore store = new PlatformStore();
        DevelopmentService service = new DevelopmentService(store);
        long folderId = store.folders.keySet().iterator().next();
        assertThrows(BadRequestException.class, () -> service.deleteFolder(folderId));
    }
}
