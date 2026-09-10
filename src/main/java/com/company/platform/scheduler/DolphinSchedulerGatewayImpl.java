package com.company.platform.scheduler;

import com.company.platform.config.PlatformProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DolphinScheduler 3.1.x boundary. Real mode uses the v1 sessionId protocol. */
@Component
public class DolphinSchedulerGatewayImpl implements DolphinSchedulerGateway {
    private static final Pattern SESSION_ID = Pattern.compile("\\\"sessionId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern RESULT_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*([0-9]+)");
    private static final Pattern DATA_CODE = Pattern.compile("\\\"data\\\"\\s*:\\s*\\{.*?\\\"code\\\"\\s*:\\s*([0-9]+)", Pattern.DOTALL);
    private static final Pattern PROCESS_INSTANCE_ID = Pattern.compile("\\\"(?:processInstanceId|processCode)\\\"\\s*:\\s*\\\"?([A-Za-z0-9_-]+)");
    private final PlatformProperties properties;
    private final ObjectMapper mapper;
    private final DolphinSchedulerProcessConverter processConverter;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, String> processCodes = new ConcurrentHashMap<>();
    private volatile String sessionId;

    public DolphinSchedulerGatewayImpl(PlatformProperties properties) {
        this(properties, new ObjectMapper());
    }

    @Autowired
    public DolphinSchedulerGatewayImpl(PlatformProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        var ds = properties.getScheduler().getDolphinscheduler();
        this.processConverter = new DolphinSchedulerProcessConverter(mapper, ds.getFailRetryTimes(),
                ds.getFailRetryInterval(), ds.getWorkerGroup());
    }

    @Override public PublishResult publish(PublishRequest request) {
        requireRealMode();
        long projectCode = projectCode();
        var payload = processConverter.convert(request.definitionJson(), projectCode,
                properties.getScheduler().getDolphinscheduler().getTenantCode(),
                properties.getScheduler().getDolphinscheduler().getUsername());
        Map<String, String> form = baseForm();
        form.put("name", request.name());
        form.put("description", payload.description());
        form.put("globalParams", "[]");
        form.put("locations", payload.locations());
        form.put("timeout", "0");
        form.put("tenantCode", payload.tenantCode());
        form.put("taskRelationJson", payload.relations());
        form.put("taskDefinitionJson", payload.taskDefinitions());
        form.put("executionType", "PARALLEL");
        String existing = request.existingProcessCode() == null ? "" : request.existingProcessCode().trim();
        String response = existing.isBlank()
                ? sendForm("POST", path("/process-definition"), form)
                : sendForm("PUT", path("/process-definition/" + encode(existing)), form);
        String dsCode = extractId(response, existing.isBlank() ? request.workflowCode() : existing);
        processCodes.put(request.workflowCode(), dsCode);
        processCodes.put(dsCode, dsCode);
        return new PublishResult(dsCode, request.version(), "PUBLISHED@DS-" + version());
    }

    @Override public RunResult run(String processCode) {
        requireRealMode();
        String dsProcessCode = processCodes.getOrDefault(processCode, processCode);
        Map<String, String> form = baseForm();
        form.put("processDefinitionCode", dsProcessCode);
        form.put("failureStrategy", "END");
        form.put("processInstancePriority", "MEDIUM");
        form.put("warningType", "NONE");
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        form.put("scheduleTime", now + "," + now);
        form.put("execType", "START_PROCESS");
        form.put("runMode", "RUN_MODE_PARALLEL");
        form.put("complementDependentMode", "ALL_DEPENDENT");
        form.put("workerGroup", properties.getScheduler().getDolphinscheduler().getWorkerGroup());
        form.put("warningGroupId", "0");
        form.put("environmentCode", "-1");
        form.put("dryRun", "0");
        String response = sendForm("POST", path("/executors/start-process-instance"), form);
        String instanceId = awaitProcessInstance(dsProcessCode);
        return new RunResult(instanceId == null ? "ds-command-" + UUID.randomUUID() : instanceId,
                instanceId == null ? "SUBMITTED" : "RUNNING");
    }

