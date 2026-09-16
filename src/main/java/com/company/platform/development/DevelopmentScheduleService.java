package com.company.platform.development;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.query.QueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class DevelopmentScheduleService {
    private final JdbcTemplate jdbc;
    private final Scheduler quartz;
    private final QueryService queryService;
    private final ObjectMapper mapper;

    public DevelopmentScheduleService(JdbcTemplate jdbc, Scheduler quartz, QueryService queryService, ObjectMapper mapper) {
        this.jdbc=jdbc; this.quartz=quartz; this.queryService=queryService; this.mapper=mapper;
    }

    public ScheduleView get(long fileId) {
        requireFile(fileId);
        List<ScheduleView> rows=jdbc.query("SELECT * FROM dev_file_schedule WHERE file_id=?",(rs,n)->new ScheduleView(
                fileId,rs.getInt("current_version"),rs.getInt("published_version"),rs.getBoolean("enabled"),
                rs.getString("cycle_type"),rs.getString("execution_time"),rs.getString("cron_expression"),rs.getString("timezone"),
                rs.getObject("data_source_id",Long.class),rs.getString("database_name"),rs.getString("biz_date_param"),
                rs.getInt("retry_times"),rs.getInt("retry_interval_minutes"),rs.getInt("timeout_minutes"),dependencies(fileId),downstream(fileId),
                publishedSqlVersion(fileId),currentReleaseNo(fileId)),fileId);
        if(!rows.isEmpty()) return rows.getFirst();
        return new ScheduleView(fileId,0,0,false,"DAILY","02:00","0 0 2 * * ?","Asia/Shanghai",null,null,
                "${system.biz.date-1}",3,5,120,List.of(),downstream(fileId),publishedSqlVersion(fileId),currentReleaseNo(fileId));
    }

    @Transactional
    public ScheduleView save(long fileId, ScheduleRequest r, String operator) {
        requireFile(fileId); validate(r,fileId);
        int next=get(fileId).currentVersion()+1;
        jdbc.update("INSERT INTO dev_file_schedule(file_id,current_version,published_version,enabled,cycle_type,execution_time,cron_expression,timezone,data_source_id,database_name,biz_date_param,retry_times,retry_interval_minutes,timeout_minutes,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE current_version=VALUES(current_version),enabled=VALUES(enabled),cycle_type=VALUES(cycle_type),execution_time=VALUES(execution_time),cron_expression=VALUES(cron_expression),timezone=VALUES(timezone),data_source_id=VALUES(data_source_id),database_name=VALUES(database_name),biz_date_param=VALUES(biz_date_param),retry_times=VALUES(retry_times),retry_interval_minutes=VALUES(retry_interval_minutes),timeout_minutes=VALUES(timeout_minutes),updated_by=VALUES(updated_by)",
                fileId,next,0,r.enabled(),norm(r.cycleType(),"DAILY"),norm(r.executionTime(),"02:00"),r.cronExpression().trim(),norm(r.timezone(),"Asia/Shanghai"),r.dataSourceId(),blank(r.databaseName()),norm(r.bizDateParam(),"${system.biz.date-1}"),Math.max(0,r.retryTimes()),Math.max(1,r.retryIntervalMinutes()),Math.max(1,r.timeoutMinutes()),operator(operator));
        jdbc.update("DELETE FROM dev_file_schedule_dependency WHERE file_id=?",fileId);
        for(Long upstream: safeIds(r.upstreamFileIds())) jdbc.update("INSERT INTO dev_file_schedule_dependency(file_id,upstream_file_id) VALUES(?,?)",fileId,upstream);
        try { jdbc.update("INSERT INTO dev_file_schedule_version(file_id,version_no,config_json,created_by) VALUES(?,?,?,?)",fileId,next,mapper.writeValueAsString(get(fileId)),operator(operator)); }
        catch(Exception ex){throw new BadRequestException("保存调度版本失败："+ex.getMessage());}
        return get(fileId);
    }

    @Transactional
    public BundleView publish(long fileId,int sqlVersion,String operator,String remark) {
        ScheduleView s=get(fileId);
        int release=currentReleaseNo(fileId)+1;
        jdbc.update("UPDATE dev_file_release_bundle SET current_flag=FALSE WHERE file_id=?",fileId);
        jdbc.update("INSERT INTO dev_file_release_bundle(file_id,release_no,sql_version,schedule_version,current_flag,operator_name,remark) VALUES(?,?,?,?,TRUE,?,?)",fileId,release,sqlVersion,s.currentVersion(),operator(operator),blank(remark));
        if(s.currentVersion()>0) jdbc.update("UPDATE dev_file_schedule SET published_version=current_version WHERE file_id=?",fileId);
        syncQuartz(fileId);
        return bundle(fileId);
    }

    public BundleView bundle(long fileId){
        ScheduleView s=get(fileId); int sqlCurrent=jdbc.queryForObject("SELECT current_version FROM dev_file WHERE id=?",Integer.class,fileId);
        return new BundleView(fileId,sqlCurrent,s.publishedSqlVersion(),s.currentVersion(),s.publishedVersion(),s.currentReleaseNo(),sqlCurrent!=s.publishedSqlVersion(),s.currentVersion()!=s.publishedVersion());
    }

    public List<ReleaseView> releases(long fileId){requireFile(fileId);return jdbc.query("SELECT release_no,sql_version,schedule_version,current_flag,operator_name,remark,released_at FROM dev_file_release_bundle WHERE file_id=? ORDER BY release_no DESC",(rs,n)->new ReleaseView(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getBoolean(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toLocalDateTime()),fileId);}

    public ScheduleRuntimeView runtime(long fileId) {
        requireFile(fileId);
        ScheduleRuntimeView latest=jdbc.query("SELECT status,planned_at,started_at,finished_at,execution_id,error_message FROM dev_file_schedule_execution WHERE file_id=? ORDER BY id DESC LIMIT 1",(rs,n)->new ScheduleRuntimeView(fileId,rs.getString("status"),time(rs.getTimestamp("planned_at")),time(rs.getTimestamp("started_at")),time(rs.getTimestamp("finished_at")),rs.getString("execution_id"),rs.getString("error_message"),null),fileId).stream().findFirst().orElse(new ScheduleRuntimeView(fileId,"NEVER_RUN",null,null,null,null,null,null));
        LocalDateTime next=null;
        try { Trigger trigger=quartz.getTrigger(new TriggerKey("dev_schedule_"+fileId,"datasphere-development")); if(trigger!=null&&trigger.getNextFireTime()!=null){ ZoneId zone=trigger instanceof CronTrigger cron?cron.getTimeZone().toZoneId():ZoneId.systemDefault(); next=LocalDateTime.ofInstant(trigger.getNextFireTime().toInstant(), zone); } } catch(SchedulerException ignored) {}
        return new ScheduleRuntimeView(fileId,latest.status(),latest.plannedAt(),latest.startedAt(),latest.finishedAt(),latest.executionId(),latest.errorMessage(),next);
    }

    private LocalDateTime time(java.sql.Timestamp value){return value==null?null:value.toLocalDateTime();}

    public ScheduleView version(long fileId, int versionNo) {
        requireFile(fileId);
        if (versionNo <= 0) {
            throw new BadRequestException("调度版本不存在：S" + versionNo);
        }
        String json = jdbc.query("SELECT config_json FROM dev_file_schedule_version WHERE file_id=? AND version_no=?",
                (rs,n) -> rs.getString(1), fileId, versionNo).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("调度版本不存在：S" + versionNo));
        try {
            return mapper.readValue(json, ScheduleView.class);
        } catch (Exception ex) {
            throw new BadRequestException("读取调度版本失败：" + ex.getMessage());
        }
    }

    @Transactional
    public BundleView rollback(long fileId,int releaseNo,String operator){
        ReleaseView target=releases(fileId).stream().filter(x->x.releaseNo()==releaseNo).findFirst().orElseThrow(()->new NotFoundException("发布版本不存在：P"+releaseNo));
        jdbc.update("UPDATE dev_file_version SET publish_flag=(version_no=?) WHERE file_id=?",target.sqlVersion(),fileId);
        jdbc.update("UPDATE dev_file_release_bundle SET current_flag=(release_no=?) WHERE file_id=?",releaseNo,fileId);
        jdbc.update("UPDATE dev_file_schedule SET published_version=? WHERE file_id=?",target.scheduleVersion(),fileId);
        syncQuartz(fileId); return bundle(fileId);
    }

    public void executeScheduled(long fileId, java.util.Date plannedAt){
        if(!lifecycleOnline(fileId)) return;
        ProdConfig p=prodConfig(fileId); if(p==null||!p.enabled())return;
        String biz=resolveBizDate(p.bizDateParam(),p.timezone());
        Integer existing=jdbc.queryForObject("SELECT COUNT(*) FROM dev_file_schedule_execution WHERE file_id=? AND business_date=? AND release_no=? AND status IN ('RUNNING','SUCCESS')",Integer.class,fileId,java.sql.Date.valueOf(biz),p.releaseNo());
        if(existing!=null&&existing>0)return;
        if(!dependenciesReady(p,biz)){insertExecution(fileId,p,biz,"WAITING_DEPENDENCY",plannedAt,null,null);return;}
        String sql=publishedSql(fileId,p.sqlVersion());
        sql=sql.replace("${biz_date}",biz).replace("${system.biz.date-1}",LocalDate.now(ZoneId.of(p.timezone())).minusDays(1).toString()).replace("${system.biz.date}",LocalDate.now(ZoneId.of(p.timezone())).toString()).replace("${system.date}",LocalDate.now(ZoneId.of(p.timezone())).toString());
        long executionId=insertExecution(fileId,p,biz,"RUNNING",plannedAt,null,null);
        try { var result=queryService.execute(sql,false,p.dataSourceId(),p.databaseName(),"scheduler"); jdbc.update("UPDATE dev_file_schedule_execution SET execution_id=?,status=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",result.executionId(),result.status(),executionId); if("SUCCESS".equals(result.status()))triggerReadyDownstream(fileId,biz); }
        catch(RuntimeException ex){jdbc.update("UPDATE dev_file_schedule_execution SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE id=?",ex.getMessage(),executionId);throw ex;}
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore(){jdbc.queryForList("SELECT file_id FROM dev_file_schedule WHERE published_version>0").forEach(r->syncQuartz(((Number)r.get("file_id")).longValue()));}

    public void refreshLifecycle(long fileId){ syncQuartz(fileId); }

    private void syncQuartz(long fileId){
        ProdConfig p=lifecycleOnline(fileId)?prodConfig(fileId):null; JobKey jk=jobKey(fileId);TriggerKey tk=new TriggerKey("dev_schedule_"+fileId,"datasphere-development");
        try{if(p==null||!p.enabled()){if(quartz.checkExists(tk))quartz.unscheduleJob(tk);if(quartz.checkExists(jk))quartz.deleteJob(jk);return;} JobDetail job=JobBuilder.newJob(DevelopmentScheduleQuartzJob.class).withIdentity(jk).usingJobData("fileId",String.valueOf(fileId)).storeDurably(true).build();if(quartz.checkExists(jk))quartz.addJob(job,true);else quartz.addJob(job,false);CronTrigger trigger=TriggerBuilder.newTrigger().withIdentity(tk).forJob(jk).withSchedule(CronScheduleBuilder.cronSchedule(p.cronExpression()).inTimeZone(TimeZone.getTimeZone(p.timezone())).withMisfireHandlingInstructionDoNothing()).build();if(quartz.checkExists(tk))quartz.rescheduleJob(tk,trigger);else quartz.scheduleJob(trigger);quartz.resumeTrigger(tk);}catch(SchedulerException ex){throw new BadRequestException("同步开发任务调度失败："+ex.getMessage());}
    }

    private boolean lifecycleOnline(long fileId){
        String v=jdbc.queryForObject("SELECT lifecycle_status FROM dev_file WHERE id=?",String.class,fileId);
        return "ONLINE".equalsIgnoreCase(v);
    }
    private ProdConfig prodConfig(long fileId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT s.published_version,b.release_no,b.sql_version FROM dev_file_schedule s LEFT JOIN dev_file_release_bundle b ON b.file_id=s.file_id AND b.current_flag=TRUE WHERE s.file_id=? AND s.published_version>0",fileId);if(rows.isEmpty())return null;int ver=((Number)rows.getFirst().get("published_version")).intValue();int rel=rows.getFirst().get("release_no")==null?0:((Number)rows.getFirst().get("release_no")).intValue();int sql=rows.getFirst().get("sql_version")==null?publishedSqlVersion(fileId):((Number)rows.getFirst().get("sql_version")).intValue();
        String json=jdbc.queryForObject("SELECT config_json FROM dev_file_schedule_version WHERE file_id=? AND version_no=?",String.class,fileId,ver);try{ScheduleView s=mapper.readValue(json,ScheduleView.class);return new ProdConfig(ver,rel,sql,s.enabled(),s.cronExpression(),s.timezone(),s.dataSourceId(),s.databaseName(),s.bizDateParam(),s.dependencies().stream().map(DependencyView::fileId).toList());}catch(Exception ex){throw new BadRequestException("读取生产调度配置失败："+ex.getMessage());}
    }
    private long insertExecution(long fileId,ProdConfig p,String biz,String status,java.util.Date plannedAt,String executionId,String error){jdbc.update("INSERT INTO dev_file_schedule_execution(file_id,release_no,sql_version,schedule_version,business_date,execution_id,status,planned_at,error_message) VALUES(?,?,?,?,?,?,?,?,?)",fileId,p.releaseNo(),p.sqlVersion(),p.scheduleVersion(),java.sql.Date.valueOf(biz),executionId,status,plannedAt==null?null:new java.sql.Timestamp(plannedAt.getTime()),error);return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
    private boolean dependenciesReady(ProdConfig p,String biz){for(Long upstream:p.upstreamFileIds()){Integer ok=jdbc.queryForObject("SELECT COUNT(*) FROM dev_file_schedule_execution WHERE file_id=? AND business_date=? AND status='SUCCESS'",Integer.class,upstream,java.sql.Date.valueOf(biz));if(ok==null||ok==0)return false;}return true;}
    private void triggerReadyDownstream(long upstream,String biz){for(Map<String,Object> row:jdbc.queryForList("SELECT file_id FROM dev_file_schedule WHERE published_version>0")){long candidate=((Number)row.get("file_id")).longValue();ProdConfig p=prodConfig(candidate);if(p==null||!p.enabled()||!p.upstreamFileIds().contains(upstream)||!dependenciesReady(p,biz))continue;try{JobKey key=jobKey(candidate);if(quartz.checkExists(key))quartz.triggerJob(key);}catch(SchedulerException ex){throw new BadRequestException("触发下游开发任务失败："+ex.getMessage());}}}
    private JobKey jobKey(long fileId){return new JobKey("dev_file_"+fileId,"datasphere-development");}
    private String publishedSql(long fileId,int version){return jdbc.query("SELECT content FROM dev_file_version WHERE file_id=? AND version_no=?",(rs,n)->rs.getString(1),fileId,version).stream().findFirst().orElseThrow(()->new BadRequestException("生产 SQL V"+version+" 不存在"));}
    private int publishedSqlVersion(long fileId){Integer v=jdbc.query("SELECT version_no FROM dev_file_version WHERE file_id=? AND publish_flag=TRUE ORDER BY version_no DESC LIMIT 1",(rs,n)->rs.getInt(1),fileId).stream().findFirst().orElse(0);return v==null?0:v;}
    private int currentReleaseNo(long fileId){Integer v=jdbc.query("SELECT release_no FROM dev_file_release_bundle WHERE file_id=? AND current_flag=TRUE LIMIT 1",(rs,n)->rs.getInt(1),fileId).stream().findFirst().orElse(0);return v==null?0:v;}
    private List<DependencyView> dependencies(long fileId){return jdbc.query("SELECT d.upstream_file_id,f.name FROM dev_file_schedule_dependency d JOIN dev_file f ON f.id=d.upstream_file_id WHERE d.file_id=? ORDER BY f.name",(rs,n)->new DependencyView(rs.getLong(1),rs.getString(2)),fileId);}
    private List<DependencyView> downstream(long fileId){return jdbc.query("SELECT d.file_id,f.name FROM dev_file_schedule_dependency d JOIN dev_file f ON f.id=d.file_id WHERE d.upstream_file_id=? ORDER BY f.name",(rs,n)->new DependencyView(rs.getLong(1),rs.getString(2)),fileId);}
    private void validate(ScheduleRequest r,long fileId){if(r==null||r.cronExpression()==null||!org.quartz.CronExpression.isValidExpression(r.cronExpression().trim()))throw new BadRequestException("Cron 表达式无效，请使用 Quartz Cron 格式");try{ZoneId.of(norm(r.timezone(),"Asia/Shanghai"));}catch(Exception ex){throw new BadRequestException("无效时区");}if(r.dataSourceId()==null)throw new BadRequestException("请选择 StarRocks 数据源");Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM data_source WHERE id=? AND type='STARROCKS'",Integer.class,r.dataSourceId());if(count==null||count==0)throw new BadRequestException("调度仅支持 StarRocks 数据源");for(Long id:safeIds(r.upstreamFileIds())){if(id==fileId)throw new BadRequestException("任务不能依赖自身");requireFile(id);Integer same=jdbc.queryForObject("SELECT COUNT(*) FROM dev_file a JOIN dev_file b ON a.project_id=b.project_id WHERE a.id=? AND b.id=?",Integer.class,fileId,id);if(same==null||same==0)throw new BadRequestException("上游依赖必须属于当前开发项目");}}
    private void requireFile(long fileId){Integer c=jdbc.queryForObject("SELECT COUNT(*) FROM dev_file WHERE id=?",Integer.class,fileId);if(c==null||c==0)throw new NotFoundException("开发任务不存在："+fileId);}
    private List<Long> safeIds(List<Long> ids){return ids==null?List.of():ids.stream().filter(Objects::nonNull).distinct().toList();}
    private String resolveBizDate(String param,String timezone){LocalDate d=LocalDate.now(ZoneId.of(timezone));return "${system.biz.date}".equals(param)||"${system.date}".equals(param)?d.toString():d.minusDays(1).toString();}
    private String norm(String v,String d){return v==null||v.isBlank()?d:v.trim();} private String blank(String v){return v==null?"":v.trim();} private String operator(String v){return v==null||v.isBlank()?"admin":v.trim();}

    public record DependencyView(long fileId,String name){}
    public record ScheduleRequest(boolean enabled,String cycleType,String executionTime,String cronExpression,String timezone,Long dataSourceId,String databaseName,String bizDateParam,int retryTimes,int retryIntervalMinutes,int timeoutMinutes,List<Long> upstreamFileIds){}
    public record ScheduleView(long fileId,int currentVersion,int publishedVersion,boolean enabled,String cycleType,String executionTime,String cronExpression,String timezone,Long dataSourceId,String databaseName,String bizDateParam,int retryTimes,int retryIntervalMinutes,int timeoutMinutes,List<DependencyView> dependencies,List<DependencyView> downstream,int publishedSqlVersion,int currentReleaseNo){}
    public record BundleView(long fileId,int sqlVersion,int publishedSqlVersion,int scheduleVersion,int publishedScheduleVersion,int releaseNo,boolean sqlDirty,boolean scheduleDirty){}
    public record ReleaseView(int releaseNo,int sqlVersion,int scheduleVersion,boolean current,String operatorName,String remark,java.time.LocalDateTime releasedAt){}
    public record ScheduleRuntimeView(long fileId,String status,LocalDateTime plannedAt,LocalDateTime startedAt,LocalDateTime finishedAt,String executionId,String errorMessage,LocalDateTime nextPlannedAt){}
    private record ProdConfig(int scheduleVersion,int releaseNo,int sqlVersion,boolean enabled,String cronExpression,String timezone,Long dataSourceId,String databaseName,String bizDateParam,List<Long> upstreamFileIds){}
}
