package com.company.platform.metadata;

import com.company.platform.datasource.DataSourceAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MetadataControllerTest {
    @Test
    void tablePreviewRequiresDatasourceQueryPermission() {
        MetadataService service = mock(MetadataService.class);
        DataSourceAccessService access = mock(DataSourceAccessService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("platform.operator")).thenReturn("analyst");
        MetadataService.TablePreviewView preview = new MetadataService.TablePreviewView(1006L, "ods", "user_basic",
                List.of("id"), List.of(List.of("1")), 50);
        when(service.tablePreview(1006L, "ods", "user_basic", 50)).thenReturn(preview);
        MetadataController controller = new MetadataController(service, access);

        var result = controller.tablePreview(1006L, "ods", "user_basic", 50, request);

        assertEquals(preview, result.data());
        verify(access).requireQuery(1006L, "analyst");
        verify(service).tablePreview(1006L, "ods", "user_basic", 50);
    }
}
