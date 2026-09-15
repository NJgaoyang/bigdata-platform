package com.company.platform.realtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RealtimeVersionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public RealtimeVersionService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<VersionRow> versions(long jobId) {
        Integer publishedVersion = jdbc.query(
                "SELECT published_version FROM realtime_sync_definition WHERE id=?",
                rs -> rs.next() ? rs.getObject(1, Integer.class) : null,
                jobId);
        return jdbc.query(
                "SELECT version_no,spec_json,published,created_by,created_at FROM realtime_sync_version WHERE job_id=? ORDER BY version_no DESC",
                (rs, n) -> toRow(jobId, publishedVersion, rs),
                jobId);
    }

    private VersionRow toRow(long jobId, Integer publishedVersion, ResultSet rs) throws java.sql.SQLException {
        int version = rs.getInt("version_no");
        Map<String,Object> spec = parse(rs.getString("spec_json"));
        Execution execution = latestExecution(jobId, version);
        String restoreSavepoint = execution == null ? null : restoreSavepoint(execution.id());
        List<String> addedTables = execution == null ? List.of() : addedTables(execution.id());
        String changeType = version == 1 ? "CREATE" : (!addedTables.isEmpty() || restoreSavepoint != null ? "ADD_TABLE" : "CONFIG_UPDATE");
        Long sourceExecutionId = version <= 1 ? null : latestExecutionId(jobId, version - 1);
        return new VersionRow(
                version,
                publishedVersion != null && publishedVersion == version,
                rs.getBoolean("published"),
                changeType,
                tables(spec),
                addedTables,
                restoreSavepoint,
                sourceExecutionId,
                execution == null ? null : execution.id(),
                execution == null ? null : execution.engineJobId(),
                execution == null ? null : execution.status(),
                rs.getString("created_by"),
                timestamp(rs, "created_at")
        );
    }

    private Execution latestExecution(long jobId, int version) {
        return jdbc.query(
                "SELECT id,engine_job_id,status FROM realtime_sync_execution WHERE job_id=? AND definition_version=? ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? new Execution(rs.getLong(1), rs.getString(2), rs.getString(3)) : null,
                jobId, version);
    }

    private Long latestExecutionId(long jobId, int version) {
        Execution e = latestExecution(jobId, version);
        return e == null ? null : e.id();
    }

    private String restoreSavepoint(long executionId) {
        String detail = jdbc.query(
                "SELECT detail FROM realtime_sync_event WHERE execution_id=? AND event_type='RESTORE_START' ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                executionId);
        if (detail == null || detail.isBlank()) return null;
        int idx = detail.indexOf("Savepoint=");
        if (idx < 0) return null;
        String value = detail.substring(idx + "Savepoint=".length()).trim();
        int semi = value.indexOf('；');
        return semi >= 0 ? value.substring(0, semi).trim() : value;
    }

    private List<String> addedTables(long executionId) {
        String detail = jdbc.query(
                "SELECT detail FROM realtime_sync_event WHERE execution_id=? AND event_type='RUNNING' AND detail LIKE '%新增表：%' ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null,
                executionId);
        if (detail == null || detail.isBlank()) return List.of();
        int idx = detail.indexOf("新增表：");
        if (idx < 0) return List.of();
        String value = detail.substring(idx + "新增表：".length()).trim();
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split("、"))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> tables(Map<String,Object> spec) {
        Object raw = spec.get("tables");
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?,?> map)) continue;
            Object source = ((Map<String,Object>) map).get("sourceTable");
            if (source != null && !String.valueOf(source).isBlank()) result.add(String.valueOf(source));
        }
        return result;
    }

    private Map<String,Object> parse(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return mapper.readValue(value, new TypeReference<LinkedHashMap<String,Object>>(){});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private LocalDateTime timestamp(ResultSet rs, String name) throws java.sql.SQLException {
        Timestamp t = rs.getTimestamp(name);
        return t == null ? null : t.toLocalDateTime();
    }

    private record Execution(long id, String engineJobId, String status) {}

    public record VersionRow(
            int versionNo,
            boolean current,
            boolean published,
            String changeType,
            List<String> tables,
            List<String> addedTables,
            String restoreSavepoint,
            Long sourceExecutionId,
            Long executionId,
            String engineJobId,
            String status,
            String createdBy,
            LocalDateTime createdAt
    ) {}
}
