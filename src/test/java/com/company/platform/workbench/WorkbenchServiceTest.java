package com.company.platform.workbench;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkbenchServiceTest {
    @Test
    void summaryUsesOnlyPersistedExecutionAndDraftFacts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(2,3,1, 4,5,6, 7,8,9, 1,2,3, 4);
        WorkbenchService.Summary result = new WorkbenchService(jdbc).summary();
        assertEquals(6, result.running());
        assertEquals(15, result.unpublished());
        assertEquals(24, result.successful24h());
        assertEquals(6, result.failed24h());
        assertEquals(4, result.unhealthySources());
        assertEquals(10, result.issues());
        assertEquals(80.0d, result.successRate24h(), 0.001d);
        assertNotNull(result.generatedAt());
    }
}
