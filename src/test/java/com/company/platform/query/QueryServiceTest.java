package com.company.platform.query;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryServiceTest {
    @Test void missingDatasourceNeverReturnsFabricatedSuccess() {
        PlatformStore store = mock(PlatformStore.class);
        QueryService service = new QueryService(new SqlSafetyChecker(), new PlatformProperties(),
                null, null, null, store);
        try {
            assertThrows(BadRequestException.class, () -> service.execute("select 1", false));
            assertThrows(BadRequestException.class, () -> service.submit("select 1", false, null, null));
            verifyNoInteractions(store);
        } finally { service.shutdown(); }
    }
}
