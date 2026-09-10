package com.company.platform.cluster;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.company.platform.system.AuditService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class DolphinSchedulerClusterService {
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

    public DolphinSchedulerClusterView create(DolphinSchedulerClusterRequests.ClusterRequest request) {
        ensureUniqueName(request.name(), null);
        DolphinSchedulerClusterView cluster = new DolphinSchedulerClusterView(store.nextId(), request.name().trim(), request.host().trim(),
                request.port(), normalizePath(request.basePath()), blankToNull(request.version()), blankToNull(request.username()),
                blankToNull(request.installDir()), blankToNull(request.description()), "UNKNOWN", LocalDateTime.now());
        String encryptedPassword = cipher.encrypt(request.password());
        store.dolphinSchedulerClusters.put(cluster.id(), cluster);
        store.encryptedDolphinSchedulerPasswords.put(cluster.id(), encryptedPassword);
        store.persistDolphinSchedulerCluster(cluster, encryptedPassword);
        audit.record("CREATE_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", cluster.id(), cluster.name(), "admin");
        return cluster;
    }

    public DolphinSchedulerClusterView update(long id, DolphinSchedulerClusterRequests.ClusterRequest request) {
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
        audit.record("UPDATE_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", id, updated.name(), "admin");
        return updated;
    }

    public void delete(long id) {
        DolphinSchedulerClusterView cluster = get(id);
        store.dolphinSchedulerClusters.remove(id);
        store.encryptedDolphinSchedulerPasswords.remove(id);
        store.deleteDolphinSchedulerCluster(id);
        audit.record("DELETE_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", id, cluster.name(), "admin");
    }

    public DolphinSchedulerClusterView check(long id) {
        DolphinSchedulerClusterView current = get(id);
        String status = "UNREACHABLE";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(current) + "/ui"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            int code = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code >= 200 && code < 400) status = "HEALTHY";
        } catch (Exception ignored) {
            // The status is intentionally based on a real HTTP request to the configured DS endpoint.
        }
        DolphinSchedulerClusterView updated = new DolphinSchedulerClusterView(current.id(), current.name(), current.host(), current.port(),
                current.basePath(), current.version(), current.username(), current.installDir(), current.description(), status, current.createdAt());
        store.dolphinSchedulerClusters.put(id, updated);
        store.persistDolphinSchedulerCluster(updated, store.encryptedDolphinSchedulerPasswords.getOrDefault(id, ""));
        audit.record("CHECK_DOLPHINSCHEDULER_CLUSTER", "DOLPHINSCHEDULER_CLUSTER", id, status, "admin");
        return updated;
    }

    public List<DolphinSchedulerClusterView> checkAll() { return list().stream().map(item -> check(item.id())).toList(); }

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
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
