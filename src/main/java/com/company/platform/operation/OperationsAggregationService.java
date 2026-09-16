package com.company.platform.operation;

import com.company.platform.common.BadRequestException;
import com.company.platform.development.DevelopmentScheduleService;
import com.company.platform.integration.IntegrationService;
import com.company.platform.realtime.RealtimeSyncService;
import com.company.platform.scheduler.SchedulerGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OperationsAggregationService {
    private final JdbcTemplate jdbc;
    private final SchedulerGateway scheduler;
    private final IntegrationService integration;
    private final RealtimeSyncService realtime;
    private final DevelopmentScheduleService developmentSchedules;

    public OperationsAggregationService(JdbcTemplate jdbc, SchedulerGateway scheduler, IntegrationService integration,
                                        RealtimeSyncService realtime, DevelopmentScheduleService developmentSchedules) {
        this.jdbc = jdbc; this.scheduler = scheduler; this.integration = integration;
        this.realtime = realtime; this.developmentSchedules = developmentSchedules;
    }

    public Summary summary() {
        List<TaskItem> values = tasks();
        long running = values.stream().filter(item -> running(item.runtimeStatus())).count();
        long success = values.stream().filter(item -> success(item.runtimeStatus())).count();
        long failed = values.stream().filter(item -> failed(item.runtimeStatus())).count();
        long stopped = values.stream().filter(item -> stopped(item.runtimeStatus())).count();
        return new Summary(values.size(), running, success, failed, stopped);
    }

    public List<TaskItem> tasks() {
        realtime.refreshAllRuntimeStates();
        List<TaskItem> out = new ArrayList<>();
        out.addAll(jdbc.query("SELECT t.id,t.name,t.lifecycle_status,t.status,t.created_by,i.id AS instance_id,i.execution_id,i.status AS runtime_status,i.started_at,i.finished_at,i.error_message " +
                "FROM integration_task t LEFT JOIN integration_instance i ON i.id=(SELECT x.id FROM integration_instance x WHERE x.task_id=t.id ORDER BY x.created_at DESC LIMIT 1) ORDER BY t.id DESC",
                (rs,n)->task("OFFLINE",rs.getLong("id"),rs.getString("name"),"SeaTunnel",rs.getString("lifecycle_status"),
                        rs.getString("runtime_status"),rs.getString("created_by"),rs.getString("execution_id"),
                        time(rs.getTimestamp("started_at")),time(rs.getTimestamp("finished_at")),rs.getString("error_message"),
                        "ONLINE".equalsIgnoreCase(rs.getString("lifecycle_status")),
                        rs.getString("runtime_status")!=null && !running(rs.getString("runtime_status")), running(rs.getString("runtime_status")))));
        out.addAll(jdbc.query("SELECT d.id,d.name,d.release_state,d.observed_state,d.created_by,e.id AS execution_row,e.engine_job_id,e.status AS runtime_status,e.started_at,e.finished_at,e.error_message " +
                "FROM realtime_sync_definition d LEFT JOIN realtime_sync_execution e ON e.id=(SELECT x.id FROM realtime_sync_execution x WHERE x.job_id=d.id ORDER BY x.created_at DESC LIMIT 1) ORDER BY d.id DESC",
                (rs,n)->task("REALTIME",rs.getLong("id"),rs.getString("name"),"Flink CDC",rs.getString("release_state"),
                        rs.getString("runtime_status") == null ? rs.getString("observed_state") : rs.getString("runtime_status"),rs.getString("created_by"),rs.getString("engine_job_id"),
                        time(rs.getTimestamp("started_at")),time(rs.getTimestamp("finished_at")),rs.getString("error_message"),
                        "PUBLISHED".equalsIgnoreCase(rs.getString("release_state")) && !running(rs.getString("observed_state")),
                        "PUBLISHED".equalsIgnoreCase(rs.getString("release_state")) && rs.getString("runtime_status")!=null && !running(rs.getString("runtime_status")),
                        running(rs.getString("observed_state")))));
        out.addAll(jdbc.query("SELECT f.id,f.name,f.file_type,f.lifecycle_status,p.owner_name,e.execution_id,e.status AS runtime_status,e.started_at,e.finished_at,e.error_message," +
                "(SELECT COUNT(*) FROM dev_file_release_bundle b WHERE b.file_id=f.id AND b.current_flag=TRUE) AS release_count " +
                "FROM dev_file f LEFT JOIN dev_project p ON p.id=f.project_id LEFT JOIN dev_file_schedule_execution e ON e.id=(SELECT x.id FROM dev_file_schedule_execution x WHERE x.file_id=f.id ORDER BY x.id DESC LIMIT 1) " +
                "WHERE f.recycled=FALSE ORDER BY f.id DESC",
                (rs,n)->{ boolean executable="SQL".equalsIgnoreCase(rs.getString("file_type")) && rs.getInt("release_count")>0 && "ONLINE".equalsIgnoreCase(rs.getString("lifecycle_status")); String runtime=rs.getString("runtime_status");
                    return task("DEVELOPMENT",rs.getLong("id"),rs.getString("name"),"StarRocks",rs.getString("lifecycle_status"),runtime,rs.getString("owner_name"),rs.getString("execution_id"),
                            time(rs.getTimestamp("started_at")),time(rs.getTimestamp("finished_at")),rs.getString("error_message"),
                            executable && !running(runtime), executable && runtime!=null && !running(runtime), executable && running(runtime) && rs.getString("execution_id")!=null); }));
        return out.stream().sorted(Comparator.comparing(TaskItem::type).thenComparing(TaskItem::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<InstanceItem> instances() {
        realtime.refreshAllRuntimeStates();
        List<InstanceItem> result = new ArrayList<>();
        result.addAll(jdbc.query("SELECT ii.id,ii.execution_id,it.name,ii.status,ii.started_at,ii.finished_at,ii.error_message,ii.created_at,COALESCE(ib.created_by,it.created_by,'platform') AS created_by FROM integration_instance ii LEFT JOIN integration_task it ON it.id=ii.task_id LEFT JOIN integration_attempt ia ON ia.execution_id=ii.execution_id LEFT JOIN integration_batch ib ON ib.id=ia.batch_id ORDER BY ii.created_at DESC LIMIT 200",
                (rs,n)->item("OFFLINE",String.valueOf(rs.getLong("id")),rs.getString("execution_id"),rs.getString("name"),rs.getString("status"),"SEATUNNEL",rs.getString("created_by"),rs.getTimestamp("started_at"),rs.getTimestamp("finished_at"),rs.getString("error_message"),rs.getTimestamp("created_at"))));
        result.addAll(jdbc.query("SELECT re.id,re.engine_job_id,rd.name,re.status,re.started_at,re.finished_at,re.error_message,re.created_at,rd.created_by FROM realtime_sync_execution re LEFT JOIN realtime_sync_definition rd ON rd.id=re.job_id ORDER BY re.created_at DESC LIMIT 200",
                (rs,n)->item("REALTIME",String.valueOf(rs.getLong("id")),rs.getString("engine_job_id"),rs.getString("name"),rs.getString("status"),"FLINK_CDC",rs.getString("created_by"),rs.getTimestamp("started_at"),rs.getTimestamp("finished_at"),rs.getString("error_message"),rs.getTimestamp("created_at"))));
        result.addAll(jdbc.query("SELECT e.id,e.execution_id,f.name,e.status,e.started_at,e.finished_at,e.error_message,p.owner_name FROM dev_file_schedule_execution e JOIN dev_file f ON f.id=e.file_id LEFT JOIN dev_project p ON p.id=f.project_id ORDER BY e.id DESC LIMIT 200",
                (rs,n)->item("DEVELOPMENT",String.valueOf(rs.getLong("id")),rs.getString("execution_id"),rs.getString("name"),rs.getString("status"),"STARROCKS",rs.getString("owner_name"),rs.getTimestamp("started_at"),rs.getTimestamp("finished_at"),rs.getString("error_message"),rs.getTimestamp("started_at"))));
        return result.stream().sorted(Comparator.comparing(InstanceItem::createdAt,Comparator.nullsLast(Comparator.reverseOrder()))).limit(400).toList();
    }

    public List<FailureItem> failures() {
        return tasks().stream().filter(x->failed(x.runtimeStatus())).map(x->new FailureItem(x.type(),String.valueOf(x.id()),x.lastExecutionId(),x.name(),x.engine(),x.runtimeStatus(),1,x.errorMessage(),x.lastStartedAt())).toList();
    }

    public List<AlertItem> alerts() {
        return jdbc.query("SELECT id,channel_id,channel_name,channel_type,task_name,task_status,message,delivery_status,response_message,pushed_at FROM alert_delivery_history ORDER BY pushed_at DESC,id DESC LIMIT 500",
                (rs,n)->new AlertItem(rs.getString("channel_type"),rs.getString("channel_name"),String.valueOf(rs.getObject("channel_id")),rs.getString("task_name"),rs.getString("task_status"),
                        rs.getString("message"),time(rs.getTimestamp("pushed_at")),rs.getString("delivery_status")));
    }

    public TaskActionResult startTask(String type,long id,String operator){
        return switch(normalizeType(type)){
            case "OFFLINE" -> { var r=integration.execute(id); yield new TaskActionResult(type,id,r.executionId(),r.status()); }
            case "REALTIME" -> { requireRealtimePublished(id); var r=realtime.start(id,operator); yield new TaskActionResult(type,id,r.execution()==null?null:r.execution().engineJobId(),r.job().observedState()); }
            case "DEVELOPMENT" -> { var r=developmentSchedules.runNow(id,operator); yield new TaskActionResult(type,id,r.executionId(),r.status()); }
            default -> throw new BadRequestException("不支持的任务类型："+type);
        };
    }

    public TaskActionResult rerunTask(String type,long id,String operator){
        return switch(normalizeType(type)){
            case "OFFLINE" -> { var r=integration.executeConfirmed(id); yield new TaskActionResult(type,id,r.executionId(),r.status()); }
            case "REALTIME" -> { requireRealtimePublished(id); var r=realtime.restart(id,operator); yield new TaskActionResult(type,id,r.execution()==null?null:r.execution().engineJobId(),r.job().observedState()); }
            case "DEVELOPMENT" -> { var r=developmentSchedules.rerunNow(id,operator); yield new TaskActionResult(type,id,r.executionId(),r.status()); }
            default -> throw new BadRequestException("不支持的任务类型："+type);
        };
    }

    public void killTask(String type,long id,String operator){
        switch(normalizeType(type)){
            case "OFFLINE" -> { String executionId=jdbc.query("SELECT execution_id FROM integration_instance WHERE task_id=? AND status IN ('RUNNING','STARTING','SUBMITTED','QUEUED') ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(()->new BadRequestException("当前没有运行中的离线实例")); integration.stop(executionId); }
            case "REALTIME" -> realtime.stop(id,operator);
            case "DEVELOPMENT" -> developmentSchedules.killNow(id,operator);
            default -> throw new BadRequestException("不支持的任务类型："+type);
        }
    }

    public String taskLog(String type,long id){
        return switch(normalizeType(type)){
            case "OFFLINE" -> { String executionId=jdbc.query("SELECT execution_id FROM integration_instance WHERE task_id=? ORDER BY created_at DESC LIMIT 1",(rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(()->new BadRequestException("暂无离线执行实例")); yield integration.log(executionId); }
            case "REALTIME" -> realtime.logs(id);
            case "DEVELOPMENT" -> developmentLog(id);
            default -> throw new BadRequestException("不支持的任务类型："+type);
        };
    }

    private String developmentLog(long id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT e.execution_id,e.status,e.started_at,e.finished_at,e.error_message,q.sql_text,q.elapsed_ms FROM dev_file_schedule_execution e LEFT JOIN query_execution q ON q.query_id=e.execution_id WHERE e.file_id=? ORDER BY e.id DESC LIMIT 1",id);
        if(rows.isEmpty()) return "暂无数据开发运行日志"; Map<String,Object> r=rows.getFirst();
        return "执行ID："+value(r.get("execution_id"))+"\n状态："+value(r.get("status"))+"\n开始："+value(r.get("started_at"))+"\n结束："+value(r.get("finished_at"))+"\n耗时："+value(r.get("elapsed_ms"))+" ms\n错误："+value(r.get("error_message"))+"\n\nSQL：\n"+value(r.get("sql_text"));
    }

    public String log(String type,String id){
        return switch(normalizeType(type)){
            case "OFFLINE" -> { Map<String,Object> row=jdbc.queryForMap("SELECT execution_id FROM integration_instance WHERE id=?",Long.parseLong(id)); yield integration.log(String.valueOf(row.get("execution_id"))); }
            case "REALTIME" -> { Long jobId=jdbc.queryForObject("SELECT job_id FROM realtime_sync_execution WHERE id=?",Long.class,Long.parseLong(id)); if(jobId==null)throw new BadRequestException("实时运行实例不存在："+id); yield realtime.logs(jobId); }
            case "DEVELOPMENT" -> { Long fileId=jdbc.queryForObject("SELECT file_id FROM dev_file_schedule_execution WHERE id=?",Long.class,Long.parseLong(id)); if(fileId==null)throw new BadRequestException("开发运行实例不存在："+id); yield developmentLog(fileId); }
            default -> throw new BadRequestException("当前实例类型暂不支持查看日志："+type);
        };
    }

    public void stop(String type,String id){
        switch(normalizeType(type)){
            case "OFFLINE" -> { Map<String,Object> row=jdbc.queryForMap("SELECT execution_id FROM integration_instance WHERE id=?",Long.parseLong(id)); integration.cancel(String.valueOf(row.get("execution_id"))); }
            case "REALTIME" -> { Long jobId=jdbc.queryForObject("SELECT job_id FROM realtime_sync_execution WHERE id=?",Long.class,Long.parseLong(id)); if(jobId==null)throw new BadRequestException("实时运行实例不存在："+id); realtime.stop(jobId,"operations"); }
            case "DEVELOPMENT" -> { Long fileId=jdbc.queryForObject("SELECT file_id FROM dev_file_schedule_execution WHERE id=?",Long.class,Long.parseLong(id)); if(fileId==null)throw new BadRequestException("开发运行实例不存在："+id); developmentSchedules.killNow(fileId,"operations"); }
            default -> throw new BadRequestException("不支持的实例类型："+type);
        }
    }

    public SchedulerGateway.RunResult rerunWorkflow(String instanceId){return scheduler.rerun(instanceId);}
    private void requireRealtimePublished(long id){String state=jdbc.query("SELECT release_state FROM realtime_sync_definition WHERE id=?",(rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(()->new BadRequestException("实时任务不存在："+id));if(!"PUBLISHED".equalsIgnoreCase(state))throw new BadRequestException("实时任务尚未发布，运维中心不能修改或自动发布任务，请先在数据集成完成发布");}
    private TaskItem task(String type,long id,String name,String engine,String lifecycle,String runtime,String owner,String executionId,LocalDateTime started,LocalDateTime finished,String error,boolean canStart,boolean canRerun,boolean canKill){return new TaskItem(type,id,name==null||name.isBlank()?"未命名任务":name,engine,lifecycle==null?"—":lifecycle,runtime==null?"未运行":runtime,owner==null?"platform":owner,executionId,started,finished,error,canStart,canRerun,canKill);}
    private String normalizeType(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);} private boolean running(String status){String s=norm(status);return s.contains("RUNNING")||s.contains("STARTING")||s.contains("QUEUED")||s.contains("SUBMITTED");} private boolean success(String status){String s=norm(status);return s.contains("SUCCESS")||s.contains("FINISHED");} private boolean failed(String status){String s=norm(status);return s.contains("FAIL")||s.contains("ERROR")||s.contains("LOST")||s.contains("UNKNOWN");} private boolean stopped(String status){String s=norm(status);return s.contains("STOP")||s.contains("CANCEL");} private String norm(String status){return status==null?"":status.toUpperCase(Locale.ROOT);} private LocalDateTime time(java.sql.Timestamp value){return value==null?null:value.toLocalDateTime();} private String value(Object v){return v==null?"—":String.valueOf(v);}
    private InstanceItem item(String type,String id,String externalId,String name,String status,String engine,String createdBy,java.sql.Timestamp started,java.sql.Timestamp finished,String error,java.sql.Timestamp created){return new InstanceItem(type,id,externalId,name==null||name.isBlank()?"未命名任务":name,status,engine,createdBy==null||createdBy.isBlank()?"platform":createdBy,time(started),time(finished),error,time(created));}

    public record Summary(long total,long running,long success,long failed,long stopped){}
    public record TaskItem(String type,long id,String name,String engine,String lifecycleStatus,String runtimeStatus,String owner,String lastExecutionId,LocalDateTime lastStartedAt,LocalDateTime lastFinishedAt,String errorMessage,boolean canStart,boolean canRerun,boolean canKill){}
    public record TaskActionResult(String type,long id,String executionId,String status){}
    public record InstanceItem(String type,String id,String externalId,String name,String status,String engine,String createdBy,LocalDateTime startedAt,LocalDateTime finishedAt,String errorMessage,LocalDateTime createdAt){}
    public record FailureItem(String type,String id,String parentInstanceId,String name,String engine,String status,int attemptNo,String errorMessage,LocalDateTime startedAt){}
    public record AlertItem(String alertType,String resourceType,String resourceId,String name,String status,String message,LocalDateTime occurredAt,String handlingState){}
}
