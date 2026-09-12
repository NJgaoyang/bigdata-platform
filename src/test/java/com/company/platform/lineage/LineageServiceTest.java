package com.company.platform.lineage;

import com.company.platform.common.PlatformStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineageServiceTest {
    @Test
    void qualifiedMetadataNameFindsUnqualifiedParsedLineage() {
        PlatformStore store = new PlatformStore();
        store.lineages.clear();
        store.lineages.put(1L, new LineageView(1L, "orders", "ads.order_summary", "SQL", 10L, 11L));
        LineageService service = new LineageService(store, new SqlLineageParser());

        assertEquals(1, service.byTable("yzl_prd.orders").size());
        assertEquals(1, service.byTable("ADS.ORDER_SUMMARY").size());
    }
}
