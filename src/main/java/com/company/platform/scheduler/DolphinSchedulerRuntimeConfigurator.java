package com.company.platform.scheduler;

import com.company.platform.cluster.DolphinSchedulerClusterView;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.datasource.PasswordCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;

/**
 * Synchronizes the effective DolphinScheduler runtime with the cluster saved in
 * System Settings. Explicit environment variables still win over persisted
 * settings. Project names are resolved to DolphinScheduler numeric project codes.
 */
@Component
public class DolphinSchedulerRuntimeConfigurator {
    private static final Logger log = LoggerFactory.getLogger(DolphinSchedulerRuntimeConfigurator.class);
    private final PlatformProperties properties;
    private final PlatformStore store;
    private final PasswordCipher cipher;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String defaultProjectSelector;
    private volatile String resolvedKey;
    private volatile String resolvedProjectCode;

    public DolphinSchedulerRuntimeConfigurator(PlatformProperties properties, PlatformStore store,
                                               PasswordCipher cipher, ObjectMapper mapper) {
        this.properties = properties;
        this.store = store;
        this.cipher = cipher;
        this.mapper = mapper;
        String configured = properties.getScheduler().getDolphinscheduler().getProjectCode();
        this.defaultProjectSelector = configured == null || configured.isBlank() || "0".equals(configured.trim())
                ? "bigdata-platform" : configured.trim();
    }

    public synchronized void refresh(boolean resolveProject) {
        var ds = properties.getScheduler().getDolphinscheduler();
        DolphinSchedulerClusterView cluster = selectCluster();
        String basePath = "/dolphinscheduler";

        if (cluster != null) {
            basePath = normalizeBasePath(cluster.basePath());
            if (env("DOLPHINSCHEDULER_BASE_URL").isBlank()) {
                ds.setBaseUrl("http://" + cluster.host() + ":" + cluster.port());
            }
            if (env("DOLPHINSCHEDULER_USERNAME").isBlank() && notBlank(cluster.username())) {
                ds.setUsername(cluster.username().trim());
            }
            if (env("DOLPHINSCHEDULER_PASSWORD").isBlank() && env("DOLPHINSCHEDULER_TOKEN").isBlank()) {
                String encrypted = store.encryptedDolphinSchedulerPasswords.get(cluster.id());
                if (notBlank(encrypted)) ds.setPassword(cipher.decrypt(encrypted));
            }
            if (notBlank(cluster.version())) ds.setVersion(cluster.version().trim());
            if (notBlank(cluster.installDir())) ds.setInstallDir(cluster.installDir().trim());
        }

        String selector = env("DOLPHINSCHEDULER_PROJECT_CODE");
        if (selector.isBlank()) selector = defaultProjectSelector;
        if (selector.matches("\\d+") && !"0".equals(selector)) {
            ds.setProjectCode(selector);
            return;
        }
        if (!resolveProject) {
            ds.setProjectCode(selector);
            return;
        }

        if (!notBlank(ds.getUsername()) || (!notBlank(ds.getPassword()) && !notBlank(ds.getToken()))) {
            throw new IllegalStateException("DolphinScheduler 未配置可用认证信息");
        }
        String projectCode = resolveProjectCode(ds.getBaseUrl(), basePath, ds.getUsername(),
                ds.getPassword(), ds.getToken(), selector);
        ds.setProjectCode(projectCode);
        if (cluster != null) {
            log.debug("DolphinScheduler runtime uses cluster {} ({}) and project {}", cluster.id(), cluster.name(), projectCode);
        }
    }

    private DolphinSchedulerClusterView selectCluster() {
        return store.dolphinSchedulerClusters.values().stream()
                .sorted(Comparator
                        .comparing((DolphinSchedulerClusterView item) -> !"HEALTHY".equalsIgnoreCase(item.healthStatus()))
                        .thenComparing(DolphinSchedulerClusterView::createdAt, Comparator.reverseOrder()))
                .findFirst().orElse(null);
    }

    private String resolveProjectCode(String baseUrl, String basePath, String username,
                                      String password, String token, String projectName) {
        if (!notBlank(projectName) || "0".equals(projectName)) projectName = "bigdata-platform";
        String apiRoot = normalizeRoot(baseUrl) + normalizeBasePath(basePath);
        String key = apiRoot + "|" + username + "|" + projectName;
        if (key.equals(resolvedKey) && notBlank(resolvedProjectCode)) return resolvedProjectCode;

        String session = notBlank(token) ? token.trim() : login(apiRoot, username, password);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiRoot + "/projects?pageNo=1&pageSize=100"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("sessionId", session)
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = parseOk(response, "DolphinScheduler 项目列表");
            JsonNode list = root.path("data").path("totalList");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    if (!projectName.equals(item.path("name").asText())) continue;
                    String code = item.path("code").asText("");
                    if (code.matches("\\d+")) {
                        resolvedKey = key;
                        resolvedProjectCode = code;
                        return code;
                    }
                }
            }
            throw new IllegalStateException("DolphinScheduler 未找到项目：" + projectName);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DolphinScheduler 项目查询被中断", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("DolphinScheduler 项目查询失败：" + ex.getMessage(), ex);
        }
    }

    private String login(String apiRoot, String username, String password) {
        if (!notBlank(password)) throw new IllegalStateException("DolphinScheduler 未配置密码或 Token");
        String form = "userName=" + encode(username) + "&userPassword=" + encode(password);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiRoot + "/login"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = parseOk(response, "DolphinScheduler 登录");
            JsonNode session = root.findValue("sessionId");
            if (session == null || !notBlank(session.asText())) {
                throw new IllegalStateException("DolphinScheduler 登录未返回 sessionId");
            }
            return session.asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DolphinScheduler 登录被中断", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("DolphinScheduler 登录失败：" + ex.getMessage(), ex);
        }
    }

    private JsonNode parseOk(HttpResponse<String> response, String action) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(action + " HTTP=" + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body() == null ? "{}" : response.body());
        if (root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException(action + "返回业务错误，code=" + root.path("code").asText());
        }
        return root;
    }

    private String normalizeRoot(String value) {
        String root = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (root.endsWith("/dolphinscheduler")) {
            return root.substring(0, root.length() - "/dolphinscheduler".length());
        }
        return root;
    }

    private String normalizeBasePath(String value) {
        String path = value == null || value.isBlank() ? "/dolphinscheduler" : value.trim();
        return "/" + path.replaceAll("^/+|/+$", "");
    }

    private String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }
    private boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
