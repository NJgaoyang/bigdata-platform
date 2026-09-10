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
        PlatformProperties.Dolphinscheduler ds = properties.getScheduler().getDolphinscheduler();
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
        sendForm("POST", path("/executors/execute"), form);
        return new RunResult(instanceId, "RUNNING");
    }

    @Override public RunResult backfill(String processCode, String start, String end, int parallelism) {
        requireRealMode();
        String dsProcessCode = processCodes.getOrDefault(processCode, processCode);
        Map<String, String> form = baseForm();
        form.put("processDefinitionCode", dsProcessCode);
        form.put("failureStrategy", "END");
        form.put("processInstancePriority", "MEDIUM");
        form.put("warningType", "NONE");
        form.put("scheduleTime", start + "," + end);
        form.put("execType", "COMPLEMENT_DATA");
        form.put("runMode", parallelism > 1 ? "RUN_MODE_PARALLEL" : "RUN_MODE_SERIAL");
        form.put("expectedParallelismNumber", Integer.toString(Math.max(1, parallelism)));
        form.put("workerGroup", properties.getScheduler().getDolphinscheduler().getWorkerGroup());
        form.put("warningGroupId", "0");
        form.put("environmentCode", "-1");
        form.put("dryRun", "0");
        sendForm("POST", path("/executors/start-process-instance"), form);
        return new RunResult("ds-backfill-" + UUID.randomUUID(), "SUBMITTED");
    }

    @Override public ScheduleResult upsertSchedule(ScheduleRequest request) {
        requireRealMode();
        String processCode = processCodes.getOrDefault(request.processCode(), request.processCode());
        Map<String, String> form = baseForm();
        form.put("processDefinitionCode", processCode);
        form.put("schedule", scheduleJson(request));
        form.put("failureStrategy", request.failureStrategy());
        form.put("warningType", "NONE");
        form.put("warningGroupId", "0");
        form.put("processInstancePriority", "MEDIUM");
        form.put("workerGroup", request.workerGroup());
        form.put("environmentCode", "-1");
        String response = request.scheduleId() == null || request.scheduleId().isBlank()
                ? sendForm("POST", path("/schedules"), form)
                : sendForm("PUT", path("/schedules/" + encode(request.scheduleId())), form);
        return new ScheduleResult(extractId(response, request.scheduleId() == null ? "" : request.scheduleId()), "SAVED");
    }

    @Override public void scheduleState(String scheduleId, boolean online) {
        requireRealMode();
        if (scheduleId == null || scheduleId.isBlank()) throw new IllegalStateException("DolphinScheduler scheduleId 不能为空");
        Map<String, String> form = baseForm();
        form.put("id", scheduleId);
        form.put("releaseState", online ? "ONLINE" : "OFFLINE");
        sendForm("POST", path("/schedules/" + encode(scheduleId)), form);
    }

    @Override public void release(String processCode, boolean online) {
        requireRealMode();
        String dsProcessCode = processCodes.getOrDefault(processCode, processCode);
        Map<String, String> form = baseForm();
        form.put("name", "");
        form.put("releaseState", online ? "ONLINE" : "OFFLINE");
        sendForm("POST", path("/process-definition/" + encode(dsProcessCode) + "/release"), form);
    }

    private String scheduleJson(ScheduleRequest request) {
        try {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("startTime", "2020-01-01 00:00:00");
            json.put("endTime", "2120-01-01 00:00:00");
            json.put("crontab", request.cronExpression());
            json.put("timezoneId", request.timezone());
            return mapper.writeValueAsString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("DolphinScheduler 调度配置序列化失败", ex);
        }
    }

    private String awaitProcessInstance(String processCode) {
        try {
            Thread.sleep(150L);
            String response = sendForm("GET", path("/process-instances"), Map.of(
                    "processDefinitionCode", processCode,
                    "pageNo", "1",
                    "pageSize", "10"));
            Matcher matcher = PROCESS_INSTANCE_ID.matcher(response);
            return matcher.find() ? matcher.group(1) : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String extractStatus(String response) {
        for (String status : List.of("SUCCESS", "FAILURE", "RUNNING_EXECUTION", "READY_STOP", "STOP", "PAUSE", "SUBMITTED_SUCCESS")) {
            if (response.contains(status)) return status;
        }
        return "UNKNOWN";
    }

    private String extractId(String response, String fallback) {
        Matcher dataMatcher = DATA_CODE.matcher(response);
        if (dataMatcher.find()) return dataMatcher.group(1);
        Matcher codeMatcher = RESULT_CODE.matcher(response);
        if (codeMatcher.find() && !"0".equals(codeMatcher.group(1))) return codeMatcher.group(1);
        if (fallback != null && !fallback.isBlank()) return fallback;
        throw new IllegalStateException("DolphinScheduler 返回中未找到资源 code: " + response);
    }

    private long projectCode() {
        String value = properties.getScheduler().getDolphinscheduler().getProjectCode();
        if (value == null || value.isBlank()) throw new IllegalStateException("DOLPHINSCHEDULER_PROJECT_CODE 未配置");
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("DOLPHINSCHEDULER_PROJECT_CODE 必须是 DolphinScheduler project code 数字值", ex);
        }
    }

    private void requireRealMode() {
        PlatformProperties.Dolphinscheduler ds = properties.getScheduler().getDolphinscheduler();
        if (!ds.isRealEnabled()) throw new IllegalStateException("DolphinScheduler 真实调用未开启，请设置 DOLPHINSCHEDULER_REAL_ENABLED=true");
        if ((ds.getToken() == null || ds.getToken().isBlank()) && (ds.getPassword() == null || ds.getPassword().isBlank())) {
            throw new IllegalStateException("DolphinScheduler 凭证未配置，请设置 DOLPHINSCHEDULER_TOKEN 或 DOLPHINSCHEDULER_PASSWORD");
        }
    }

    private String path(String suffix) {
        return properties.getScheduler().getDolphinscheduler().getBaseUrl().replaceAll("/$", "")
                + "/dolphinscheduler/projects/" + projectCode() + suffix;
    }

    private Map<String, String> baseForm() {
        Map<String, String> form = new LinkedHashMap<>();
        String token = properties.getScheduler().getDolphinscheduler().getToken();
        if (token != null && !token.isBlank()) form.put("token", token);
        return form;
    }

    private String sendForm(String method, String url, Map<String, String> form) {
        try {
            ensureSession();
            String encoded = encodeForm(form);
            String targetUrl = "GET".equals(method) && !encoded.isBlank() ? url + (url.contains("?") ? "&" : "?") + encoded : url;
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");
            if (sessionId != null && !sessionId.isBlank()) builder.header("sessionId", sessionId);
            String token = properties.getScheduler().getDolphinscheduler().getToken();
            if (token != null && !token.isBlank()) builder.header("token", token);
            if ("GET".equals(method)) builder.GET();
            else builder.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(encoded));
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("DolphinScheduler HTTP " + response.statusCode() + ": " + response.body());
            }
            if (response.body() != null && response.body().contains("\"success\":false")) {
                throw new IllegalStateException("DolphinScheduler 调用失败: " + response.body());
            }
            return response.body() == null ? "" : response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("DolphinScheduler 网络调用失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DolphinScheduler 调用被中断", ex);
        }
    }

    private void ensureSession() throws IOException, InterruptedException {
        PlatformProperties.Dolphinscheduler ds = properties.getScheduler().getDolphinscheduler();
        if (ds.getToken() != null && !ds.getToken().isBlank()) return;
        if (sessionId != null && !sessionId.isBlank()) return;
        Map<String, String> login = new LinkedHashMap<>();
        login.put("userName", ds.getUsername());
        login.put("userPassword", ds.getPassword());
        String loginUrl = ds.getBaseUrl().replaceAll("/$", "") + "/dolphinscheduler/login";
        HttpRequest request = HttpRequest.newBuilder(URI.create(loginUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(login)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("DolphinScheduler 登录失败: HTTP " + response.statusCode());
        Matcher matcher = SESSION_ID.matcher(response.body());
        if (!matcher.find()) throw new IllegalStateException("DolphinScheduler 登录响应未返回 sessionId");
        sessionId = matcher.group(1);
    }

    private String encodeForm(Map<String, String> form) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (entry.getValue() == null) continue;
            parts.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
        }
        return String.join("&", parts);
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String version() { return properties.getScheduler().getDolphinscheduler().getVersion(); }

    private void requireNumericInstanceId(String instanceId) {
        if (instanceId == null || !instanceId.matches("\\d+")) {
            throw new IllegalStateException("DolphinScheduler 尚未返回可操作的数字实例 ID: " + instanceId);
        }
    }
}
