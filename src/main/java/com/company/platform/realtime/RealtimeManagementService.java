package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.integration.MySqlToStarRocksTypeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RealtimeManagementService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DataSourceService dataSources;
    private final FlinkEnvironmentService environments;
    private final MySqlToStarRocksTypeMapper typeMapper;
    private final RealtimePreCheckService preCheck;

    public RealtimeManagementService(JdbcTemplate jdbc, ObjectMapper mapper, DataSourceService dataSources,
                                     FlinkEnvironmentService environments, MySqlToStarRocksTypeMapper typeMapper,
                                     RealtimePreCheckService preCheck) {
        this.jdbc = jdbc; this.mapper = mapper; this.dataSources = dataSources; this.environments = environments;
        this.typeMapper = typeMapper; this.preCheck = preCheck;
    }

    public List<ManagementRow> management() {
        return jdbc.query("SELECT * FROM realtime_sync_definition ORDER BY updated_at DESC", (rs,n) -> {
            long id = rs.getLong("id"); Map<String,Object> spec = parse(rs.getString("spec_json"));
            ExecutionSummary exec = latestExecution(id); CheckpointSummary cp = latestCheckpoint(id);
            Long envId = rs.getObject("runtime_environment_id", Long.class); String envName = null;
            if (envId != null) try { envName = environments.get(envId).name(); } catch (RuntimeException ignored) { }
            return new ManagementRow(id, rs.getString("name"), str(spec.get("sourceDataSourceId")), str(spec.get("sourceDatabase")),
                    str(spec.get("sinkDataSourceId")), str(spec.get("sinkDatabase")), tableCount(spec), scope(spec),
                    rs.getString("release_state"), rs.getString("observed_state"), envId, envName,
                    exec == null ? null : exec.engineJobId(), null,
                    cp == null ? "NOT_COLLECTED" : cp.status(), cp == null ? null : cp.completedAt(),
                    rs.getString("created_by"), timestamp(rs,"updated_at"), rs.getString("last_error"));
        });
    }

    public List<TableOption> tables(long dataSourceId, String database) {
        var info = dataSources.connectionInfo(dataSourceId);
        String db = required(database,"数据库");
        String sql = "SELECT t.TABLE_NAME,t.TABLE_COMMENT,t.TABLE_ROWS,"+
                "EXISTS(SELECT 1 FROM information_schema.statistics s WHERE s.TABLE_SCHEMA=t.TABLE_SCHEMA AND s.TABLE_NAME=t.TABLE_NAME AND s.INDEX_NAME='PRIMARY') has_pk "+
                "FROM information_schema.tables t WHERE t.TABLE_SCHEMA=? AND t.TABLE_TYPE='BASE TABLE' ORDER BY t.TABLE_NAME";
        try (Connection c=DriverManager.getConnection(info.jdbcUrl(),info.username(),info.password()); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1,db); try(ResultSet rs=ps.executeQuery()){List<TableOption> out=new ArrayList<>();while(rs.next()){
                boolean pk=rs.getBoolean("has_pk");out.add(new TableOption(rs.getString("TABLE_NAME"),rs.getString("TABLE_COMMENT"),rs.getLong("TABLE_ROWS"),pk,pk?"支持":"缺少主键"));}return out;}
        } catch(Exception ex){throw new BadRequestException("读取 MySQL 表信息失败："+safe(ex));}
    }

    public List<ColumnOption> columns(long dataSourceId,String database,String table) {
        var info=dataSources.connectionInfo(dataSourceId); String db=required(database,"数据库"), tbl=required(table,"表");
        String sql="SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_KEY,COLUMN_COMMENT,ORDINAL_POSITION FROM information_schema.columns WHERE TABLE_SCHEMA=? AND TABLE_NAME=? ORDER BY ORDINAL_POSITION";
        try(Connection c=DriverManager.getConnection(info.jdbcUrl(),info.username(),info.password());PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,db);ps.setString(2,tbl);try(ResultSet rs=ps.executeQuery()){List<ColumnOption> out=new ArrayList<>();while(rs.next()){
                String mysql=rs.getString("COLUMN_TYPE"), mapped;try{mapped=typeMapper.map(mysql);}catch(RuntimeException ex){mapped="不兼容";}
                out.add(new ColumnOption(rs.getString("COLUMN_NAME"),mysql,"PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY")),"YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),mapped,rs.getString("COLUMN_COMMENT"),rs.getInt("ORDINAL_POSITION")));}return out;}
        }catch(Exception ex){throw new BadRequestException("读取字段信息失败："+safe(ex));}
    }

    public List<ExecutionRow> executions(long jobId){return jdbc.query("SELECT * FROM realtime_sync_execution WHERE job_id=? ORDER BY created_at DESC LIMIT 100",(rs,n)->new ExecutionRow(rs.getLong("id"),rs.getInt("definition_version"),rs.getString("engine_job_id"),rs.getString("status"),rs.getBoolean("result_uncertain"),rs.getString("error_message"),timestamp(rs,"started_at"),timestamp(rs,"finished_at"),timestamp(rs,"created_at")),jobId);}
    public List<EventRow> events(long jobId){return jdbc.query("SELECT * FROM realtime_sync_event WHERE job_id=? ORDER BY created_at DESC LIMIT 200",(rs,n)->new EventRow(rs.getLong("id"),rs.getObject("execution_id",Long.class),rs.getString("event_type"),rs.getString("detail"),timestamp(rs,"created_at")),jobId);}
    public List<CheckpointRow> checkpointHistory(long jobId){return jdbc.query("SELECT * FROM realtime_checkpoint WHERE job_id=? ORDER BY created_at DESC LIMIT 100",(rs,n)->new CheckpointRow(rs.getLong("id"),rs.getObject("execution_id",Long.class),rs.getLong("checkpoint_id"),rs.getString("status"),rs.getLong("duration_ms"),rs.getLong("state_size_bytes"),timestamp(rs,"completed_at"),timestamp(rs,"created_at")),jobId);}
    public List<SchemaChangeRow> schemaChanges(long jobId){return jdbc.query("SELECT * FROM realtime_schema_change WHERE job_id=? ORDER BY occurred_at DESC LIMIT 200",(rs,n)->new SchemaChangeRow(rs.getLong("id"),rs.getString("source_table"),rs.getString("change_type"),rs.getString("ddl_text"),rs.getString("policy_action"),rs.getString("target_result"),rs.getString("status"),rs.getString("detail"),timestamp(rs,"occurred_at")),jobId);}

    public List<SchemaChangeRow> scanSchemaChanges(RealtimeViews.Job job) {
        Map<String,Object> spec=job.spec(); long sourceId=longValue(spec.get("sourceDataSourceId")); String sourceDb=str(spec.get("sourceDatabase"));
        Map<String,Object> policies=spec.get("schemaPolicies") instanceof Map<?,?> raw?(Map<String,Object>)raw:Map.of();
        for(Map<String,Object> table:tableMaps(spec)){
            String sourceTable=str(table.get("sourceTable")); if(sourceTable.isBlank())continue;
            List<ColumnOption> current=columns(sourceId,sourceDb,sourceTable); String currentJson=json(current);
            String previous=jdbc.query("SELECT schema_json FROM realtime_schema_snapshot WHERE job_id=? AND source_table=?",rs->rs.next()?rs.getString(1):null,job.id(),sourceTable);
            if(previous==null){jdbc.update("INSERT INTO realtime_schema_snapshot(job_id,source_table,schema_json) VALUES(?,?,?)",job.id(),sourceTable,currentJson);continue;}
            List<ColumnOption> before=parseColumns(previous); Map<String,ColumnOption> oldMap=indexColumns(before), newMap=indexColumns(current);
            List<ColumnOption> removed=before.stream().filter(c->!newMap.containsKey(c.name())).toList();
            List<ColumnOption> added=current.stream().filter(c->!oldMap.containsKey(c.name())).toList();
            if(removed.size()==1&&added.size()==1&&removed.get(0).mysqlType().equalsIgnoreCase(added.get(0).mysqlType())&&removed.get(0).ordinalPosition()==added.get(0).ordinalPosition()){
                ColumnOption a=removed.get(0),b=added.get(0);recordSchemaChange(job.id(),sourceTable,"RENAME_COLUMN","`"+a.name()+"` → `"+b.name()+"`",str(policies.getOrDefault("renameColumn","MANUAL")),"待人工确认","NEEDS_CONFIRMATION","基于字段位置和类型推断为可能的重命名；平台不会在无法确认 DDL 语义时自动删除目标字段");
            } else {
                for(ColumnOption c:added)recordSchemaChange(job.id(),sourceTable,"ADD_COLUMN","ADD COLUMN `"+c.name()+"` "+c.mysqlType(),str(policies.getOrDefault("addColumn","AUTO")),"由 Flink CDC Schema Evolution 处理","DETECTED","目标类型 "+c.starRocksType());
                for(ColumnOption c:removed)recordSchemaChange(job.id(),sourceTable,"DROP_COLUMN","DROP COLUMN `"+c.name()+"`",str(policies.getOrDefault("dropColumn","KEEP_TARGET")),"按策略保留/阻断","DETECTED","删除字段默认保留目标字段，避免破坏性 DDL");
            }
            for(ColumnOption c:current){ColumnOption old=oldMap.get(c.name());if(old!=null&&!old.mysqlType().equalsIgnoreCase(c.mysqlType()))recordSchemaChange(job.id(),sourceTable,"ALTER_COLUMN_TYPE","MODIFY COLUMN `"+c.name()+"` "+c.mysqlType(),str(policies.getOrDefault("typeChange","MANUAL")),"待兼容性检查","NEEDS_CONFIRMATION",old.mysqlType()+" → "+c.mysqlType()+" / StarRocks "+c.starRocksType());}
            jdbc.update("UPDATE realtime_schema_snapshot SET schema_json=?,updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND source_table=?",currentJson,job.id(),sourceTable);
        }
        return schemaChanges(job.id());
    }

    public List<ValidationRow> validationResults(long jobId){return jdbc.query("SELECT * FROM realtime_validation_result WHERE job_id=? ORDER BY checked_at DESC LIMIT 200",(rs,n)->validationRow(rs),jobId);}

    public List<ValidationRow> runValidation(RealtimeViews.Job job) {
        Map<String,Object> spec=job.spec(); long sourceId=longValue(spec.get("sourceDataSourceId")), sinkId=longValue(spec.get("sinkDataSourceId"));
        String sourceDb=str(spec.get("sourceDatabase")), sinkDb=str(spec.get("sinkDatabase"));
        List<Map<String,Object>> tables=tableMaps(spec); if(tables.isEmpty()) throw new BadRequestException("没有可校验的同步表");
        Long executionId=jdbc.query("SELECT id FROM realtime_sync_execution WHERE job_id=? ORDER BY created_at DESC LIMIT 1",rs->rs.next()?rs.getLong(1):null,job.id());
        var src=dataSources.connectionInfo(sourceId);var dst=dataSources.connectionInfo(sinkId);
        try(Connection sc=DriverManager.getConnection(src.jdbcUrl(),src.username(),src.password());Connection dc=DriverManager.getConnection(dst.jdbcUrl(),dst.username(),dst.password())){
            for(Map<String,Object> t:tables){String s=str(t.get("sourceTable")), target=str(t.getOrDefault("targetTable",s));
                long sourceCount=count(sc,sourceDb,s), targetCount=count(dc,sinkDb,target);String rowStatus=sourceCount==targetCount?"PASSED":"WARNING";
                insertValidation(job.id(),executionId,"ROW_COUNT",s,String.valueOf(sourceCount),String.valueOf(targetCount),rowStatus,sourceCount==targetCount?"源端与目标端当前行数一致":"实时任务可能存在延迟；请结合 Lag 与业务窗口继续核对");
                List<String> keys=primaryKeys(sc,sourceDb,s);if(keys.isEmpty()) insertValidation(job.id(),executionId,"PRIMARY_KEY",s,"无主键","—","FAILED","无法可靠校验 UPDATE / DELETE 一致性");
                else {long nullPk=nullPrimaryKey(dc,sinkDb,target,keys);insertValidation(job.id(),executionId,"PRIMARY_KEY_NULL",s,"0",String.valueOf(nullPk),nullPk==0?"PASSED":"FAILED","目标表主键 NULL 检查");
                    boolean dup=hasDuplicate(dc,sinkDb,target,keys);insertValidation(job.id(),executionId,"PRIMARY_KEY_DUPLICATE",s,"0",dup?">0":"0",dup?"FAILED":"PASSED","目标表主键重复检查");}
            }
        }catch(Exception ex){throw new BadRequestException("实时数据校验失败："+safe(ex));}
        RealtimePreCheckService.Report report=preCheck.check(spec);
        boolean typesOk=report.items().stream().noneMatch(i->i.blocking() && (i.code().contains("TYPE")||i.code().contains("TIMEZONE")||i.code().contains("ZERO_DATE")));
        insertValidation(job.id(),executionId,"TYPE_TIME_POLICY",null,"统一映射策略","实时预检",typesOk?"PASSED":"FAILED",typesOk?"字段类型与时区策略检查通过":"存在字段类型、零日期或时区阻断项");
        String deletePolicy=str(spec.getOrDefault("deletePolicy","SYNC_DELETE"));
        insertValidation(job.id(),executionId,"DELETE_POLICY",null,"MySQL DELETE",deletePolicy,"SYNC_DELETE".equals(deletePolicy)?"PASSED":"WARNING","该项验证删除同步配置策略；逐条 DELETE 审计需要变更事件采集链路");
        return validationResults(job.id()).stream().limit(50).toList();
    }

    private void recordSchemaChange(long jobId,String table,String type,String ddl,String action,String targetResult,String status,String detail){
        Integer existing=jdbc.queryForObject("SELECT COUNT(*) FROM realtime_schema_change WHERE job_id=? AND source_table=? AND change_type=? AND COALESCE(ddl_text,'')=? AND occurred_at>=CURRENT_TIMESTAMP-INTERVAL 1 DAY",Integer.class,jobId,table,type,ddl);
        if(existing!=null&&existing>0)return;
        jdbc.update("INSERT INTO realtime_schema_change(job_id,source_table,change_type,ddl_text,policy_action,target_result,status,detail) VALUES(?,?,?,?,?,?,?,?)",jobId,table,type,ddl,action,targetResult,status,detail);
    }
    private Map<String,ColumnOption> indexColumns(List<ColumnOption> columns){Map<String,ColumnOption> out=new LinkedHashMap<>();for(ColumnOption c:columns)out.put(c.name(),c);return out;}
    private List<ColumnOption> parseColumns(String value){try{return mapper.readValue(value,new TypeReference<List<ColumnOption>>(){});}catch(Exception ex){return List.of();}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception ex){throw new BadRequestException("实时 Schema 快照无法序列化");}}

    private void insertValidation(long jobId,Long executionId,String type,String table,String source,String target,String status,String detail){jdbc.update("INSERT INTO realtime_validation_result(job_id,execution_id,validation_type,source_table,source_value,target_value,status,detail) VALUES(?,?,?,?,?,?,?,?)",jobId,executionId,type,table,source,target,status,detail);}
    private ValidationRow validationRow(ResultSet rs)throws SQLException{return new ValidationRow(rs.getLong("id"),rs.getString("validation_type"),rs.getString("source_table"),rs.getString("source_value"),rs.getString("target_value"),rs.getString("status"),rs.getString("detail"),timestamp(rs,"checked_at"));}
    private long count(Connection c,String db,String table)throws SQLException{try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM "+id(db)+"."+id(table))){return rs.next()?rs.getLong(1):0;}}
    private List<String> primaryKeys(Connection c,String db,String table)throws SQLException{List<String> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT COLUMN_NAME FROM information_schema.statistics WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX")){ps.setString(1,db);ps.setString(2,table);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(rs.getString(1));}}return out;}
    private long nullPrimaryKey(Connection c,String db,String table,List<String> keys)throws SQLException{String where=String.join(" OR ",keys.stream().map(k->id(k)+" IS NULL").toList());try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM "+id(db)+"."+id(table)+" WHERE "+where)){return rs.next()?rs.getLong(1):0;}}
    private boolean hasDuplicate(Connection c,String db,String table,List<String> keys)throws SQLException{String group=String.join(",",keys.stream().map(this::id).toList());try(Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT 1 FROM "+id(db)+"."+id(table)+" GROUP BY "+group+" HAVING COUNT(*)>1 LIMIT 1")){return rs.next();}}
    private ExecutionSummary latestExecution(long jobId){return jdbc.query("SELECT engine_job_id,status,started_at FROM realtime_sync_execution WHERE job_id=? ORDER BY created_at DESC LIMIT 1",rs->rs.next()?new ExecutionSummary(rs.getString(1),rs.getString(2),timestamp(rs,"started_at")):null,jobId);}
    private CheckpointSummary latestCheckpoint(long jobId){return jdbc.query("SELECT status,completed_at FROM realtime_checkpoint WHERE job_id=? ORDER BY created_at DESC LIMIT 1",rs->rs.next()?new CheckpointSummary(rs.getString(1),timestamp(rs,"completed_at")):null,jobId);}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> tableMaps(Map<String,Object> spec){Object raw=spec.get("tables");if(!(raw instanceof List<?> l))return List.of();return l.stream().filter(Map.class::isInstance).map(x->(Map<String,Object>)x).toList();}
    private int tableCount(Map<String,Object> spec){return tableMaps(spec).size();}
    private String scope(Map<String,Object> spec){String v=str(spec.get("syncScope"));return v.isBlank()?(tableCount(spec)==1?"SINGLE_TABLE":"MULTI_TABLE"):v;}
    private Map<String,Object> parse(String json){if(json==null||json.isBlank())return new LinkedHashMap<>();try{return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception ex){return new LinkedHashMap<>();}}
    private long longValue(Object v){try{return v instanceof Number n?n.longValue():Long.parseLong(str(v));}catch(Exception ex){return 0;}}
    private String str(Object v){return v==null?"":String.valueOf(v).trim();}
    private String required(String v,String name){if(v==null||v.isBlank())throw new BadRequestException("请选择"+name);return v.trim();}
    private String id(String v){return "`"+String.valueOf(v).replace("`","``")+"`";}
    private String safe(Throwable ex){return ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage().replaceAll("(?i)(password=)[^&\\s]+","$1***");}
    private LocalDateTime timestamp(ResultSet rs,String name)throws SQLException{Timestamp t=rs.getTimestamp(name);return t==null?null:t.toLocalDateTime();}

    private record ExecutionSummary(String engineJobId,String status,LocalDateTime startedAt){}
    private record CheckpointSummary(String status,LocalDateTime completedAt){}
    public record ManagementRow(long id,String name,String sourceDataSourceId,String sourceDatabase,String sinkDataSourceId,String sinkDatabase,int tableCount,String syncScope,String releaseState,String observedState,Long runtimeEnvironmentId,String environmentName,String engineJobId,Long lagMs,String checkpointStatus,LocalDateTime checkpointAt,String owner,LocalDateTime updatedAt,String lastError){}
    public record TableOption(String name,String comment,long estimatedRows,boolean primaryKey,String cdcStatus){}
    public record ColumnOption(String name,String mysqlType,boolean primaryKey,boolean nullable,String starRocksType,String comment,int ordinalPosition){}
    public record ExecutionRow(long id,int definitionVersion,String engineJobId,String status,boolean resultUncertain,String errorMessage,LocalDateTime startedAt,LocalDateTime finishedAt,LocalDateTime createdAt){}
    public record EventRow(long id,Long executionId,String eventType,String detail,LocalDateTime createdAt){}
    public record CheckpointRow(long id,Long executionId,long checkpointId,String status,long durationMs,long stateSizeBytes,LocalDateTime completedAt,LocalDateTime createdAt){}
    public record SchemaChangeRow(long id,String sourceTable,String changeType,String ddlText,String policyAction,String targetResult,String status,String detail,LocalDateTime occurredAt){}
    public record ValidationRow(long id,String validationType,String sourceTable,String sourceValue,String targetValue,String status,String detail,LocalDateTime checkedAt){}
}
