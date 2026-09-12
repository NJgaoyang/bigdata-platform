package com.company.platform.cluster;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.company.platform.system.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class DolphinSchedulerClusterService {
    private static final Logger log = LoggerFactory.getLogger(DolphinSchedulerClusterService.class);
    private static final Pattern SUCCESS_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*0");
    private static final Pattern SESSION_ID = Pattern.compile("\\\"sessionId\\\"\\s*:\\s*\\\"[^\\\"]+\\\"");

    private final PlatformStore store;
    private final PasswordCipher cipher;
    private final AuditService audit;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public DolphinSchedulerClusterService(PlatformStore store, PasswordCipher cipher, AuditService audit) {
        this.store = store;
        this.cipher = cipher;
        this.audit = audit;
    }

    public List<DolphinSchedulerClusterView> list() {
        return store.dolphinSchedulerClusters.values().stream()
                .sorted(Comparator.comparing(DolphinSchedulerClusterView::createdAt).reversed()).toList();
    }

    public DolphinSchedulerClusterView create(DolphinSchedulerClusterRequests.ClusterRequest request) { return create(request, "admin"); }
    public DolphinSchedulerClusterView create(DolphinSchedulerClusterRequests.ClusterRequest request, String operator) {
        ensureUniqueName(request.name(), null);
        DolphinSchedulerClusterView cluster = new DolphinSchedulerClusterView(store.nextId(), request.name().trim(), request.host().trim(),
                request.port(), normalizePath(request.basePath()), blankToNull(request.version()), blankToNull(request.username()),
                blankToNull(request.installDir()), blankToNull(request.description()), "UNKNOWN", LocalDateTime.now());
        String encryptedPassword = cipher.encrypt(request.password());
        store.dolphinSchedulerClusters.put(cluster.id(), cluster);
        store.encryptedDolphinSchedulerPasswords.put(cluster.id(), encryptedPassword);
        store.persistDolphinSchedulerCluster(cluster, encryptedPassword);
        audit.record("CREATE_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", cluster.id(), cluster.name(), normalizeOperator(operator));
        return cluster;
    }

    public DolphinSchedulerClusterView update(long id, DolphinSchedulerClusterRequests.ClusterRequest request) { return update(id, request, "admin"); }
    public DolphinSchedulerClusterView update(long id, DolphinSchedulerClusterRequests.ClusterRequest request, String operator) {
        DolphinSchedulerClusterView current = get(id);
        ensureUniqueName(request.name(), id);
        DolphinSchedulerClusterView updated = new DolphinSchedulerClusterView(id, request.name().trim(), request.host().trim(), request.port(),
                normalizePath(request.basePath()), blankToNull(request.version()), blankToNull(request.username()),
                blankToNull(request.installDir()), blankToNull(request.description()), current.healthStatus(), current.createdAt());
        String encryptedPassword = request.password() == null || request.password().isBlank()
                ? store.encryptedDolphinSchedulerPasswords.getOrDefault(id, "") : cipher.encrypt(request.password());
        store.dolphinSchedulerClusters.put(id, updated);
        store.encryptedDolphinSchedulerPasswords.put(id, encryptedPassword);
        store.persistDolphinSchedulerCluster(updated, encryptedPassword);
        audit.record("UPDATE_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", id, updated.name(), normalizeOperator(operator));
        return updated;
    }

    public void delete(long id) { delete(id, "admin"); }
    public void delete(long id, String operator) {
        DolphinSchedulerClusterView cluster = get(id);
        store.dolphinSchedulerClusters.remove(id);
        store.encryptedDolphinSchedulerPasswords.remove(id);
        store.deleteDolphinSchedulerCluster(id);
        audit.record("DELETE_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", id, cluster.name(), normalizeOperator(operator));
    }

    public DolphinSchedulerClusterView check(long id) { return check(id, "admin"); }
    public DolphinSchedulerClusterView check(long id, String operator) {
        DolphinSchedulerClusterView current = get(id);
        String status = checkStatus(current);
        DolphinSchedulerClusterView updated = new DolphinSchedulerClusterView(current.id(), current.name(), current.host(), current.port(),
                current.basePath(), current.version(), current.username(), current.installDir(), current.description(), status, current.createdAt());
        store.dolphinSchedulerClusters.put(id, updated);
        store.persistDolphinSchedulerCluster(updated, store.encryptedDolphinSchedulerPasswords.getOrDefault(id, ""));
        audit.record("CHECK_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", id, status, normalizeOperator(operator));
        return updated;
    }

    public List<DolphinSchedulerClusterView> checkAll() { return checkAll("admin"); }
    public List<DolphinSchedulerClusterView> checkAll(String operator) { return list().stream().map(item -> check(item.id(), operator)).toList(); }

    private String checkStatus(DolphinSchedulerClusterView cluster) {
        try {
            String username = cluster.username();
            String encrypted = store.encryptedDolphinSchedulerPasswords.getOrDefault(cluster.id(), "");
            if (username == null || username.isBlank() || encrypted.isBlank()) {
                log.warn("DolphinScheduler 集群 {} ({}) 缺少用户名或密码，无法验证 API 登录", cluster.id(), cluster.name());
                return "CONFIG_ERROR";
            }
            String password = cipher.decrypt(encrypted);
            if (password.isBlank()) {
                log.warn("DolphinScheduler 集群 {} ({}) 密码为空，无法验证 API 登录", cluster.id(), cluster.name());
                return "CONFIG_ERROR";
            }
            String body = "userName=" + encode(username) + "&userPassword=" + encode(password);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(cluster) + "/login"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && SUCCESS_CODE.matcher(responseBody).find() && SESSION_ID.matcher(responseBody).find()) {
                return "HEALTHY";
            }
            log.warn("DolphinScheduler 集群 {} ({}) 登录检测失败，HTTP={}，业务响应未通过认证", cluster.id(), cluster.name(), response.statusCode());
            return "AUTH_FAILED";
        } catch (IllegalStateException ex) {
            log.warn("DolphinScheduler 集群 {} ({}) 配置/密码读取失败: {}", cluster.id(), cluster.name(), safeMessage(ex));
            return "CONFIG_ERROR";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("DolphinScheduler 集群 {} ({}) 检测被中断", cluster.id(), cluster.name());
            return "UNREACHABLE";
        } catch (Exception ex) {
            log.warn("DolphinScheduler 集群 {} ({}) API 检测失败: {}", cluster.id(), cluster.name(), safeMessage(ex));
            return "UNREACHABLE";
        }
    }

    private DolphinSchedulerClusterView get(long id) {
        DolphinSchedulerClusterView cluster = store.dolphinSchedulerClusters.get(id);
        if (cluster == null) throw new NotFoundException("DolphinScheduler 集群不存在");
        return cluster;
    }
    private void ensureUniqueName(String name, Long id) {
        if (store.dolphinSchedulerClusters.values().stream().anyMatch(item -> (id == null || item.id() != id) && item.name().equalsIgnoreCase(name))) {
            throw new BadRequestException("DolphinScheduler 集群名称已存在");
        }
    }
    private String baseUrl(DolphinSchedulerClusterView cluster) {
        return "http://" + cluster.host() + ":" + cluster.port() + normalizePath(cluster.basePath());
    }
    private String normalizePath(String value) {
        String path = value == null || value.isBlank() ? "/dolphinscheduler" : value.trim();
        return "/" + path.replaceAll("^/+|/+$", "");
    }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizeOperator(String value) { return value == null || value.isBlank() ? "admin" : value.trim(); }
}
