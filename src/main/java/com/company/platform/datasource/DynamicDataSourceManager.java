package com.company.platform.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicDataSourceManager {
    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    public Connection getConnection(long sourceId, String jdbcUrl, String username, String password) throws SQLException {
        HikariDataSource pool = pools.computeIfAbsent(sourceId, ignored -> createPool(jdbcUrl, username, password));
        return pool.getConnection();
    }

    public Connection testConnection(String jdbcUrl, String username, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public void close(long sourceId) {
        HikariDataSource pool = pools.remove(sourceId);
        if (pool != null) pool.close();
    }

    private HikariDataSource createPool(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("platform-ds-pool");
        return new HikariDataSource(config);
    }
}