    @Override public InstanceStatus status(String instanceId) {
        requireRealMode();
        if (instanceId == null || !instanceId.matches("\\d+")) {
            return new InstanceStatus(instanceId, "SUBMITTED", "DolphinScheduler 已接受调度命令，等待 Master 创建实例");
        }
        String response = sendForm("GET", path("/process-instances/" + encode(instanceId)), Map.of());
        return new InstanceStatus(instanceId, extractStatus(response), response);
    }

    @Override public void stop(String instanceId) {
        requireRealMode();
        requireNumericInstanceId(instanceId);
        Map<String, String> form = baseForm();
        form.put("processInstanceId", instanceId);
        form.put("executeType", "STOP");
        sendForm("POST", path("/executors/execute"), form);
    }

    @Override public RunResult rerun(String instanceId) {
        requireRealMode();
        requireNumericInstanceId(instanceId);
        Map<String, String> form = baseForm();
        form.put("processInstanceId", instanceId);
        form.put("executeType", "REPEAT_RUNNING");
        String response = sendForm("POST", path("/executors/execute"), form);
        return new RunResult(extractId(response, instanceId), "RUNNING");
    }

    @Override public RunResult backfill(String processCode, String start, String end, int parallelism) {
        requireRealMode();
        Map<String, String> form = baseForm();
        form.put("processDefinitionCode", processCodes.getOrDefault(processCode, processCode));
        form.put("failureStrategy", "END");
        form.put("processInstancePriority", "MEDIUM");
        form.put("warningType", "NONE");
        form.put("scheduleTime", "{\"complementStartDate\":\"" + jsonEscape(start)
                + "\",\"complementEndDate\":\"" + jsonEscape(end) + "\"}");
        form.put("execType", "COMPLEMENT_DATA");
        form.put("runMode", "RUN_MODE_PARALLEL");
        form.put("expectedParallelismNumber", String.valueOf(parallelism));
        form.put("workerGroup", properties.getScheduler().getDolphinscheduler().getWorkerGroup());
        form.put("warningGroupId", "0");
        form.put("environmentCode", "-1");
        form.put("dryRun", "0");
        String response = sendForm("POST", path("/executors/start-process-instance"), form);
        return new RunResult(extractId(response, "ds-backfill-" + UUID.randomUUID()), "RUNNING");
    }

    @Override public void release(String processCode, boolean online) {
        requireRealMode();
        String dsProcessCode = processCodes.getOrDefault(processCode, processCode);
        sendForm("POST", path("/process-definition/" + encode(dsProcessCode) + "/release"),
                Map.of("releaseState", online ? "ONLINE" : "OFFLINE"));
    }

    @Override public String upsertSchedule(String processCode, String cronExpression, String timezone, boolean enabled,
                                           String failureStrategy, int parallelism) {
        requireRealMode();
        String project = encode(properties.getScheduler().getDolphinscheduler().getProjectCode());
        String query = "/schedules?processDefinitionCode=" + encode(processCode) + "&pageNo=1&pageSize=100";
        String existing = findScheduleId(sendForm("GET", path(query), Map.of()));
        Map<String, String> form = new LinkedHashMap<>();
        form.put("processDefinitionCode", processCode);
        form.put("schedule", scheduleJson(cronExpression, timezone));
        form.put("warningType", "NONE");
        form.put("warningGroupId", "1");
        form.put("failureStrategy", failureStrategy == null || failureStrategy.isBlank() ? "END" : failureStrategy);
        form.put("processInstancePriority", "MEDIUM");
        form.put("workerGroup", properties.getScheduler().getDolphinscheduler().getWorkerGroup());
        form.put("environmentCode", "-1");
        String response;
        if (existing.isBlank()) {
            response = sendForm("POST", basePath("/dolphinscheduler/projects/" + project + "/schedules"), form);
            existing = extractId(response, "");
        } else {
            response = sendForm("PUT", basePath("/dolphinscheduler/projects/" + project + "/schedules/" + encode(existing)), form);
        }
        return existing;
    }

    @Override public void scheduleState(String scheduleId, boolean online) {
        requireRealMode();
        if (scheduleId == null || scheduleId.isBlank()) return;
        sendForm("POST", basePath("/dolphinscheduler/projects/" + encode(properties.getScheduler().getDolphinscheduler().getProjectCode())
                + "/schedules/" + encode(scheduleId) + (online ? "/online" : "/offline")), Map.of());
    }

