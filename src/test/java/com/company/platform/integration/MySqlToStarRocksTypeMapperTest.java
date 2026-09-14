package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MySqlToStarRocksTypeMapperTest {
    private final MySqlToStarRocksTypeMapper mapper = new MySqlToStarRocksTypeMapper();

    @Test void keepsTinyint1NumericAndExpandsUnsigned() {
        assertEquals("TINYINT", mapper.map("tinyint(1)"));
        assertEquals("SMALLINT", mapper.map("tinyint unsigned"));
        assertEquals("BIGINT", mapper.map("int unsigned"));
        assertEquals("LARGEINT", mapper.map("bigint unsigned"));
    }

    @Test void refusesSilentDecimalPrecisionLoss() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> mapper.map("decimal(50,20)"));
        assertTrue(ex.getMessage().contains("禁止静默截断"));
        assertEquals("DECIMAL(38,9)", mapper.map("decimal(38,9)"));
    }

    @Test void preservesTimeAsNonInstantText() {
        assertEquals("VARCHAR(32)", mapper.map("time(6)"));
    }
}
