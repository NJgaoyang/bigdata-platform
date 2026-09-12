package com.company.platform.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicDataSourceManager {
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public Connection getConnection(long sourceId, String jdbcUrl, String username, String password) throws SQLException {
        String poolKey = sourceId + "|" + jdbcUrl;
        HikariDataSource pool = pools.computeIfAbsent(poolKey, ignored -> createPool(sourceId, jdbcUrl, username, password));
        return pool.getConnection();
    }

    public Connection testConnection(String jdbcUrl, String username, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public void close(long sourceId) {
        pools.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(sourceId + "|")) return false;
            entry.getValue().close();
            return true;
        });
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(pool -> {
            try { pool.close(); } catch (RuntimeException ignored) { }
        });
        pools.clear();
    }

    private HikariDataSource createPool(long sourceId, String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setPoolName("platform-ds-" + sourceId + "-" + Integer.toUnsignedString(jdbcUrl.hashCode()));
        return new HikariDataSource(config);
    }
}
