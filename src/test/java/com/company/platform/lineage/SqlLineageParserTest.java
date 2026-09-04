package com.company.platform.lineage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlLineageParserTest {
    @Test
    void extractsSourceAndTargetTables() {
        SqlLineageParser.ParseResult result = new SqlLineageParser().parse("insert into analytics.daily_sales select * from sales.orders join sales.customers c on 1=1");
        assertTrue(result.sources().contains("sales.orders"));
        assertTrue(result.sources().contains("sales.customers"));
        assertTrue(result.targets().contains("analytics.daily_sales"));
    }
}
