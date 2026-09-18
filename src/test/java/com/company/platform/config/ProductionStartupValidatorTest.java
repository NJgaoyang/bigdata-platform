package com.company.platform.config;

import com.company.platform.datasource.PasswordCipher;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionStartupValidatorTest {
    @Test
    void acceptsPersistedPbkdf2AdminCredentialWithLocalScheduler() {
        DataSphereProperties properties = new DataSphereProperties();
        properties.getSecurity().setEnabled(true);
        properties.getSeatunnel().setRealEnabled(true);
        properties.getScheduler().setType("local");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("platform_user"), eq(Integer.class), any())).thenReturn(1);
        PasswordCipher cipher = mock(PasswordCipher.class);
        when(cipher.masterKeyConfigured()).thenReturn(true);
        assertDoesNotThrow(() -> new ProductionStartupValidator(properties, jdbc, cipher).validate());
    }
}
