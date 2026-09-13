package com.company.platform.workbench;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class WorkbenchService {
    private final JdbcTemplate jdbc;

    public WorkbenchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Summary summary() {
        int workflowRunning = count("SELECT COUNT(*) FROM workflow_instance WHERE status IN ('QUEUED','RUNNING','STOPPING')");
        int integrationRunning = count("SELECT COUNT(*) FROM integration_instance WHERE status IN ('RUNNING','SUBMITTED')");
        int realtimeRunning = count("SELECT COUNT(*) FROM realtime_sync_definition WHERE observed_state IN ('STARTING','RUNNING','STOPPING')");
        int unpublished = count("SELECT COUNT(*) FROM dev_file WHERE status='DRAFT'")
                + count("SELECT COUNT(*) FROM workflow WHERE status='DRAFT'")
                + count("SELECT COUNT(*) FROM realtime_sync_definition WHERE release_state='DRAFT'");
        int successful24h = count("SELECT COUNT(*) FROM workflow_instance WHERE status='SUCCESS' AND COALESCE(finished_at,created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR")
                + count("SELECT COUNT(*) FROM integration_instance WHERE status IN ('SUCCESS','FINISHED') AND COALESCE(finished_at,created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR")
                + count("SELECT COUNT(*) FROM realtime_sync_execution WHERE status='FINISHED' AND COALESCE(finished_at,created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR");
        int failed24h = failed24h();
        int unhealthySources = count("SELECT COUNT(*) FROM data_source WHERE UPPER(COALESCE(status,'')) IN ('DOWN','FAIL','FAILED','ERROR','UNAVAILABLE')");
        int completed24h = successful24h + failed24h;
        Double successRate = completed24h == 0 ? null : (successful24h * 100.0d / completed24h);
        return new Summary(failed24h + unhealthySources, workflowRunning + integrationRunning + realtimeRunning,
                unpublished, successRate, successful24h, failed24h, unhealthySources, LocalDateTime.now());
    }

    public List<Issue> issues() {
        List<Issue> result = new ArrayList<>();
        result.addAll(jdbc.query("SELECT id,name,status,last_check_message,last_checked_at FROM data_source WHERE UPPER(COALESCE(status,'')) IN ('DOWN','FAIL','FAILED','ERROR','UNAVAILABLE')", (rs,n) ->
                new Issue("datasource-"+rs.getLong("id"),"数据源",rs.getString("name"),rs.getString("status"),
                        summaryText(rs.getString("last_check_message"),"数据源连接异常"),date(rs,"last_checked_at"),"/integration/datasources")));
        result.addAll(jdbc.query("SELECT wi.id,w.name,wi.status,wi.error_message,COALESCE(wi.finished_at,wi.started_at,wi.created_at) occurred_at FROM workflow_instance wi LEFT JOIN workflow w ON w.workflow_code=wi.workflow_code WHERE wi.status='FAILED' AND COALESCE(wi.finished_at,wi.created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR", (rs,n) ->
                issue(rs,"workflow-","工作流",value(rs.getString("name"),"工作流实例"),"/operations/failures")));
        result.addAll(jdbc.query("SELECT ii.id,it.name,ii.status,ii.error_message,COALESCE(ii.finished_at,ii.started_at,ii.created_at) occurred_at FROM integration_instance ii LEFT JOIN integration_task it ON it.id=ii.task_id WHERE ii.status IN ('FAILED','ERROR','LOST') AND COALESCE(ii.finished_at,ii.created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR", (rs,n) ->
                issue(rs,"integration-","离线同步",value(rs.getString("name"),"离线同步实例"),"/integration/instances")));
        result.addAll(jdbc.query("SELECT re.id,rd.name,re.status,re.error_message,COALESCE(re.finished_at,re.started_at,re.created_at) occurred_at FROM realtime_sync_execution re LEFT JOIN realtime_sync_definition rd ON rd.id=re.job_id WHERE re.status='FAILED' AND COALESCE(re.finished_at,re.created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR", (rs,n) ->
                issue(rs,"realtime-","实时同步",value(rs.getString("name"),"实时同步实例"),"/integration/realtime")));
        return result.stream().sorted(Comparator.comparing(Issue::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(20).toList();
    }

    public List<RecentRun> recentRuns() {
        List<RecentRun> result = new ArrayList<>();
        result.addAll(jdbc.query("SELECT wi.id,w.name,wi.status,wi.started_at,wi.finished_at,wi.created_at,wi.error_message FROM workflow_instance wi LEFT JOIN workflow w ON w.workflow_code=wi.workflow_code ORDER BY wi.created_at DESC LIMIT 20", (rs,n) ->
                run(rs,"workflow-","工作流",value(rs.getString("name"),"工作流实例"),"/operations/instances")));
        result.addAll(jdbc.query("SELECT ii.id,it.name,ii.status,ii.started_at,ii.finished_at,ii.created_at,ii.error_message FROM integration_instance ii LEFT JOIN integration_task it ON it.id=ii.task_id ORDER BY ii.created_at DESC LIMIT 20", (rs,n) ->
                run(rs,"integration-","离线同步",value(rs.getString("name"),"离线同步实例"),"/integration/instances")));
        result.addAll(jdbc.query("SELECT re.id,rd.name,re.status,re.started_at,re.finished_at,re.created_at,re.error_message FROM realtime_sync_execution re LEFT JOIN realtime_sync_definition rd ON rd.id=re.job_id ORDER BY re.created_at DESC LIMIT 20", (rs,n) ->
                run(rs,"realtime-","实时同步",value(rs.getString("name"),"实时同步实例"),"/integration/realtime")));
        return result.stream().sorted(Comparator.comparing(RecentRun::sortTime,
                Comparator.nullsLast(Comparator.reverseOrder()))).limit(20).toList();
    }

    private int failed24h() {
        return count("SELECT COUNT(*) FROM workflow_instance WHERE status='FAILED' AND COALESCE(finished_at,created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR")
                + count("SELECT COUNT(*) FROM integration_instance WHERE status IN ('FAILED','ERROR','LOST') AND COALESCE(finished_at,created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR")
                + count("SELECT COUNT(*) FROM realtime_sync_execution WHERE status='FAILED' AND COALESCE(finished_at,created_at)>=CURRENT_TIMESTAMP-INTERVAL 24 HOUR");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private Issue issue(ResultSet rs,String prefix,String type,String name,String path) throws SQLException {
        return new Issue(prefix+rs.getLong("id"),type,name,rs.getString("status"),
                summaryText(rs.getString("error_message"),"执行失败"),date(rs,"occurred_at"),path);
    }

    private RecentRun run(ResultSet rs,String prefix,String type,String name,String path) throws SQLException {
        LocalDateTime started=date(rs,"started_at"),finished=date(rs,"finished_at"),created=date(rs,"created_at");
        return new RecentRun(prefix+rs.getLong("id"),name,type,rs.getString("status"),started,finished,
                summaryText(rs.getString("error_message"),""),path,finished!=null?finished:started!=null?started:created);
    }

    private LocalDateTime date(ResultSet rs,String column) throws SQLException {
        var timestamp=rs.getTimestamp(column);
        return timestamp==null?null:timestamp.toLocalDateTime();
    }

    private String value(String value,String fallback) {
        return value==null||value.isBlank()?fallback:value;
    }

    private String summaryText(String text,String fallback) {
        String normalized=value(text,fallback).replaceAll("\\s+"," ").trim();
        return normalized.length()<=160?normalized:normalized.substring(0,157)+"...";
    }

    public record Summary(int issues,int running,int unpublished,Double successRate24h,int successful24h,int failed24h,
                          int unhealthySources,LocalDateTime generatedAt) {}
    public record Issue(String id,String type,String name,String status,String detail,LocalDateTime occurredAt,String path) {}
    public record RecentRun(String id,String name,String type,String status,LocalDateTime startedAt,LocalDateTime finishedAt,
                            String detail,String path,LocalDateTime sortTime) {}
}
