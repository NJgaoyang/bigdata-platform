package com.company.platform.release;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.development.DevelopmentService;
import com.company.platform.development.DevelopmentScheduleService;
import com.company.platform.metric.MetricService;
import com.company.platform.realtime.RealtimeSyncService;
import com.company.platform.workflow.WorkflowPublishService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ReleaseService {
    private final JdbcTemplate jdbc;
    private final WorkflowPublishService workflows;
    private final RealtimeSyncService realtime;
    private final DevelopmentService development;
    private final DevelopmentScheduleService developmentSchedules;
    private final MetricService metrics;
    private final ObjectMapper mapper;
    public ReleaseService(JdbcTemplate jdbc,WorkflowPublishService workflows,RealtimeSyncService realtime,DevelopmentService development,DevelopmentScheduleService developmentSchedules,MetricService metrics,ObjectMapper mapper){this.jdbc=jdbc;this.workflows=workflows;this.realtime=realtime;this.development=development;this.developmentSchedules=developmentSchedules;this.metrics=metrics;this.mapper=mapper;}

    public Policy policy(){return jdbc.query("SELECT id,policy_key,approval_required,updated_by,updated_at FROM release_policy WHERE policy_key='production'",(rs,n)->new Policy(rs.getLong(1),rs.getString(2),rs.getBoolean(3),rs.getString(4),rs.getTimestamp(5).toLocalDateTime())).stream().findFirst().orElse(new Policy(0,"production",false,"system",LocalDateTime.now()));}
    @Transactional public Policy updatePolicy(boolean required,String operator){jdbc.update("INSERT INTO release_policy(policy_key,approval_required,updated_by) VALUES('production',?,?) ON DUPLICATE KEY UPDATE approval_required=VALUES(approval_required),updated_by=VALUES(updated_by)",required,operator(operator));return policy();}
    public List<RequestView> requests(String status){String sql="SELECT * FROM release_request"+(status==null||status.isBlank()?"":" WHERE status=?")+" ORDER BY requested_at DESC";return status==null||status.isBlank()?jdbc.query(sql,(rs,n)->request(rs)):jdbc.query(sql,(rs,n)->request(rs),status);}
    public List<RecordView> records(){return jdbc.query("SELECT * FROM release_record ORDER BY released_at DESC LIMIT 500",(rs,n)->record(rs));}

    @Transactional
    public RequestView request(ReleaseRequest r,String operator){String type=normalizeType(r.resourceType());String payload=json(r.payload());boolean approval=policy().approvalRequired();String status=approval?"PENDING_APPROVAL":"AUTO_APPROVED";
        jdbc.update("INSERT INTO release_request(resource_type,resource_id,resource_name,requested_version,payload_json,status,requested_by,reviewed_by,reviewed_at) VALUES(?,?,?,?,?,?,?, ?, CASE WHEN ?='AUTO_APPROVED' THEN CURRENT_TIMESTAMP ELSE NULL END)",type,r.resourceId(),r.resourceName(),r.requestedVersion(),payload,status,operator(operator),approval?null:"system",status);
        long id=jdbc.queryForObject("SELECT id FROM release_request WHERE resource_type=? AND resource_id=? ORDER BY id DESC LIMIT 1",Long.class,type,r.resourceId());
        if(approval && "METRIC".equals(type)) metrics.markPending(r.resourceId());
        if(!approval) execute(id,operator(operator));return get(id);
    }

    @Transactional
    public RequestView approve(long id,String comment,String operator){RequestView current=get(id);if(!"PENDING_APPROVAL".equals(current.status()))throw new BadRequestException("发布申请当前不是待审批状态");jdbc.update("UPDATE release_request SET status='APPROVED',reviewed_by=?,review_comment=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",operator(operator),comment,id);execute(id,operator(operator));return get(id);}
    @Transactional public RequestView reject(long id,String comment,String operator){RequestView current=get(id);if(!"PENDING_APPROVAL".equals(current.status()))throw new BadRequestException("发布申请当前不是待审批状态");jdbc.update("UPDATE release_request SET status='REJECTED',reviewed_by=?,review_comment=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?",operator(operator),comment,id);if("METRIC".equals(current.resourceType()))metrics.markRejected(current.resourceId());return get(id);}
    public RequestView get(long id){return jdbc.query("SELECT * FROM release_request WHERE id=?",(rs,n)->request(rs),id).stream().findFirst().orElseThrow(()->new NotFoundException("发布申请不存在："+id));}

    private void execute(long requestId,String operator){RequestView request=get(requestId);int releasedVersion=request.requestedVersion()==null?0:request.requestedVersion();String detail="";String result="SUCCESS";
        try{switch(request.resourceType()){
            case "WORKFLOW" -> {var r=workflows.publish(request.resourceId());releasedVersion=r.version();detail=r.message();}
            case "REALTIME" -> {var r=realtime.publish(request.resourceId(),operator);releasedVersion=r.publishedVersion()==null?r.definitionVersion():r.publishedVersion();detail="实时同步版本已发布";}
            case "DEVELOPMENT" -> {var r=development.publishFile(request.resourceId(),operator);releasedVersion=r.currentVersion();String remark=developmentRemark(requestId);var bundle=developmentSchedules.publish(request.resourceId(),releasedVersion,operator,remark);detail="开发任务 V"+releasedVersion+" 已发布（代码、调度与依赖统一生效）";}
            case "METRIC" -> {var r=metrics.publish(request.resourceId(),releasedVersion,operator);releasedVersion=r.currentVersion();detail="指标 V"+releasedVersion+" 已发布";}
            default -> throw new BadRequestException("不支持的发布资源类型："+request.resourceType());
        }}catch(RuntimeException ex){result="FAILED";detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();jdbc.update("UPDATE release_request SET status='FAILED',review_comment=? WHERE id=?",detail,requestId);insertRecord(request,releasedVersion,result,detail,operator);throw ex;}
        jdbc.update("UPDATE release_request SET status='RELEASED' WHERE id=?",requestId);insertRecord(request,releasedVersion,result,detail,operator);
    }
    private void insertRecord(RequestView r,int version,String result,String detail,String operator){jdbc.update("INSERT INTO release_record(request_id,resource_type,resource_id,resource_name,released_version,result_status,detail,operator_name) VALUES(?,?,?,?,?,?,?,?)",r.id(),r.resourceType(),r.resourceId(),r.resourceName(),version,result,detail,operator);}
    private String developmentRemark(long requestId){try{String raw=jdbc.queryForObject("SELECT payload_json FROM release_request WHERE id=?",String.class,requestId);if(raw==null||raw.isBlank())return "统一发布";var node=mapper.readTree(raw);return node.hasNonNull("remark")?node.get("remark").asText("统一发布"):"统一发布";}catch(Exception ex){return "统一发布";}}
    private String normalizeType(String type){String v=type==null?"":type.trim().toUpperCase(Locale.ROOT);if(!List.of("WORKFLOW","REALTIME","DEVELOPMENT","METRIC").contains(v))throw new BadRequestException("resourceType 仅支持 WORKFLOW / REALTIME / DEVELOPMENT / METRIC");return v;}
    private String json(Object value){try{return value==null?"{}":mapper.writeValueAsString(value);}catch(Exception ex){return "{}";}}
    private String operator(String v){return v==null||v.isBlank()?"admin":v.trim();}
    private RequestView request(java.sql.ResultSet rs)throws java.sql.SQLException{var a=rs.getTimestamp("requested_at");var b=rs.getTimestamp("reviewed_at");return new RequestView(rs.getLong("id"),rs.getString("resource_type"),rs.getLong("resource_id"),rs.getString("resource_name"),rs.getObject("requested_version",Integer.class),rs.getString("status"),rs.getString("requested_by"),rs.getString("reviewed_by"),rs.getString("review_comment"),a==null?null:a.toLocalDateTime(),b==null?null:b.toLocalDateTime());}
    private RecordView record(java.sql.ResultSet rs)throws java.sql.SQLException{var t=rs.getTimestamp("released_at");return new RecordView(rs.getLong("id"),rs.getObject("request_id",Long.class),rs.getString("resource_type"),rs.getLong("resource_id"),rs.getString("resource_name"),rs.getObject("released_version",Integer.class),rs.getString("result_status"),rs.getString("detail"),rs.getString("operator_name"),t==null?null:t.toLocalDateTime());}
    public record Policy(long id,String policyKey,boolean approvalRequired,String updatedBy,LocalDateTime updatedAt){}
    public record ReleaseRequest(String resourceType,long resourceId,String resourceName,Integer requestedVersion,Object payload){}
    public record ReviewRequest(String comment){}
    public record RequestView(long id,String resourceType,long resourceId,String resourceName,Integer requestedVersion,String status,String requestedBy,String reviewedBy,String reviewComment,LocalDateTime requestedAt,LocalDateTime reviewedAt){}
    public record RecordView(long id,Long requestId,String resourceType,long resourceId,String resourceName,Integer releasedVersion,String resultStatus,String detail,String operatorName,LocalDateTime releasedAt){}
}
