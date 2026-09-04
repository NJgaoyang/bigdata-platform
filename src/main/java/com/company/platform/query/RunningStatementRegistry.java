package com.company.platform.query;

import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunningStatementRegistry {
    private final ConcurrentHashMap<String, Statement> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> cancelled = new ConcurrentHashMap<>();
    public void register(String queryId, Statement statement) {
        running.put(queryId, statement);
        if (cancelled.containsKey(queryId)) {
            try { statement.cancel(); } catch (SQLException ignored) { }
        }
    }
    public void remove(String queryId) { running.remove(queryId); cancelled.remove(queryId); }
    public boolean cancel(String queryId) {
        cancelled.put(queryId, Boolean.TRUE);
        Statement statement = running.get(queryId);
        if (statement == null) return true;
        try { statement.cancel(); return true; } catch (SQLException ignored) { return false; }
    }
}
