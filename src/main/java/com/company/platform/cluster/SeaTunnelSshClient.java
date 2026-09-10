package com.company.platform.cluster;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.datasource.PasswordCipher;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * SSH boundary for a SeaTunnel runtime registered in System Settings.
 * Passwords are decrypted only for the duration of the SSH handshake and are
 * never returned to HTTP clients or written to logs/config persistence.
 */
@Component
public class SeaTunnelSshClient {
    private final PlatformStore store;
    private final PasswordCipher cipher;

    public SeaTunnelSshClient(PlatformStore store, PasswordCipher cipher) {
        this.store = store;
        this.cipher = cipher;
    }

    public RuntimeCluster runtime(long clusterId) {
        SeaTunnelClusterView cluster = store.seaTunnelClusters.get(clusterId);
        if (cluster == null) throw new NotFoundException("SeaTunnel 集群不存在：" + clusterId);
        if (cluster.sshUsername() == null || cluster.sshUsername().isBlank()) {
            throw new IllegalStateException("SeaTunnel 集群未配置 SSH 用户名：" + cluster.name());
        }
        if (cluster.seatunnelHome() == null || cluster.seatunnelHome().isBlank()) {
            throw new IllegalStateException("SeaTunnel 集群未配置安装目录：" + cluster.name());
        }
        String encrypted = store.encryptedClusterPasswords.get(clusterId);
        String password = encrypted == null || encrypted.isBlank() ? "" : cipher.decrypt(encrypted);
        if (password.isBlank()) throw new IllegalStateException("SeaTunnel 集群未配置 SSH 密码：" + cluster.name());
        return new RuntimeCluster(cluster.id(), cluster.name(), cluster.host(), cluster.sshPort() <= 0 ? 22 : cluster.sshPort(),
                cluster.sshUsername(), password, cluster.seatunnelHome());
    }

    public boolean executableAvailable(long clusterId) {
        RuntimeCluster runtime = runtime(clusterId);
        Session session = null;
        ChannelExec channel = null;
        try {
            session = connect(runtime);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("test -x " + shellQuote(runtime.seatunnelHome() + "/bin/seatunnel.sh"));
            channel.setInputStream(null);
            channel.connect((int) Duration.ofSeconds(5).toMillis());
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
            while (!channel.isClosed() && System.currentTimeMillis() < deadline) Thread.sleep(50);
            return channel.isClosed() && channel.getExitStatus() == 0;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    public RemoteExecution start(long clusterId, String config) throws Exception {
        RuntimeCluster runtime = runtime(clusterId);
        Session session = connect(runtime);
        String remoteConfig = "/tmp/platform-seatunnel-" + UUID.randomUUID() + ".conf";
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect((int) Duration.ofSeconds(5).toMillis());
            try (InputStream input = new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8))) {
                sftp.put(input, remoteConfig);
            }
            try { sftp.chmod(0600, remoteConfig); } catch (Exception ignored) { }
            sftp.disconnect();
            sftp = null;

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            String script = runtime.seatunnelHome() + "/bin/seatunnel.sh";
            String command = shellQuote(script) + " --config " + shellQuote(remoteConfig)
                    + " --master cluster 2>&1; rc=$?; rm -f " + shellQuote(remoteConfig) + "; exit $rc";
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream output = channel.getInputStream();
            channel.connect((int) Duration.ofSeconds(10).toMillis());
            return new RemoteExecution(runtime, session, channel, output, remoteConfig);
        } catch (Exception ex) {
            if (sftp != null) sftp.disconnect();
            session.disconnect();
            throw ex;
        }
    }

    private Session connect(RuntimeCluster runtime) throws Exception {
        Session session = new JSch().getSession(runtime.username(), runtime.host(), runtime.sshPort());
        session.setPassword(runtime.password());
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
        session.setServerAliveInterval(15_000);
        session.connect((int) Duration.ofSeconds(10).toMillis());
        return session;
    }

    private String shellQuote(String value) {
        return "'" + String.valueOf(value).replace("'", "'\"'\"'") + "'";
    }

    public record RuntimeCluster(long id, String name, String host, int sshPort, String username,
                                 String password, String seatunnelHome) { }

    public record RemoteExecution(RuntimeCluster runtime, Session session, ChannelExec channel,
                                  InputStream output, String remoteConfigPath) implements AutoCloseable {
        @Override public void close() {
            try { if (channel != null) channel.disconnect(); } catch (RuntimeException ignored) { }
            try { if (session != null) session.disconnect(); } catch (RuntimeException ignored) { }
        }
    }
}
