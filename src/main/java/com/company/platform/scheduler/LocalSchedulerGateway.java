package com.company.platform.scheduler;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.integration.IntegrationService;
import com.company.platform.integration.SeaTunnelGateway;
import com.company.platform.query.QueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.quartz.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** DataSphere-owned scheduler. Quartz only fires cron triggers; DAG execution and state belong to the platform. */
@Component
@Primary
@ConditionalOnProperty(prefix = "platform.scheduler", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalSchedulerGateway implements SchedulerGateway {
    private final JdbcTemplate jdbc;
    private final Scheduler quartz;
    private final QueryService queryService;
    private final IntegrationService integrationService;
    private final PlatformStore store;
    private final ObjectMapper mapper;
    private final ExecutorService workflowPool = Executors.newFixedThreadPool(20, Thread.ofPlatform().name("workflow-", 0).factory());
    private final ExecutorService taskPool = Executors.newFixedThreadPool(30, Thread.ofPlatform().name("workflow-task-", 0).factory());
    private final Map<String, Future<?>> running = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public LocalSchedulerGateway(JdbcTemplate jdbc, Scheduler quartz, QueryService queryService,
                                 IntegrationService integrationService, PlatformStore store, ObjectMapper mapper) {
        this.jdbc = jdbc; this.quartz = quartz; this.queryService = queryService;
        this.integrationService = integrationService; this.store = store; this.mapper = mapper;
    }

    @Override
    public PublishResult publish(PublishRequest request) {
        jdbc.update("INSERT INTO workflow_definition_version(workflow_code,workflow_name,version_no,definition_json,status) VALUES(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE workflow_name=VALUES(workflow_name),definition_json=VALUES(definition_json),status=VALUES(status)",
                request.workflowCode(), request.name(), request.version(), request.definitionJson(), "PUBLISHED");
        return new PublishResult(request.workflowCode(), request.version(), "PUBLISHED");
    }

    @Override public RunResult run(String processCode) { return runWithType(processCode, "MANUAL", "{}"); }

    public RunResult runScheduled(String processCode) { return runWithType(processCode, "SCHEDULED", "{}"); }

    private RunResult runWithType(String processCode, String runType, String parametersJson) {
        Definition definition = definition(processCode, null);
        String instanceCode = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO workflow_instance(instance_code,workflow_code,definition_version,run_type,status,parameters_json,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                instanceCode, processCode, definition.version(), runType, "QUEUED", parametersJson);
        submit(instanceCode, processCode, definition.version());
        return new RunResult(instanceCode, "QUEUED");
    }

    @Override
    public InstanceStatus status(String instanceId) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT instance_code,status,error_message FROM workflow_instance WHERE instance_code=? OR CAST(id AS CHAR)=? LIMIT 1", instanceId, instanceId);
        if (rows.isEmpty()) throw new BadRequestException("工作流实例不存在：" + instanceId);
        Map<String,Object> row = rows.getFirst();
        String code = String.valueOf(row.get("instance_code"));
        return new InstanceStatus(code, String.valueOf(row.get("status")), workflowLog(code));
    }

    @Override
    public void stop(String instanceId) {
        String code = resolveInstanceCode(instanceId);
        cancelFlags.computeIfAbsent(code, ignored -> new AtomicBoolean()).set(true);
        jdbc.update("UPDATE workflow_instance SET status='STOPPING' WHERE instance_code=? AND status IN ('QUEUED','RUNNING')", code);
        Future<?> future = running.get(code);
        if (future != null) future.cancel(true);
        jdbc.update("UPDATE workflow_instance SET status='STOPPED',finished_at=CURRENT_TIMESTAMP,error_message='用户停止' WHERE instance_code=? AND status='STOPPING'", code);
    }

    @Override
    public RunResult rerun(String instanceId) {
        Map<String,Object> row = instanceRow(instanceId);
        return runWithType(String.valueOf(row.get("workflow_code")), "RERUN", "{\"sourceInstance\":\"" + resolveInstanceCode(instanceId) + "\"}");
    }

    @Override
    public RunResult backfill(String processCode, String start, String end, int parallelism) {
        String payload;
        try { payload = mapper.writeValueAsString(Map.of("start", start, "end", end, "parallelism", parallelism)); }
        catch (Exception ex) { payload = "{}"; }
        return runWithType(processCode, "BACKFILL", payload);
    }

    @Override public void release(String processCode, boolean online) { /* Definition state is owned by platform tables. */ }

    @Override
    public String upsertSchedule(String processCode, String cronExpression, String timezone, boolean enabled,
                                 String failureStrategy, int parallelism) {
        String triggerCode = "schedule_" + processCode;
        jdbc.update("INSERT INTO scheduler_trigger(trigger_code,workflow_code,cron_expression,timezone,enabled,failure_strategy,parallelism) VALUES(?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE cron_expression=VALUES(cron_expression),timezone=VALUES(timezone),enabled=VALUES(enabled),failure_strategy=VALUES(failure_strategy),parallelism=VALUES(parallelism)",
                triggerCode, processCode, cronExpression, timezone, enabled, failureStrategy, parallelism);
        try {
            JobKey jobKey = new JobKey("workflow_" + processCode, "datasphere");
            JobDetail detail = JobBuilder.newJob(WorkflowQuartzJob.class).withIdentity(jobKey)
                    .usingJobData("workflowCode", processCode).storeDurably(true).build();
            if (quartz.checkExists(jobKey)) quartz.addJob(detail, true); else quartz.addJob(detail, false);
            TriggerKey triggerKey = new TriggerKey(triggerCode, "datasphere");
            CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(cronExpression)
                    .inTimeZone(TimeZone.getTimeZone(timezone)).withMisfireHandlingInstructionDoNothing();
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).forJob(jobKey).withSchedule(schedule).build();
            if (quartz.checkExists(triggerKey)) quartz.rescheduleJob(triggerKey, trigger); else quartz.scheduleJob(trigger);
            if (!enabled) quartz.pauseTrigger(triggerKey);
            return triggerCode;
        } catch (SchedulerException ex) {
            throw new BadRequestException("本地调度配置失败：" + ex.getMessage());
        }
    }

    @Override
    public void scheduleState(String scheduleId, boolean online) {
        try {
            TriggerKey key = new TriggerKey(scheduleId, "datasphere");
            if (online) quartz.resumeTrigger(key); else quartz.pauseTrigger(key);
            jdbc.update("UPDATE scheduler_trigger SET enabled=? WHERE trigger_code=?", online, scheduleId);
        } catch (SchedulerException ex) { throw new BadRequestException("调度状态切换失败：" + ex.getMessage()); }
    }

    @Override
    public List<Map<String, Object>> listProcessInstances() {
        return jdbc.queryForList("SELECT id,instance_code AS instanceId,workflow_code AS processCode,run_type AS runType,status,started_at AS startedAt,finished_at AS finishedAt,error_message AS errorMessage,created_at AS createdAt FROM workflow_instance ORDER BY created_at DESC LIMIT 200");
    }

    @Override
    public List<Map<String, Object>> listTaskInstances() {
        return jdbc.queryForList("SELECT ti.id,wi.instance_code AS processInstanceId,ti.node_id AS nodeId,ti.node_name AS name,ti.node_type AS type,ti.attempt_no AS attemptNo,ti.status,ti.started_at AS startedAt,ti.finished_at AS finishedAt,ti.error_message AS errorMessage FROM task_instance ti JOIN workflow_instance wi ON wi.id=ti.workflow_instance_id ORDER BY ti.created_at DESC LIMIT 500");
    }

    @Override
    public String taskLog(String taskInstanceId) {
        try {
            long id = Long.parseLong(taskInstanceId);
            return String.join("\n", jdbc.query("SELECT message FROM task_log WHERE task_instance_id=? ORDER BY id", (rs, n) -> rs.getString(1), id));
        } catch (NumberFormatException ex) { return "任务实例编号无效"; }
    }

    @Override public boolean isRealMode() { return true; }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverIncompleteInstances() {
        jdbc.queryForList("SELECT instance_code,workflow_code,definition_version FROM workflow_instance WHERE status IN ('QUEUED','RUNNING','STOPPING') ORDER BY created_at")
                .forEach(row -> {
                    String code = String.valueOf(row.get("instance_code"));
                    jdbc.update("UPDATE workflow_instance SET status='QUEUED',error_message='服务重启后恢复执行' WHERE instance_code=?", code);
                    submit(code, String.valueOf(row.get("workflow_code")), ((Number)row.get("definition_version")).intValue());
                });
    }

    private void submit(String instanceCode, String workflowCode, int version) {
        AtomicBoolean flag = cancelFlags.computeIfAbsent(instanceCode, ignored -> new AtomicBoolean(false));
        Future<?> future = workflowPool.submit(() -> executeInstance(instanceCode, workflowCode, version, flag));
        running.put(instanceCode, future);
    }

    private void executeInstance(String instanceCode, String workflowCode, int version, AtomicBoolean cancelled) {
        try {
            jdbc.update("UPDATE workflow_instance SET status='RUNNING',started_at=COALESCE(started_at,CURRENT_TIMESTAMP),error_message=NULL WHERE instance_code=?", instanceCode);
            Definition definition = definition(workflowCode, version);
            JsonNode root = mapper.readTree(definition.json());
            Map<Long, JsonNode> nodes = new LinkedHashMap<>();
            Map<Long, Integer> indegree = new HashMap<>();
            Map<Long, List<Long>> outgoing = new HashMap<>();
            root.path("nodes").forEach(node -> { long id=node.path("id").asLong(); nodes.put(id,node); indegree.put(id,0); });
            root.path("edges").forEach(edge -> {
                long s=edge.path("sourceNodeId").asLong(), t=edge.path("targetNodeId").asLong();
                outgoing.computeIfAbsent(s, ignored -> new ArrayList<>()).add(t); indegree.compute(t,(k,v)->v==null?1:v+1);
            });
            List<Long> ready = indegree.entrySet().stream().filter(e -> e.getValue()==0).map(Map.Entry::getKey).toList();
            Set<Long> completed = new HashSet<>();
            while (!ready.isEmpty()) {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("工作流已停止");
                List<Future<TaskOutcome>> futures = new ArrayList<>();
                for (Long nodeId : ready) futures.add(taskPool.submit(() -> executeNode(instanceCode, nodes.get(nodeId), cancelled)));
                List<Long> next = new ArrayList<>();
                for (int i=0;i<futures.size();i++) {
                    TaskOutcome outcome = futures.get(i).get();
                    long nodeId = ready.get(i);
                    if (!outcome.success()) throw new IllegalStateException(outcome.message());
                    completed.add(nodeId);
                    for (Long target : outgoing.getOrDefault(nodeId,List.of())) {
                        int remaining = indegree.compute(target,(k,v)->Math.max(0,(v==null?0:v)-1));
                        if (remaining==0) next.add(target);
                    }
                }
                ready = next;
            }
            if (completed.size()!=nodes.size()) throw new IllegalStateException("DAG 未能完成，可能存在循环或不可达节点");
            jdbc.update("UPDATE workflow_instance SET status='SUCCESS',finished_at=CURRENT_TIMESTAMP WHERE instance_code=?", instanceCode);
        } catch (CancellationException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            jdbc.update("UPDATE workflow_instance SET status='STOPPED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE instance_code=?", "工作流已停止", instanceCode);
        } catch (Exception ex) {
            String message = ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
            jdbc.update("UPDATE workflow_instance SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE instance_code=?", message, instanceCode);
        } finally {
            running.remove(instanceCode); cancelFlags.remove(instanceCode);
        }
    }

    private TaskOutcome executeNode(String instanceCode, JsonNode node, AtomicBoolean cancelled) {
        long workflowInstanceId = ((Number)instanceRow(instanceCode).get("id")).longValue();
        int retries = config(node).path("retryTimes").asInt(0);
        String nodeName=node.path("name").asText("node-"+node.path("id").asLong());
        String nodeType=node.path("type").asText("SQL");
        Exception last = null;
        for (int attempt=1; attempt<=retries+1; attempt++) {
            long taskId = insertTask(workflowInstanceId,node,nodeName,nodeType,attempt);
            try {
                log(taskId,"INFO","开始执行 " + nodeName + "，第 " + attempt + " 次尝试");
                if (cancelled.get()) throw new CancellationException("已停止");
                switch (nodeType.toUpperCase(Locale.ROOT)) {
                    case "SQL" -> executeSql(node,taskId);
                    case "SEATUNNEL" -> executeSeaTunnel(node,taskId,cancelled);
                    case "CONDITION" -> executeCondition(node,taskId);
                    default -> throw new IllegalStateException("Local Scheduler 不支持节点类型：" + nodeType);
                }
                jdbc.update("UPDATE task_instance SET status='SUCCESS',finished_at=CURRENT_TIMESTAMP WHERE id=?",taskId);
                log(taskId,"INFO","执行成功");
                return new TaskOutcome(true,"SUCCESS");
            } catch (CancellationException ex) {
                jdbc.update("UPDATE task_instance SET status='STOPPED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE id=?",ex.getMessage(),taskId);
                return new TaskOutcome(false,"任务已停止");
            } catch (Exception ex) {
                last=ex; String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                jdbc.update("UPDATE task_instance SET status='FAILED',finished_at=CURRENT_TIMESTAMP,error_message=? WHERE id=?",message,taskId);
                log(taskId,"ERROR",message);
            }
        }
        return new TaskOutcome(false,last==null?"任务失败":last.getMessage());
    }

    private void executeSql(JsonNode node,long taskId) {
        String encoded=node.path("contentBase64").asText("");
        if (encoded.isBlank()) throw new IllegalStateException("SQL 节点没有发布快照");
        String sql=new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonNode cfg=config(node); Long dataSourceId=cfg.hasNonNull("dataSourceId")?cfg.get("dataSourceId").asLong():defaultStarRocks();
        String database=cfg.path("database").asText("");
        log(taskId,"INFO","StarRocks SQL: dataSource="+dataSourceId+", database="+database);
        queryService.execute(sql,false,dataSourceId,database,"scheduler");
    }

    private void executeSeaTunnel(JsonNode node,long taskId,AtomicBoolean cancelled) throws InterruptedException {
        long integrationTaskId=config(node).path("integrationTaskId").asLong(0);
        if (integrationTaskId<=0) throw new IllegalStateException("SeaTunnel 节点未配置 integrationTaskId");
        SeaTunnelGateway.SubmitResult submitted=integrationService.execute(integrationTaskId);
        log(taskId,"INFO","SeaTunnel executionId="+submitted.executionId());
        while (!cancelled.get()) {
            SeaTunnelGateway.JobStatus status=integrationService.status(submitted.executionId());
            String state=status.status()==null?"UNKNOWN":status.status().toUpperCase(Locale.ROOT);
            if (Set.of("SUCCESS","FINISHED").contains(state)) return;
            if (Set.of("FAILED","CANCELLED","KILLED","LOST").contains(state)) throw new IllegalStateException("SeaTunnel 任务失败："+status.message());
            Thread.sleep(1000);
        }
        integrationService.cancel(submitted.executionId());
        throw new CancellationException("工作流停止，SeaTunnel 已取消");
    }

    private void executeCondition(JsonNode node,long taskId) {
        String expression=config(node).path("expression").asText("true").trim();
        boolean matched=Set.of("true","1","yes","on").contains(expression.toLowerCase(Locale.ROOT));
        log(taskId,"INFO","条件表达式 " + expression + " => " + matched + (matched?"":"（false 按 SKIP 处理）"));
    }

    private JsonNode config(JsonNode node) {
        String raw=node.path("configJson").asText("");
        if (raw.isBlank()) return mapper.createObjectNode();
        try { return mapper.readTree(raw); } catch(Exception ex) { return mapper.createObjectNode(); }
    }
    private long defaultStarRocks() {
        return store.dataSources.values().stream().filter(ds->ds.type()== DataSourceType.STARROCKS).map(ds->ds.id()).findFirst()
                .orElseThrow(()->new BadRequestException("没有可用的 StarRocks 数据源"));
    }
    private long insertTask(long workflowInstanceId,JsonNode node,String name,String type,int attempt) {
        KeyHolder keys=new GeneratedKeyHolder();
        jdbc.update(connection->{ PreparedStatement ps=connection.prepareStatement("INSERT INTO task_instance(workflow_instance_id,node_id,node_name,node_type,attempt_no,status,started_at) VALUES(?,?,?,?,?,'RUNNING',CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS); ps.setLong(1,workflowInstanceId);ps.setLong(2,node.path("id").asLong());ps.setString(3,name);ps.setString(4,type);ps.setInt(5,attempt);return ps;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void log(long taskId,String level,String message) { jdbc.update("INSERT INTO task_log(task_instance_id,log_level,message) VALUES(?,?,?)",taskId,level,message); }
    private Definition definition(String workflowCode,Integer version) {
        List<Map<String,Object>> rows=version==null
                ? jdbc.queryForList("SELECT version_no,definition_json FROM workflow_definition_version WHERE workflow_code=? AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1",workflowCode)
                : jdbc.queryForList("SELECT version_no,definition_json FROM workflow_definition_version WHERE workflow_code=? AND version_no=? LIMIT 1",workflowCode,version);
        if(rows.isEmpty()) throw new BadRequestException("工作流没有已发布定义："+workflowCode);
        return new Definition(((Number)rows.getFirst().get("version_no")).intValue(),String.valueOf(rows.getFirst().get("definition_json")));
    }
    private Map<String,Object> instanceRow(String idOrCode) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM workflow_instance WHERE instance_code=? OR CAST(id AS CHAR)=? LIMIT 1",idOrCode,idOrCode);
        if(rows.isEmpty()) throw new BadRequestException("工作流实例不存在："+idOrCode); return rows.getFirst();
    }
    private String resolveInstanceCode(String idOrCode){ return String.valueOf(instanceRow(idOrCode).get("instance_code")); }
    private String workflowLog(String code){ Map<String,Object> row=instanceRow(code); long id=((Number)row.get("id")).longValue(); List<String> logs=jdbc.query("SELECT CONCAT('[',tl.log_level,'] ',ti.node_name,' - ',tl.message) FROM task_log tl JOIN task_instance ti ON ti.id=tl.task_instance_id WHERE ti.workflow_instance_id=? ORDER BY tl.id",(rs,n)->rs.getString(1),id); return String.join("\n",logs); }

    @PreDestroy public void shutdown(){ workflowPool.shutdownNow(); taskPool.shutdownNow(); }
    private record Definition(int version,String json){}
    private record TaskOutcome(boolean success,String message){}
}
