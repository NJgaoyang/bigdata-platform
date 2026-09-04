package com.company.platform.query;

import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunningStatementRegistry {
    private final ConcurrentHashMap<String, Statement> running = new ConcurrentHashMap<>();
    public void register(String queryId, Statement statement) { running.put(queryId, statement); }
    public void remove(String queryId) { running.remove(queryId); }
    public boolean cancel(String queryId) {
        Statement statement = running.get(queryId);
        if (statement == null) return false;
        try { statement.cancel(); return true; } catch (SQLException ignored) { return false; }
    }
}