    @Override public List<Map<String, Object>> listProcessInstances() {
        requireRealMode();
        String body = sendForm("GET", path("/process-instances?pageNo=1&pageSize=100"), Map.of());
        return parseInstanceList(body, "process");
    }

    @Override public boolean isRealMode() { return realEnabled(); }

    @Override public List<Map<String, Object>> listTaskInstances() {
        requireRealMode();
        String body = sendForm("GET", path("/task-instances?pageNo=1&pageSize=100&taskExecuteType=BATCH"), Map.of());
        return parseInstanceList(body, "task");
    }

    @Override public String taskLog(String taskInstanceId) {
        requireRealMode();
        if (taskInstanceId == null || !taskInstanceId.matches("\\d+")) {
            return "DolphinScheduler 任务实例尚未生成可查询的编号";
        }
        String body = sendForm("GET", basePath("/dolphinscheduler/log/" + encode(properties.getScheduler().getDolphinscheduler().getProjectCode())
                + "/detail?taskInstanceId=" + encode(taskInstanceId) + "&skipLineNum=0&limit=1000"), Map.of());
        try {
            var data = mapper.readTree(body).path("data");
            for (String key : List.of("log", "content", "text")) {
                if (data.hasNonNull(key)) return data.path(key).asText();
            }
            return data.isTextual() ? data.asText() : data.toString();
        } catch (IOException ex) {
            return body;
        }
    }

    private boolean realEnabled() {
        var config = properties.getScheduler().getDolphinscheduler();
        if (!config.isRealEnabled()) return false;
        if ((config.getPassword() == null || config.getPassword().isBlank())
                && (config.getToken() == null || config.getToken().isBlank())) {
            throw new IllegalStateException("DolphinScheduler 已开启真实模式，但未配置密码或 sessionId Token");
        }
        return true;
    }

    private void requireRealMode() {
        if (!realEnabled()) throw new IllegalStateException("DolphinScheduler 真实执行未启用，禁止创建模拟任务");
    }

