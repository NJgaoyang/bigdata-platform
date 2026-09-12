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

    public RealtimeSyncService(JdbcTemplate jdbc, ObjectMapper mapper, FlinkEnvironmentService environments,
                               FlinkCdcConfigBuilder configBuilder, FlinkCdcGateway gateway) {
        this.jdbc=jdbc;this.mapper=mapper;this.environments=environments;this.configBuilder=configBuilder;this.gateway=gateway;
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
        event(id,null,"CREATED","创建实时同步草稿 V1");return get(id);
    }

    @Transactional
    public RealtimeViews.Job saveDraft(long id,RealtimeRequests.DraftRequest request,String operator){
        RealtimeViews.Job current=get(id);Map<String,Object> spec=new LinkedHashMap<>(request.spec()==null?current.spec():request.spec());
        String name=request.name()==null||request.name().isBlank()?current.name():request.name().trim();spec.put("name",name);String json=json(spec),digest=digest(json);int version=current.definitionVersion()+1;
        Long env=request.runtimeEnvironmentId()==null?current.runtimeEnvironmentId():request.runtimeEnvironmentId();String desc=request.description()==null?current.description():request.description();
        jdbc.update("UPDATE realtime_sync_definition SET name=?,description=?,runtime_environment_id=?,release_state='DRAFT',definition_version=?,spec_json=?,config_digest=?,last_error=NULL WHERE id=?",name,desc,env,version,json,digest,id);
        jdbc.update("INSERT INTO realtime_sync_version(job_id,version_no,spec_json,config_digest,published,created_by) VALUES(?,?,?,?,FALSE,?)",id,version,json,digest,operator(operator));
        event(id,null,"DRAFT_SAVED","保存草稿 V"+version);return get(id);
    }

    public Validation validate(long id){RealtimeViews.Job job=get(id);List<String> warnings=new ArrayList<>();configBuilder.validate(job.spec());if(job.runtimeEnvironmentId()==null)warnings.add("尚未选择 Flink 运行环境，发布前必须配置");else if(!environments.get(job.runtimeEnvironmentId()).enabled())warnings.add("Flink 运行环境已禁用");return new Validation(true,"配置校验通过",warnings,configBuilder.build(job.spec(),true));}

    @Transactional
    public RealtimeViews.Job publish(long id,String operator){RealtimeViews.Job job=get(id);configBuilder.validate(job.spec());if(job.runtimeEnvironmentId()==null)throw new BadRequestException("发布前必须选择 Flink 运行环境");environments.get(job.runtimeEnvironmentId());
        jdbc.update("UPDATE realtime_sync_version SET published=(version_no=?) WHERE job_id=?",job.definitionVersion(),id);
        jdbc.update("UPDATE realtime_sync_definition SET release_state='PUBLISHED',published_version=?,last_error=NULL WHERE id=?",job.definitionVersion(),id);
        event(id,null,"PUBLISHED","发布 V"+job.definitionVersion()+" by "+operator(operator));return get(id);
    }

    @Transactional
    public RealtimeViews.Runtime start(long id,String operator){RealtimeViews.Job job=get(id);if(job.publishedVersion()==null)throw new BadRequestException("请先发布实时同步任务");if(job.runtimeEnvironmentId()==null)throw new BadRequestException("未配置 Flink 运行环境");
        Map<String,Object> spec=publishedSpec(id,job.publishedVersion());FlinkEnvironmentService.RuntimeEnvironment env=environments.runtime(job.runtimeEnvironmentId());String yaml=configBuilder.build(spec,false);
        jdbc.update("UPDATE realtime_sync_definition SET desired_state='RUNNING',observed_state='STARTING',last_error=NULL WHERE id=?",id);
        jdbc.update("INSERT INTO realtime_sync_execution(job_id,definition_version,runtime_environment_snapshot,status,started_at) VALUES(?,?,?,'STARTING',CURRENT_TIMESTAMP)",id,job.publishedVersion(),json(env.view()));
        long executionId=Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM realtime_sync_execution WHERE job_id=? ORDER BY id DESC LIMIT 1",Long.class,id));event(id,executionId,"STARTING","提交 Flink CDC V"+job.publishedVersion());
        try{
            FlinkCdcGateway.SubmitResult submitted=gateway.submit(env,yaml);
            jdbc.update("UPDATE realtime_sync_execution SET engine_job_id=?,status='RUNNING',runtime_revision=? WHERE id=?",submitted.jobId(),"v"+job.publishedVersion(),executionId);
            jdbc.update("UPDATE realtime_sync_definition SET observed_state='RUNNING' WHERE id=?",id);event(id,executionId,"RUNNING","Flink JobId="+submitted.jobId());
        }catch(RuntimeException ex){String m=msg(ex);jdbc.update("UPDATE realtime_sync_execution SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE id=?",m,executionId);jdbc.update("UPDATE realtime_sync_definition SET observed_state='FAILED',last_error=? WHERE id=?",m,id);event(id,executionId,"FAILED",m);throw ex;}
        return runtime(id);
    }

    @Transactional
    public RealtimeViews.Runtime stop(long id,String operator){RealtimeViews.Job job=get(id);RealtimeViews.Execution execution=latestExecution(id);jdbc.update("UPDATE realtime_sync_definition SET desired_state='STOPPED',observed_state='STOPPING' WHERE id=?",id);
        try{if(execution!=null&&execution.engineJobId()!=null&&job.runtimeEnvironmentId()!=null)gateway.cancel(environments.get(job.runtimeEnvironmentId()),execution.engineJobId());
            if(execution!=null)jdbc.update("UPDATE realtime_sync_execution SET status='STOPPED',finished_at=CURRENT_TIMESTAMP WHERE id=?",execution.id());jdbc.update("UPDATE realtime_sync_definition SET observed_state='STOPPED',last_error=NULL WHERE id=?",id);event(id,execution==null?null:execution.id(),"STOPPED","停止任务 by "+operator(operator));
        }catch(RuntimeException ex){String m=msg(ex);jdbc.update("UPDATE realtime_sync_definition SET observed_state='UNKNOWN',last_error=? WHERE id=?",m,id);if(execution!=null)jdbc.update("UPDATE realtime_sync_execution SET result_uncertain=TRUE,error_message=? WHERE id=?",m,execution.id());throw ex;}
        return runtime(id);
    }

    public RealtimeViews.Runtime restart(long id,String operator){RealtimeViews.Job job=get(id);if(!"STOPPED".equals(job.observedState()))try{stop(id,operator);}catch(RuntimeException ignored){}return start(id,operator);}
    public RealtimeViews.Runtime applyPublishedVersion(long id,String operator){return restart(id,operator);}

    public RealtimeViews.Runtime runtime(long id){
        RealtimeViews.Job job=get(id);
        RealtimeViews.Execution execution=latestExecution(id);
        if(execution!=null&&execution.engineJobId()!=null&&job.runtimeEnvironmentId()!=null&&Set.of("RUNNING","STARTING","UNKNOWN").contains(job.observedState())){
            try{
                JsonNode state=gateway.job(environments.get(job.runtimeEnvironmentId()),execution.engineJobId());
                String flink=state.path("state").asText("UNKNOWN");
                String observed=mapState(flink);
                jdbc.update("UPDATE realtime_sync_definition SET observed_state=? WHERE id=?",observed,id);
                if(Set.of("FINISHED","FAILED","CANCELED").contains(flink)) jdbc.update("UPDATE realtime_sync_execution SET status=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",mapExecution(flink),execution.id());
                job=get(id);
                execution=latestExecution(id);
            }catch(RuntimeException ignored){}
        }
        return new RealtimeViews.Runtime(job,execution,job.runtimeEnvironmentId()==null?null:environments.get(job.runtimeEnvironmentId()));
    }

    public JsonNode checkpoints(long id){RealtimeViews.Job job=get(id);RealtimeViews.Execution e=requireExecution(id);JsonNode raw=gateway.checkpoints(environments.get(requireEnv(job)),e.engineJobId());persistCheckpoint(id,e.id(),raw);return raw;}
    public JsonNode metrics(long id){RealtimeViews.Job job=get(id);RealtimeViews.Execution e=requireExecution(id);return gateway.metrics(environments.get(requireEnv(job)),e.engineJobId());}
    public JsonNode logs(long id){RealtimeViews.Job job=get(id);RealtimeViews.Execution e=requireExecution(id);return gateway.exceptions(environments.get(requireEnv(job)),e.engineJobId());}
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
    private String mapState(String s){return switch(s){case "RUNNING"->"RUNNING";case "CREATED","INITIALIZING","RECONCILING"->"STARTING";case "FINISHED","CANCELED"->"STOPPED";case "FAILED"->"FAILED";default->"UNKNOWN";};}
    private String mapExecution(String s){return switch(s){case "FINISHED"->"FINISHED";case "CANCELED"->"STOPPED";case "FAILED"->"FAILED";default->s;};}
    public record Validation(boolean valid,String message,List<String>warnings,String yamlPreview){}
}
