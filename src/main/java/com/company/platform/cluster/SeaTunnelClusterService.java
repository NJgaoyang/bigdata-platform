package com.company.platform.cluster;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.company.platform.system.AuditService;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeaTunnelClusterService {
    private final PlatformStore store;
    private final PasswordCipher cipher;
    private final AuditService audit;

    public SeaTunnelClusterService(PlatformStore store, PasswordCipher cipher, AuditService audit) {
        this.store = store;
        this.cipher = cipher;
        this.audit = audit;
    }

    public List<SeaTunnelClusterView> list() {
        return store.seaTunnelClusters.values().stream()
                .sorted(java.util.Comparator.comparing(SeaTunnelClusterView::createdAt).reversed()).toList();
    }

    public SeaTunnelClusterView create(SeaTunnelClusterRequests.ClusterRequest request) {
        if (store.seaTunnelClusters.values().stream().anyMatch(item -> item.name().equalsIgnoreCase(request.name()))) {
            throw new BadRequestException("集群名称已存在");
        }
        SeaTunnelClusterView cluster = new SeaTunnelClusterView(store.nextId(), request.name().trim(), request.host().trim(),
                request.port(), blankToNull(request.sshUsername()), normalizeSshPort(request.sshPort()), request.seatunnelHome().trim(),
                blankToNull(request.description()), "UNKNOWN", LocalDateTime.now());
        String encryptedPassword = cipher.encrypt(request.sshPassword());
        store.persistSeaTunnelCluster(cluster, encryptedPassword);
        store.seaTunnelClusters.put(cluster.id(), cluster);
        store.encryptedClusterPasswords.put(cluster.id(), encryptedPassword);
        audit.record("CREATE_CLUSTER", "SEATUNNEL_CLUSTER", cluster.id(), cluster.name(), "admin");
        return cluster;
    }

    public SeaTunnelClusterView update(long id, SeaTunnelClusterRequests.ClusterRequest request) {
        SeaTunnelClusterView current = get(id);
        boolean duplicate = store.seaTunnelClusters.values().stream()
                .anyMatch(item -> item.id() != id && item.name().equalsIgnoreCase(request.name()));
        if (duplicate) throw new BadRequestException("集群名称已存在");
        SeaTunnelClusterView updated = new SeaTunnelClusterView(id, request.name().trim(), request.host().trim(), request.port(),
                blankToNull(request.sshUsername()), normalizeSshPort(request.sshPort()), request.seatunnelHome().trim(),
                blankToNull(request.description()), current.healthStatus(), current.createdAt());
        String encryptedPassword = request.sshPassword() == null || request.sshPassword().isBlank()
                ? store.encryptedClusterPasswords.getOrDefault(id, "") : cipher.encrypt(request.sshPassword());
        store.persistSeaTunnelCluster(updated, encryptedPassword);
        store.seaTunnelClusters.put(id, updated);
        store.encryptedClusterPasswords.put(id, encryptedPassword);
        audit.record("UPDATE_CLUSTER", "SEATUNNEL_CLUSTER", id, updated.name(), "admin");
        return updated;
    }

    public void delete(long id) {
        SeaTunnelClusterView cluster = get(id);
        store.deleteSeaTunnelCluster(id);
        store.seaTunnelClusters.remove(id);
        store.encryptedClusterPasswords.remove(id);
        audit.record("DELETE_CLUSTER", "SEATUNNEL_CLUSTER", id, cluster.name(), "admin");
    }

    public SeaTunnelClusterView check(long id) {
        SeaTunnelClusterView current = get(id);
        String status;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(current.host(), current.port()), 2500);
            status = "HEALTHY";
        } catch (Exception ignored) {
            status = "UNREACHABLE";
        }
        SeaTunnelClusterView updated = new SeaTunnelClusterView(current.id(), current.name(), current.host(), current.port(),
                current.sshUsername(), current.sshPort(), current.seatunnelHome(), current.description(), status, current.createdAt());
        store.persistSeaTunnelCluster(updated, store.encryptedClusterPasswords.getOrDefault(id, ""));
        store.seaTunnelClusters.put(id, updated);
        audit.record("CHECK_CLUSTER", "SEATUNNEL_CLUSTER", id, status, "admin");
        return updated;
    }

    public List<SeaTunnelClusterView> checkAll() { return list().stream().map(item -> check(item.id())).toList(); }

    private SeaTunnelClusterView get(long id) {
        SeaTunnelClusterView cluster = store.seaTunnelClusters.get(id);
        if (cluster == null) throw new NotFoundException("集群不存在");
        return cluster;
    }
    private int normalizeSshPort(int value) { return value <= 0 ? 22 : value; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