    private Map<String, String> baseForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("projectCode", properties.getScheduler().getDolphinscheduler().getProjectCode());
        form.put("tenantCode", properties.getScheduler().getDolphinscheduler().getTenantCode());
        return form;
    }

    private String path(String suffix) {
        return basePath("/dolphinscheduler/projects/" + encode(properties.getScheduler().getDolphinscheduler().getProjectCode()) + suffix);
    }

    private String basePath(String suffix) {
        String base = properties.getScheduler().getDolphinscheduler().getBaseUrl().replaceAll("/+$", "");
        return base + suffix;
    }

    private long projectCode() {
        String code = properties.getScheduler().getDolphinscheduler().getProjectCode();
        try {
            return Long.parseLong(code);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("真实 DolphinScheduler 模式要求 project-code 使用数字编码：" + code);
        }
    }

    private String sendForm(String method, String url, Map<String, String> values) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            String currentSession = ensureSession();
            if (currentSession != null && !currentSession.isBlank()) builder.header("sessionId", currentSession);
            HttpRequest request;
            if ("GET".equals(method)) request = builder.GET().build();
            else {
                builder = builder.header("Content-Type", "application/x-www-form-urlencoded");
                var body = HttpRequest.BodyPublishers.ofString(formEncode(values), StandardCharsets.UTF_8);
                request = switch (method) {
                    case "PUT" -> builder.PUT(body).build();
                    case "DELETE" -> builder.method("DELETE", body).build();
                    default -> builder.POST(body).build();
                };
            }
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("DolphinScheduler API 返回 " + response.statusCode());
            String body = response.body();
            Matcher code = RESULT_CODE.matcher(body == null ? "" : body);
            if (code.find() && Integer.parseInt(code.group(1)) != 0) throw new IllegalStateException("DolphinScheduler API 返回错误：" + body);
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DolphinScheduler API 调用中断", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("DolphinScheduler API 调用失败：" + ex.getMessage(), ex);
        }
    }

    private String ensureSession() {
        var config = properties.getScheduler().getDolphinscheduler();
        if (config.getToken() != null && !config.getToken().isBlank()) return config.getToken();
        if (sessionId != null && !sessionId.isBlank()) return sessionId;
        Map<String, String> login = new LinkedHashMap<>();
        login.put("userName", config.getUsername());
        login.put("userPassword", config.getPassword());
        String response = sendLogin(login);
        Matcher matcher = SESSION_ID.matcher(response == null ? "" : response);
        if (!matcher.find()) throw new IllegalStateException("DolphinScheduler 登录未返回 sessionId");
        sessionId = matcher.group(1);
        return sessionId;
    }

    private String sendLogin(Map<String, String> values) {
        try {
            String base = properties.getScheduler().getDolphinscheduler().getBaseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/dolphinscheduler/login"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(values), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("DolphinScheduler 登录返回 " + response.statusCode());
            Matcher code = RESULT_CODE.matcher(response.body());
            if (code.find() && Integer.parseInt(code.group(1)) != 0) throw new IllegalStateException("DolphinScheduler 登录失败");
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DolphinScheduler 登录中断", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("DolphinScheduler 登录失败：" + ex.getMessage(), ex);
        }
    }

    private String formEncode(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue() == null ? "" : entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }
    private String extractId(String body, String fallback) {
        try {
            var data = mapper.readTree(body == null ? "" : body).path("data");
            if (data.isNumber() || data.isTextual()) return data.asText();
            for (String field : List.of("id", "processInstanceId", "processCode", "code")) {
                var value = data.path(field);
                if (value.isNumber() || value.isTextual()) return value.asText();
            }
        } catch (IOException ignored) { }
        Matcher instance = PROCESS_INSTANCE_ID.matcher(body == null ? "" : body);
        if (instance.find()) return instance.group(1);
        Matcher data = DATA_CODE.matcher(body == null ? "" : body);
        if (data.find()) return data.group(1);
        return fallback;
    }
    private String extractStatus(String body) {
        Matcher matcher = Pattern.compile("\\\"state\\\"\\s*:\\s*\\\"([^\\\"]+)").matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : "UNKNOWN";
    }
    private String findScheduleId(String body) {
        try {
            var list = mapper.readTree(body).path("data").path("totalList");
            if (list.isArray() && !list.isEmpty()) return list.get(0).path("id").asText("");
        } catch (IOException ignored) { }
        return "";
    }
    private String scheduleJson(String cronExpression, String timezone) {
        return "{\"startTime\":\"2000-01-01 00:00:00\",\"endTime\":\"2099-12-31 23:59:59\",\"crontab\":\""
                + jsonEscape(cronExpression) + "\",\"timezoneId\":\"" + jsonEscape(timezone) + "\"}";
    }
    private List<Map<String, Object>> parseInstanceList(String body, String kind) {
        try {
            var list = mapper.readTree(body).path("data").path("totalList");
            List<Map<String, Object>> result = new ArrayList<>();
            if (!list.isArray()) return result;
            for (var item : list) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText());
                row.put("name", item.path("name").asText());
                row.put("status", item.path("state").asText("UNKNOWN"));
                row.put("engine", "DolphinScheduler " + version());
                row.put("processInstanceId", "process".equals(kind)
                        ? item.path("id").asText() : item.path("processInstanceId").asText());
                row.put("processDefinitionCode", item.path("processDefinitionCode").asText());
                row.put("taskType", item.path("taskType").asText());
                row.put("startTime", item.path("startTime").asText());
                row.put("endTime", item.path("endTime").asText());
                result.add(row);
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("DolphinScheduler 实例列表解析失败", ex);
        }
    }
    private String awaitProcessInstance(String processDefinitionCode) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Thread.sleep(400);
                for (Map<String, Object> instance : listProcessInstances()) {
                    if (processDefinitionCode.equals(String.valueOf(instance.get("processDefinitionCode")))) {
                        return String.valueOf(instance.get("id"));
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
    private void requireNumericInstanceId(String instanceId) {
        if (instanceId == null || !instanceId.matches("\\d+")) {
            throw new IllegalArgumentException("DolphinScheduler 实例尚未生成可操作的实例编号：" + instanceId);
        }
    }
    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private String version() { return properties.getScheduler().getDolphinscheduler().getVersion(); }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
