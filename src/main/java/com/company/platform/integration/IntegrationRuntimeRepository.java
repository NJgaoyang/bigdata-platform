package com.company.platform.integration;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.PasswordCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class IntegrationRuntimeRepository {
    private final JdbcTemplate jdbc;
    private final PasswordCipher cipher;

    public IntegrationRuntimeRepository(JdbcTemplate jdbc, PasswordCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public IntegrationBatchView createBatch(long taskId, String triggerType, String runtimeConfig,
                                             Long clusterId, String parametersJson, Long sourceBatchId,
                                             String createdBy) {
        String batchCode = "B" + UUID.randomUUID().toString().replace("-", "");
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO integration_batch(task_id,batch_code,trigger_type,status,runtime_config_encrypted,cluster_id,parameters_json,source_batch_id,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, taskId);
            ps.setString(2, batchCode);
            ps.setString(3, triggerType);
            ps.setString(4, "QUEUED");
            ps.setString(5, cipher.encrypt(runtimeConfig));
            if (clusterId == null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, clusterId);
            ps.setString(7, parametersJson == null ? "{}" : parametersJson);
            if (sourceBatchId == null) ps.setNull(8, java.sql.Types.BIGINT); else ps.setLong(8, sourceBatchId);
            ps.setString(9, createdBy == null || createdBy.isBlank() ? "platform" : createdBy);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("离线同步批次创建失败");
        return getBatch(key.longValue());
    }

    public List<IntegrationBatchView> listBatches(long taskId) {
        return jdbc.query("SELECT * FROM integration_batch WHERE task_id=? ORDER BY created_at DESC,id DESC",
                (rs, n) -> batch(rs), taskId);
    }

    public IntegrationBatchView getBatch(long batchId) {
        List<IntegrationBatchView> rows = jdbc.query("SELECT * FROM integration_batch WHERE id=?",
                (rs, n) -> batch(rs), batchId);
        if (rows.isEmpty()) throw new BadRequestException("离线同步批次不存在：" + batchId);
        return rows.getFirst();
    }

    public List<IntegrationAttemptView> attempts(long batchId) {
        return jdbc.query("SELECT * FROM integration_attempt WHERE batch_id=? ORDER BY attempt_no DESC",
                (rs, n) -> attempt(rs), batchId);
    }

    public IntegrationAttemptView latestAttempt(long batchId) {
        List<IntegrationAttemptView> rows = jdbc.query(
                "SELECT * FROM integration_attempt WHERE batch_id=? ORDER BY attempt_no DESC LIMIT 1",
                (rs, n) -> attempt(rs), batchId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public IntegrationAttemptView recordAttempt(long batchId, String executionId, String status, String errorMessage) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(attempt_no),0) FROM integration_attempt WHERE batch_id=?", Integer.class, batchId);
        int attemptNo = (max == null ? 0 : max) + 1;
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO integration_attempt(batch_id,attempt_no,execution_id,status,started_at,error_message) VALUES(?,?,?,?,CURRENT_TIMESTAMP,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, batchId);
            ps.setInt(2, attemptNo);
            ps.setString(3, executionId);
            ps.setString(4, status == null ? "SUBMITTED" : status);
            ps.setString(5, errorMessage);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("离线同步执行尝试创建失败");
        updateBatch(batchId, status, terminal(status) ? LocalDateTime.now() : null, errorMessage);
        return attempts(batchId).stream().filter(item -> item.id() == key.longValue()).findFirst()
                .orElseThrow(() -> new IllegalStateException("离线同步执行尝试创建失败"));
    }

    public void updateAttempt(String executionId, String status, String errorMessage) {
        if (executionId == null || executionId.isBlank()) return;
        boolean terminal = terminal(status);
        jdbc.update("UPDATE integration_attempt SET status=?,finished_at=?,error_message=? WHERE execution_id=?",
                status, terminal ? Timestamp.valueOf(LocalDateTime.now()) : null, errorMessage, executionId);
        Long batchId = batchIdForExecution(executionId);
        if (batchId != null) updateBatch(batchId, status, terminal ? LocalDateTime.now() : null, errorMessage);
    }

    public void updateBatch(long batchId, String status, LocalDateTime finishedAt, String errorMessage) {
        String normalized = status == null || status.isBlank() ? "UNKNOWN" : status.toUpperCase();
        boolean active = active(normalized);
        jdbc.update("UPDATE integration_batch SET status=?,started_at=CASE WHEN ? THEN COALESCE(started_at,CURRENT_TIMESTAMP) ELSE started_at END,finished_at=?,error_message=? WHERE id=?",
                normalized, active, finishedAt == null ? null : Timestamp.valueOf(finishedAt), errorMessage, batchId);
    }

    public String runtimeConfig(long batchId) {
        String encrypted = jdbc.queryForObject("SELECT runtime_config_encrypted FROM integration_batch WHERE id=?", String.class, batchId);
        if (encrypted == null || encrypted.isBlank()) throw new BadRequestException("离线同步批次缺少运行快照：" + batchId);
        return cipher.decrypt(encrypted);
    }

    public Long batchIdForExecution(String executionId) {
        List<Long> rows = jdbc.query("SELECT batch_id FROM integration_attempt WHERE execution_id=? ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getLong(1), executionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public IntegrationCursorView cursor(long taskId) {
        List<IntegrationCursorView> rows = jdbc.query("SELECT task_id,cursor_column,cursor_value,updated_at FROM integration_cursor WHERE task_id=?",
                (rs, n) -> new IntegrationCursorView(rs.getLong(1), rs.getString(2), rs.getString(3), time(rs.getTimestamp(4))), taskId);
        return rows.isEmpty() ? new IntegrationCursorView(taskId, "", "", null) : rows.getFirst();
    }

    public IntegrationCursorView saveCursor(long taskId, String cursorColumn, String cursorValue) {
        jdbc.update("INSERT INTO integration_cursor(task_id,cursor_column,cursor_value) VALUES(?,?,?) ON DUPLICATE KEY UPDATE cursor_column=VALUES(cursor_column),cursor_value=VALUES(cursor_value),updated_at=CURRENT_TIMESTAMP",
                taskId, cursorColumn, cursorValue);
        return cursor(taskId);
    }

    private IntegrationBatchView batch(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IntegrationBatchView(rs.getLong("id"), rs.getLong("task_id"), rs.getString("batch_code"),
                rs.getString("trigger_type"), rs.getString("status"), nullableLong(rs, "cluster_id"),
                rs.getString("parameters_json"), nullableLong(rs, "source_batch_id"), rs.getString("created_by"),
                time(rs.getTimestamp("started_at")), time(rs.getTimestamp("finished_at")),
                rs.getString("error_message"), time(rs.getTimestamp("created_at")));
    }

    private IntegrationAttemptView attempt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IntegrationAttemptView(rs.getLong("id"), rs.getLong("batch_id"), rs.getInt("attempt_no"),
                rs.getString("execution_id"), rs.getString("status"), time(rs.getTimestamp("started_at")),
                time(rs.getTimestamp("finished_at")), rs.getString("error_message"), time(rs.getTimestamp("created_at")));
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private boolean active(String status) {
        String value = status == null ? "" : status.toUpperCase();
        return value.equals("QUEUED") || value.equals("SUBMITTED") || value.equals("RUNNING") || value.equals("STARTING");
    }

    private boolean terminal(String status) {
        String value = status == null ? "" : status.toUpperCase();
        return value.equals("SUCCESS") || value.equals("FINISHED") || value.equals("FAILED")
                || value.equals("CANCELLED") || value.equals("CANCELED") || value.equals("KILLED")
                || value.equals("STOPPED") || value.equals("LOST") || value.equals("UNKNOWN");
    }
}
