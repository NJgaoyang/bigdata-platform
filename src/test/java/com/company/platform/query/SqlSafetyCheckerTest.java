package com.company.platform.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSafetyCheckerTest {
    private final SqlSafetyChecker checker = new SqlSafetyChecker();
    @Test void allowsReadOnlyQuery() { assertTrue(checker.check("select * from orders").safe()); }
    @Test void blocksDrop() { assertFalse(checker.check("drop table orders").safe()); }
    @Test void blocksDeleteWithoutWhere() { assertFalse(checker.check("delete from orders").safe()); }
    @Test void blocksUpdateWithoutWhere() { assertFalse(checker.check("update orders set amount = 0").safe()); }
    @Test void blocksTruncate() { assertFalse(checker.check("truncate table orders").safe()); }
}
