package com.company.platform.system;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AlertSettingService {
    private static final String DEFAULT_TEMPLATE = "### {taskName}\n- 状态：{status}\n- 时间：{time}\n- 信息：{message}\n- 耗时：{duration}\n- 数据量：{rows}\n- QPS：{qps}";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PasswordCipher cipher;
    private final AuditService audit;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AlertSettingService(JdbcTemplate jdbc, ObjectMapper mapper, PasswordCipher cipher, AuditService audit) {
        this.jdbc = jdbc; this.mapper = mapper; this.cipher = cipher; this.audit = audit;
    }

    public List<AlertSettingView> list() {
        return jdbc.query("SELECT id,name,channel_type,config_json,enabled,created_at,updated_at FROM alert_channel ORDER BY id DESC",
                (rs, n) -> view(rs.getLong("id"), rs.getString("name"), rs.getString("channel_type"),
                        rs.getString("config_json"), rs.getBoolean("enabled"),
                        time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at"))));
    }

    @Transactional
    public AlertSettingView create(AlertSettingRequest request) {
        validateChannel(request.channelType());
        if (blank(request.webhook())) throw new BadRequestException("请填写钉钉 Webhook 地址");
        Config config = config(request, null);
        jdbc.update("INSERT INTO alert_channel(name,channel_type,config_json,enabled) VALUES(?,?,?,?)",
                request.name().trim(), "DINGTALK", json(config), request.enabled());
        Long id = jdbc.queryForObject("SELECT id FROM alert_channel ORDER BY id DESC LIMIT 1", Long.class);
        audit.record("CREATE_ALERT_SETTING", "ALERT_CHANNEL", id, request.name(), "admin");
        return get(id == null ? 0 : id);
    }

    @Transactional
    public AlertSettingView update(long id, AlertSettingRequest request) {
        Row current = row(id); validateChannel(request.channelType());
        Config config = config(request, parse(current.configJson()));
        jdbc.update("UPDATE alert_channel SET name=?,channel_type='DINGTALK',config_json=?,enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                request.name().trim(), json(config), request.enabled(), id);
        audit.record("UPDATE_ALERT_SETTING", "ALERT_CHANNEL", id, request.name(), "admin");
        return get(id);
    }

    @Transactional
    public AlertSettingView setEnabled(long id, boolean enabled) {
        row(id);
        jdbc.update("UPDATE alert_channel SET enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", enabled, id);
        audit.record(enabled ? "ENABLE_ALERT_SETTING" : "DISABLE_ALERT_SETTING", "ALERT_CHANNEL", id, null, "admin");
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        Row current = row(id);
        jdbc.update("DELETE FROM alert_channel WHERE id=?", id);
        audit.record("DELETE_ALERT_SETTING", "ALERT_CHANNEL", id, current.name(), "admin");
    }

    public String test(long id) {
        Row current = row(id);
        Config config = parse(current.configJson());
        send(config, current.name(), "TEST", "这是一条来自大数据平台的钉钉告警测试消息", 0L, 0L, 0D);
        audit.record("TEST_ALERT_SETTING", "ALERT_CHANNEL", id, current.name(), "admin");
        return "测试消息已发送";
    }

    public void notifyTask(String taskName, String status, String message, Long durationMs, Long rows, Double qps) {
        List<Row> channels = jdbc.query("SELECT id,name,channel_type,config_json,enabled FROM alert_channel WHERE enabled=TRUE",
                (rs, n) -> new Row(rs.getLong("id"), rs.getString("name"), rs.getString("channel_type"), rs.getString("config_json"), rs.getBoolean("enabled")));
        for (Row channel : channels) {
            if (!"DINGTALK".equalsIgnoreCase(channel.channelType())) continue;
            Config config;
            try { config = parse(channel.configJson()); } catch (RuntimeException ignored) { continue; }
            if (!matches(config.triggerEvent(), status)) continue;
            CompletableFuture.runAsync(() -> {
                try { send(config, taskName, status, message, durationMs, rows, qps); }
                catch (RuntimeException ignored) { }
            });
        }
    }

    private AlertSettingView get(long id) {
        RowDetail detail = jdbc.query("SELECT id,name,channel_type,config_json,enabled,created_at,updated_at FROM alert_channel WHERE id=?",
                rs -> rs.next() ? new RowDetail(rs.getLong("id"), rs.getString("name"), rs.getString("channel_type"), rs.getString("config_json"), rs.getBoolean("enabled"), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at"))) : null, id);
        if (detail == null) throw new NotFoundException("告警配置不存在：" + id);
        return view(detail.id(), detail.name(), detail.channelType(), detail.configJson(), detail.enabled(), detail.createdAt(), detail.updatedAt());
    }

    private Row row(long id) {
        Row row = jdbc.query("SELECT id,name,channel_type,config_json,enabled FROM alert_channel WHERE id=?",
                rs -> rs.next() ? new Row(rs.getLong("id"), rs.getString("name"), rs.getString("channel_type"), rs.getString("config_json"), rs.getBoolean("enabled")) : null, id);
        if (row == null) throw new NotFoundException("告警配置不存在：" + id);
        return row;
    }

    private AlertSettingView view(long id, String name, String channelType, String raw, boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Config config = parse(raw);
        String webhook = decrypt(config.webhookEncrypted());
        return new AlertSettingView(id, name, channelType, normalizeTrigger(config.triggerEvent()), maskWebhook(webhook),
                !blank(config.secretEncrypted()), nullToEmpty(config.keyword()), nullToEmpty(config.customTemplate()), enabled, createdAt, updatedAt);
    }

    private Config config(AlertSettingRequest request, Config current) {
        String webhook = !blank(request.webhook()) ? request.webhook().trim() : current == null ? "" : decrypt(current.webhookEncrypted());
        String secret = !blank(request.secret()) ? request.secret().trim() : current == null ? "" : decrypt(current.secretEncrypted());
        if (blank(webhook)) throw new BadRequestException("请填写钉钉 Webhook 地址");
        if (!webhook.startsWith("https://") && !webhook.startsWith("http://")) throw new BadRequestException("Webhook 地址必须以 http:// 或 https:// 开头");
        return new Config(normalizeTrigger(request.triggerEvent()), cipher.encrypt(webhook), cipher.encrypt(secret),
                nullToEmpty(request.keyword()).trim(), nullToEmpty(request.customTemplate()));
    }

    private Config parse(String raw) {
        try {
            JsonNode root = mapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
            return new Config(normalizeTrigger(root.path("triggerEvent").asText("FAILURE_ONLY")),
                    root.path("webhookEncrypted").asText(""), root.path("secretEncrypted").asText(""),
                    root.path("keyword").asText(""), root.path("customTemplate").asText(""));
        } catch (Exception ex) { throw new BadRequestException("告警配置无法解析"); }
    }

    private void send(Config config, String taskName, String status, String message, Long durationMs, Long rows, Double qps) {
        String webhook = decrypt(config.webhookEncrypted());
        if (blank(webhook)) throw new BadRequestException("钉钉 Webhook 未配置");
        String secret = decrypt(config.secretEncrypted());
        String url = signedUrl(webhook, secret);
        String rendered = render(config, taskName, status, message, durationMs, rows, qps);
        String keyword = nullToEmpty(config.keyword()).trim();
        if (!keyword.isBlank() && !rendered.contains(keyword)) rendered = "**" + keyword + "**\n\n" + rendered;
        String title = (keyword.isBlank() ? "大数据平台告警" : keyword) + " · " + nullToEmpty(taskName);
        try {
            String body = mapper.writeValueAsString(Map.of("msgtype", "markdown", "markdown", Map.of("title", title, "text", rendered)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new BadRequestException("钉钉返回 HTTP " + response.statusCode());
            JsonNode result = mapper.readTree(response.body());
            if (result.path("errcode").asInt(-1) != 0) throw new BadRequestException("钉钉发送失败：" + result.path("errmsg").asText("未知错误"));
        } catch (BadRequestException ex) { throw ex; }
        catch (Exception ex) { throw new BadRequestException("钉钉告警发送失败：" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())); }
    }

    private String render(Config config, String taskName, String status, String message, Long durationMs, Long rows, Double qps) {
        String text = blank(config.customTemplate()) ? DEFAULT_TEMPLATE : config.customTemplate();
        return text.replace("{taskName}", nullToEmpty(taskName))
                .replace("{status}", statusLabel(status)).replace("{message}", trimMessage(message))
                .replace("{duration}", duration(durationMs)).replace("{rows}", rows == null ? "—" : String.valueOf(rows))
                .replace("{qps}", qps == null ? "—" : String.format(Locale.ROOT, "%.2f", qps))
                .replace("{time}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private String signedUrl(String webhook, String secret) {
        if (blank(secret)) return webhook;
        try {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8))), StandardCharsets.UTF_8);
            return webhook + (webhook.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception ex) { throw new BadRequestException("钉钉签名生成失败"); }
    }

    private boolean matches(String triggerEvent, String status) {
        String value = status == null ? "" : status.toUpperCase(Locale.ROOT);
        boolean failure = value.contains("FAIL") || value.equals("ERROR") || value.equals("LOST") || value.equals("UNKNOWN");
        boolean success = value.equals("SUCCESS") || value.equals("SUCCEEDED") || value.equals("FINISHED") || value.equals("COMPLETED");
        return switch (normalizeTrigger(triggerEvent)) {
            case "ALL" -> true;
            case "SUCCESS_AND_FAILURE" -> success || failure;
            default -> failure;
        };
    }

    private String statusLabel(String status) {
        String value = status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT);
        if (value.equals("TEST")) return "测试";
        if (value.equals("SUCCESS") || value.equals("SUCCEEDED") || value.equals("FINISHED") || value.equals("COMPLETED")) return "成功";
        if (value.contains("FAIL") || value.equals("ERROR") || value.equals("LOST") || value.equals("UNKNOWN")) return "失败";
        if (value.equals("RUNNING") || value.equals("STARTING")) return "运行中";
        if (value.equals("STOPPED") || value.equals("CANCELED") || value.equals("CANCELLED")) return "已停止";
        return value;
    }

    private String normalizeTrigger(String value) {
        String normalized = blank(value) ? "FAILURE_ONLY" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) { case "ALL", "SUCCESS_AND_FAILURE" -> normalized; default -> "FAILURE_ONLY"; };
    }
    private void validateChannel(String value) { if (!"DINGTALK".equalsIgnoreCase(nullToEmpty(value))) throw new BadRequestException("当前仅支持钉钉告警"); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception ex) { throw new BadRequestException("告警配置无法保存"); } }
    private String decrypt(String value) { return blank(value) ? "" : cipher.decrypt(value); }
    private String maskWebhook(String value) { if (blank(value)) return "未配置"; int token = value.indexOf("access_token="); if (token < 0) return value.length() <= 28 ? "******" : value.substring(0, 24) + "…"; String prefix = value.substring(0, token + 13); String secret = value.substring(token + 13); return prefix + (secret.length() <= 4 ? "****" : "****" + secret.substring(secret.length() - 4)); }
    private String trimMessage(String value) { String v = blank(value) ? "—" : value.strip(); return v.length() <= 800 ? v : v.substring(0, 800) + "…"; }
    private String duration(Long ms) { if (ms == null) return "—"; long s = Math.max(0, ms / 1000); return s < 60 ? s + " 秒" : (s / 60) + " 分 " + (s % 60) + " 秒"; }
    private LocalDateTime time(java.sql.Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }

    private record Config(String triggerEvent, String webhookEncrypted, String secretEncrypted, String keyword, String customTemplate) { }
    private record Row(long id, String name, String channelType, String configJson, boolean enabled) { }
    private record RowDetail(long id, String name, String channelType, String configJson, boolean enabled, LocalDateTime createdAt, LocalDateTime updatedAt) { }
}
