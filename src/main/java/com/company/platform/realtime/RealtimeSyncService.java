package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RealtimeSyncService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final FlinkEnvironmentService environments;
    private final FlinkCdcConfigBuilder configBuilder;
    private final FlinkCdcGateway gateway;
    private final RealtimePreCheckService preCheck;
    private final CdcServerIdAllocator serverIds;

    public RealtimeSyncService(JdbcTemplate jdbc, ObjectMapper mapper, FlinkEnvironmentService environments,
                               FlinkCdcConfigBuilder configBuilder, FlinkCdcGateway gateway,
                               RealtimePreCheckService preCheck, CdcServerIdAllocator serverIds) {
        this.jdbc=jdbc;this.mapper=mapper;this.environments=environments;this.configBuilder=configBuilder;this.gateway=gateway;
        this.preCheck=preCheck;this.serverIds=serverIds;
    }

    public List<RealtimeViews.Job> list(){return jdbc.query("SELECT * FROM realtime_sync_definition ORDER BY updated_at DESC",(rs,n)->job(rs));}
    public RealtimeViews.Job get(long id){return jdbc.query("SELECT * FROM realtime_sync_definition WHERE id=?",(rs,n)->job(rs),id).stream().findFirst().orElseThrow(()->new NotFoundException("实时同步任务不存在："+id));}

    @Transactional
    public RealtimeViews.Job create(RealtimeRequests.CreateJobRequest request,String operator){
        Map<String,Object> spec=new LinkedHashMap<>(request.spec()==null?Map.of():request.spec());spec.putIfAbsent("name",request.name());
        String json=json(spec), digest=digest(json);Long env=request.runtimeEnvironmentId();
        jdbc.update("INSERT INTO realtime_sync_definition(name,description,runtime_environment_id,release_state,desired_state,observed_state,definition_version,spec_json,config_digest,created_by) VALUES(?,?,?,'DRAFT','STOPPED','STOPPED',1,?,?,?)",
                request.name(),request.description(),env,json,digest,operator(operator));
        Long id=jdbc.queryForObject("SELECT id FROM realtime_sync_definition WHERE name=? AND created_by=? ORDER BY id DESC LIMIT 1",Long.class,request.name(),operator(operator));
        jdbc.update("INSERT INTO realtime_sync_version(job_id,version_no,spec_json,config_digest,published,created_by) VALUES(?,?,?,?,FALSE,?)",id,1,json,digest,operator(operator));
        event(id,null,"CREATED","创建实时同步任务 V1");
        return prepareRunnableVersion(id,operator,"READY");
    }

    @Transactional
    public RealtimeViews.Job saveDraft(long id,RealtimeRequests.DraftRequest request,String operator){
        RealtimeViews.Job current=get(id);Map<String,Object> spec=new LinkedHashMap<>(request.spec()==null?current.spec():request.spec());
        String name=request.name()==null||request.name().isBlank()?current.name():request.name().trim();spec.put("name",name);String json=json(spec),digest=digest(json);int version=current.definitionVersion()+1;
        Long env=request.runtimeEnvironmentId()==null?current.runtimeEnvironmentId():request.runtimeEnvironmentId();String desc=request.description()==null?current.description():request.description();
        if(Set.of("STARTING","RUNNING","STOPPING").contains(current.observedState())) throw new BadRequestException("实时任务运行中，请先停止后再修改配置");
        jdbc.update("UPDATE realtime_sync_definition SET name=?,description=?,runtime_environment_id=?,release_state='DRAFT',definition_version=?,spec_json=?,config_digest=?,last_error=NULL WHERE id=?",name,desc,env,version,json,digest,id);
        jdbc.update("INSERT INTO realtime_sync_version(job_id,version_no,spec_json,config_digest,published,created_by) VALUES(?,?,?,?,FALSE,?)",id,version,json,digest,operator(operator));
        event(id,null,"CONFIG_UPDATED","保存配置 V"+version);
        return prepareRunnableVersion(id,operator,"READY");
    }

    public Validation validateDraft(RealtimeRequests.CreateJobRequest request){
        Map<String,Object> spec=new LinkedHashMap<>(request.spec()==null?Map.of():request.spec());
        spec.putIfAbsent("name",request.name());
        List<String> warnings=new ArrayList<>();
        configBuilder.validate(spec);
        RealtimePreCheckService.Report report=preCheck.check(spec);
        report.items().stream().filter(item->"WARNING".equals(item.level())).forEach(item->warnings.add(item.message()+": "+item.detail()));
        boolean envReady=request.runtimeEnvironmentId()!=null;
        if(!envReady) warnings.add("尚未选择 Flink 运行环境，发布前必须配置");
        else if(!environments.get(request.runtimeEnvironmentId()).enabled()){warnings.add("Flink 运行环境已禁用");envReady=false;}
        boolean valid=report.allowed()&&envReady;
        return new Validation(valid,valid?"创建前检查通过":"创建前检查存在阻断项",warnings,configBuilder.build(spec,true),report.items());
    }

    public Validation validate(long id){
        RealtimeViews.Job job=get(id);
        List<String> warnings=new ArrayList<>();
        configBuilder.validate(job.spec());
        RealtimePreCheckService.Report report=preCheck.check(job.spec());
        report.items().stream().filter(item->"WARNING".equals(item.level())).forEach(item->warnings.add(item.message()+": "+item.detail()));
        boolean envReady=job.runtimeEnvironmentId()!=null;
        if(!envReady) warnings.add("尚未选择 Flink 运行环境，发布前必须配置");
        else if(!environments.get(job.runtimeEnvironmentId()).enabled()){warnings.add("Flink 运行环境已禁用");envReady=false;}
        boolean valid=report.allowed()&&envReady;
        return new Validation(valid,valid?"发布前检查通过":"发布前检查存在阻断项",warnings,configBuilder.build(job.spec(),true),report.items());
    }

    @Transactional
    public RealtimeViews.Job publish(long id,String operator){
        return prepareRunnableVersion(id,operator,"PUBLISHED");
    }

    private RealtimeViews.Job prepareRunnableVersion(long id,String operator,String eventType){
        RealtimeViews.Job job=get(id);
        if(job.runtimeEnvironmentId()==null)throw new BadRequestException("必须选择 Flink 运行环境");
        if(!environments.get(job.runtimeEnvironmentId()).enabled())throw new BadRequestException("Flink 运行环境已禁用");
        int parallelism=intValue(job.spec().get("parallelism"),1);
        String serverId=serverIds.allocate(id,parallelism);
        Map<String,Object> effective=new LinkedHashMap<>(job.spec());
        effective.put("serverId",serverId);
        effective.put("jobId",id);
        effective.put("labelPrefix","datasphere_rt_"+id);
        configBuilder.validate(effective);
        RealtimePreCheckService.Report report=preCheck.check(effective);
        if(!report.allowed()){
            String errors=report.items().stream().filter(RealtimePreCheckService.Item::blocking).map(i->i.message()+"（"+i.detail()+"）").reduce((a,b)->a+"；"+b).orElse("创建前检查失败");
            throw new BadRequestException(errors);
        }
        preCheck.prepareTargets(effective);
        String effectiveJson=json(effective), effectiveDigest=digest(effectiveJson);
        jdbc.update("UPDATE realtime_sync_version SET published=FALSE WHERE job_id=?",id);
        jdbc.update("UPDATE realtime_sync_version SET spec_json=?,config_digest=?,published=TRUE WHERE job_id=? AND version_no=?",effectiveJson,effectiveDigest,id,job.definitionVersion());
        jdbc.update("UPDATE realtime_sync_definition SET spec_json=?,config_digest=?,release_state='PUBLISHED',published_version=?,last_error=NULL WHERE id=?",effectiveJson,effectiveDigest,job.definitionVersion(),id);
        event(id,null,eventType,"运行配置已就绪 V"+job.definitionVersion()+"，Server ID="+serverId+" by "+operator(operator));
        return get(id);
    }

    @Transactional
    public RealtimeViews.Runtime start(long id,String operator){RealtimeViews.Job job=get(id);if(job.publishedVersion()==null||job.publishedVersion()!=job.definitionVersion()){prepareRunnableVersion(id,operator,"READY");job=get(id);}if(job.runtimeEnvironmentId()==null)throw new BadRequestException("未配置 Flink 运行环境");if(Set.of("STARTING","RUNNING","STOPPING").contains(job.observedState()))throw new BadRequestException("实时任务当前正在运行或状态切换中，请勿重复启动");
        Map<String,Object> spec=publishedSpec(id,job.publishedVersion());FlinkEnvironmentService.RuntimeEnvironment env=environments.runtime(job.runtimeEnvironmentId());String yaml=configBuilder.build(spec,false);
        jdbc.update("UPDATE realtime_sync_definition SET desired_state='RUNNING',observed_state='STARTING',last_error=NULL WHERE id=?",id);
        jdbc.update("INSERT INTO realtime_sync_execution(job_id,definition_version,runtime_environment_snapshot,status,started_at) VALUES(?,?,?,'STARTING',CURRENT_TIMESTAMP)",id,job.publishedVersion(),json(env.view()));
        long executionId=Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM realtime_sync_execution WHERE job_id=? ORDER BY id DESC LIMIT 1",Long.class,id));event(id,executionId,"STARTING","提交 Flink CDC V"+job.publishedVersion());
        try{
            FlinkCdcGateway.SubmitResult submitted=gateway.submit(env,yaml,spec);
            jdbc.update("UPDATE realtime_sync_execution SET engine_job_id=?,status='RUNNING',runtime_revision=?,local_log=? WHERE id=?",submitted.jobId(),"v"+job.publishedVersion(),submitted.log(),executionId);
            jdbc.update("UPDATE realtime_sync_definition SET observed_state='RUNNING' WHERE id=?",id);event(id,executionId,"RUNNING","Flink JobId="+submitted.jobId());
        }catch(RuntimeException ex){String m=msg(ex);jdbc.update("UPDATE realtime_sync_execution SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=?,local_log=? WHERE id=?",m,m,executionId);jdbc.update("UPDATE realtime_sync_definition SET observed_state='FAILED',last_error=? WHERE id=?",m,id);event(id,executionId,"FAILED",m);throw ex;}
        return runtime(id);
    }

    public RealtimeViews.Runtime stop(long id,String operator){
        RealtimeViews.Job job=get(id);
        RealtimeViews.Execution execution=latestExecution(id);
        jdbc.update("UPDATE realtime_sync_definition SET desired_state='STOPPED',observed_state='STOPPING' WHERE id=?",id);
        try{
            if(execution==null||execution.engineJobId()==null||job.runtimeEnvironmentId()==null){
                if(execution!=null) jdbc.update("UPDATE realtime_sync_execution SET status='STOPPED',finished_at=CURRENT_TIMESTAMP WHERE id=?",execution.id());
                jdbc.update("UPDATE realtime_sync_definition SET observed_state='STOPPED',last_error=NULL WHERE id=?",id);
                event(id,execution==null?null:execution.id(),"STOPPED","无活动 Flink Job，任务已停止 by "+operator(operator));
                return runtime(id);
            }
            FlinkEnvironmentView env=environments.get(job.runtimeEnvironmentId());
            try {
                gateway.cancel(env,execution.engineJobId());
            } catch (RuntimeException ex) {
                if (isJobNotFound(ex)) {
                    jdbc.update("UPDATE realtime_sync_execution SET status='STOPPED',finished_at=COALESCE(finished_at,CURRENT_TIMESTAMP),result_uncertain=FALSE,error_message=NULL WHERE id=?",execution.id());
                    jdbc.update("UPDATE realtime_sync_definition SET observed_state='STOPPED',desired_state='STOPPED',last_error=NULL WHERE id=?",id);
                    event(id,execution.id(),"STOPPED","Flink 中已不存在该 Job，平台状态已收敛为已停止 by "+operator(operator));
                    return runtime(id);
                }
                throw ex;
            }
            String terminal=waitForTerminal(env,execution.engineJobId(),10_000L);
            if(terminal==null){
                String m="Flink 取消请求已发送，但 10 秒内未确认终态";
                jdbc.update("UPDATE realtime_sync_definition SET observed_state='UNKNOWN',last_error=? WHERE id=?",m,id);
                jdbc.update("UPDATE realtime_sync_execution SET result_uncertain=TRUE,error_message=? WHERE id=?",m,execution.id());
                event(id,execution.id(),"STOP_UNCONFIRMED",m);
                throw new BadRequestException(m);
            }
            String observed=mapState(terminal);
            String executionState=mapExecution(terminal);
            jdbc.update("UPDATE realtime_sync_execution SET status=?,finished_at=CURRENT_TIMESTAMP,result_uncertain=FALSE,error_message=NULL WHERE id=?",executionState,execution.id());
            jdbc.update("UPDATE realtime_sync_definition SET observed_state=?,last_error=NULL WHERE id=?",observed,id);
            event(id,execution.id(),observed,"Flink 终态="+terminal+", 停止任务 by "+operator(operator));
            if("FAILED".equals(terminal)) throw new BadRequestException("Flink Job 在停止过程中进入 FAILED");
            return runtime(id);
        }catch(BadRequestException ex){throw ex;}catch(RuntimeException ex){
            String m=msg(ex);
            jdbc.update("UPDATE realtime_sync_definition SET observed_state='UNKNOWN',last_error=? WHERE id=?",m,id);
            if(execution!=null) jdbc.update("UPDATE realtime_sync_execution SET result_uncertain=TRUE,error_message=? WHERE id=?",m,execution.id());
            throw ex;
        }
    }

    public RealtimeViews.Runtime restart(long id,String operator){RealtimeViews.Job job=get(id);if(!"STOPPED".equals(job.observedState()))try{stop(id,operator);}catch(RuntimeException ignored){}return start(id,operator);}
    public RealtimeViews.Runtime applyPublishedVersion(long id,String operator){return restart(id,operator);}

    @Transactional
    public void remove(long id,String operator){
        RealtimeViews.Job job=get(id);
        if(Set.of("STARTING","RUNNING","STOPPING").contains(job.observedState())) throw new BadRequestException("实时任务正在运行，请先停止后再删除");
        jdbc.update("DELETE FROM realtime_validation_result WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_schema_change WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_schema_snapshot WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_checkpoint WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_sync_event WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_sync_execution WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_sync_version WHERE job_id=?",id);
        jdbc.update("DELETE FROM realtime_sync_definition WHERE id=?",id);
    }

    public RealtimeViews.Runtime runtime(long id){
        RealtimeViews.Job job=get(id);
        RealtimeViews.Execution execution=latestExecution(id);
        if(execution!=null&&execution.engineJobId()!=null&&job.runtimeEnvironmentId()!=null&&executionNeedsRefresh(execution.status())){
            try{
                JsonNode state=gateway.job(environments.get(job.runtimeEnvironmentId()),execution.engineJobId());
                String flink=state.path("state").asText("UNKNOWN");
                String observed=mapState(flink);
                String executionState=mapExecution(flink);
                jdbc.update("UPDATE realtime_sync_definition SET observed_state=?,last_error=NULL WHERE id=?",observed,id);
                if(Set.of("FINISHED","FAILED","CANCELED").contains(flink))
                    jdbc.update("UPDATE realtime_sync_execution SET status=?,finished_at=CURRENT_TIMESTAMP,result_uncertain=FALSE WHERE id=?",executionState,execution.id());
                else
                    jdbc.update("UPDATE realtime_sync_execution SET status=?,finished_at=NULL,result_uncertain=FALSE WHERE id=?",executionState,execution.id());
                job=get(id);
                execution=latestExecution(id);
            }catch(RuntimeException ignored){}
        }
        return new RealtimeViews.Runtime(job,execution,job.runtimeEnvironmentId()==null?null:environments.get(job.runtimeEnvironmentId()));
    }

    public void refreshAllRuntimeStates(){
        for(RealtimeViews.Job job:list()){
            try{ runtime(job.id()); }catch(RuntimeException ignored){}
        }
    }

    public JsonNode checkpoints(long id){RealtimeViews.Job job=get(id);RealtimeViews.Execution e=requireExecution(id);JsonNode raw=gateway.checkpoints(environments.get(requireEnv(job)),e.engineJobId());persistCheckpoint(id,e.id(),raw);return raw;}
    public JsonNode metrics(long id){RealtimeViews.Job job=get(id);RealtimeViews.Execution e=requireExecution(id);return gateway.metrics(environments.get(requireEnv(job)),e.engineJobId());}
    public String logs(long id){
        get(id);
        RealtimeViews.Execution e=latestExecution(id);
        if(e==null) return "暂无本地执行日志";
        String local=jdbc.query("SELECT local_log FROM realtime_sync_execution WHERE id=?",rs->rs.next()?rs.getString(1):null,e.id());
        if(local!=null&&!local.isBlank()) return local;
        if(e.errorMessage()!=null&&!e.errorMessage().isBlank()) return e.errorMessage();
        return "当前执行实例暂无本地日志";
    }
    public String yaml(long id){return configBuilder.build(get(id).spec(),true);}

    private Map<String,Object> publishedSpec(long id,int version){String json=jdbc.queryForObject("SELECT spec_json FROM realtime_sync_version WHERE job_id=? AND version_no=?",String.class,id,version);return parse(json);}
    private RealtimeViews.Execution latestExecution(long jobId){return jdbc.query("SELECT * FROM realtime_sync_execution WHERE job_id=? ORDER BY id DESC LIMIT 1",(rs,n)->execution(rs),jobId).stream().findFirst().orElse(null);}
    private RealtimeViews.Execution requireExecution(long id){RealtimeViews.Execution e=latestExecution(id);if(e==null||e.engineJobId()==null)throw new BadRequestException("实时任务尚无 Flink 运行实例");return e;}
    private long requireEnv(RealtimeViews.Job job){if(job.runtimeEnvironmentId()==null)throw new BadRequestException("未配置 Flink 环境");return job.runtimeEnvironmentId();}
    private void event(long jobId,Long executionId,String type,String detail){jdbc.update("INSERT INTO realtime_sync_event(job_id,execution_id,event_type,detail) VALUES(?,?,?,?)",jobId,executionId,type,detail);}
    private void persistCheckpoint(long jobId,long executionId,JsonNode raw){JsonNode latest=raw.path("latest").path("completed");if(latest.isMissingNode()||latest.isNull())return;long checkpointId=latest.path("id").asLong(0);if(checkpointId<=0)return;Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM realtime_checkpoint WHERE job_id=? AND checkpoint_id=?",Integer.class,jobId,checkpointId);if(count!=null&&count>0)return;jdbc.update("INSERT INTO realtime_checkpoint(job_id,execution_id,checkpoint_id,status,duration_ms,state_size_bytes,completed_at,raw_json) VALUES(?,?,?,'COMPLETED',?,?,CURRENT_TIMESTAMP,?)",jobId,executionId,checkpointId,latest.path("end_to_end_duration").asLong(0),latest.path("state_size").asLong(0),raw.toString());}
    private RealtimeViews.Job job(java.sql.ResultSet rs)throws java.sql.SQLException{int def=rs.getInt("definition_version");Integer pub=rs.getObject("published_version",Integer.class);var c=rs.getTimestamp("created_at");var u=rs.getTimestamp("updated_at");return new RealtimeViews.Job(rs.getLong("id"),rs.getString("name"),rs.getString("description"),rs.getObject("runtime_environment_id",Long.class),rs.getString("release_state"),rs.getString("desired_state"),rs.getString("observed_state"),def,pub,parse(rs.getString("spec_json")),rs.getString("config_digest"),rs.getString("last_error"),rs.getString("created_by"),c==null?null:c.toLocalDateTime(),u==null?null:u.toLocalDateTime(),pub!=null&&def>pub);}
    private RealtimeViews.Execution execution(java.sql.ResultSet rs)throws java.sql.SQLException{var s=rs.getTimestamp("started_at");var f=rs.getTimestamp("finished_at");var c=rs.getTimestamp("created_at");return new RealtimeViews.Execution(rs.getLong("id"),rs.getLong("job_id"),rs.getInt("definition_version"),rs.getString("engine_job_id"),rs.getString("runtime_revision"),rs.getString("status"),rs.getBoolean("result_uncertain"),rs.getString("error_message"),s==null?null:s.toLocalDateTime(),f==null?null:f.toLocalDateTime(),c==null?null:c.toLocalDateTime());}
    private Map<String,Object> parse(String value){if(value==null||value.isBlank())return new LinkedHashMap<>();try{return mapper.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception ex){throw new BadRequestException("实时同步配置无法解析");}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new BadRequestException("配置无法序列化");}}
    private String digest(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){return UUID.randomUUID().toString();}}
    private String operator(String value){return value==null||value.isBlank()?"admin":value.trim();}
    private String msg(Throwable ex){return ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();}
    private boolean isJobNotFound(Throwable ex){
        String value=msg(ex);
        return value.contains("Flink REST 返回 404") || value.contains("Job could not be found") || value.contains("Could not find Flink job");
    }
    private String waitForTerminal(FlinkEnvironmentView env,String engineJobId,long timeoutMs){
        long deadline=System.currentTimeMillis()+Math.max(1000L,timeoutMs);
        while(System.currentTimeMillis()<deadline){
            try {
                String state=gateway.job(env,engineJobId).path("state").asText("UNKNOWN");
                if(Set.of("FINISHED","CANCELED","FAILED").contains(state)) return state;
            } catch (RuntimeException ex) {
                if (isJobNotFound(ex)) return "CANCELED";
                throw ex;
            }
            try{Thread.sleep(250L);}catch(InterruptedException ex){Thread.currentThread().interrupt();return null;}
        }
        return null;
    }
    private String mapState(String s){return switch(s){case "RUNNING"->"RUNNING";case "CREATED","INITIALIZING","RECONCILING"->"STARTING";case "FINISHED","CANCELED"->"STOPPED";case "FAILED"->"FAILED";default->"UNKNOWN";};}
    private String mapExecution(String s){return switch(s){case "RUNNING"->"RUNNING";case "CREATED","INITIALIZING","RECONCILING"->"STARTING";case "FINISHED"->"FINISHED";case "CANCELED"->"STOPPED";case "FAILED"->"FAILED";default->"UNKNOWN";};}
    private boolean executionNeedsRefresh(String status){return !Set.of("FINISHED","FAILED","STOPPED","CANCELED","SUCCESS").contains(status==null?"":status.toUpperCase(Locale.ROOT));}
    private int intValue(Object value,int fallback){if(value==null)return fallback;try{return value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}catch(Exception ex){return fallback;}}
    public record Validation(boolean valid,String message,List<String>warnings,String yamlPreview,List<RealtimePreCheckService.Item> items){}
}
