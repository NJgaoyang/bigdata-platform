package com.company.platform.integration;

import com.company.platform.cluster.SeaTunnelSshClient;
import com.company.platform.config.PlatformProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SeaTunnel 2.3.12 process boundary.
 *
 * <p>Without a cluster id the job runs on the application host. With a cluster
 * id it uses the SeaTunnel cluster registered in System Settings, uploads the
 * generated HOCON over SFTP and invokes the remote seatunnel.sh in cluster mode.</p>
 */
@Component
public class SeaTunnelGatewayImpl implements SeaTunnelGateway {
    private final PlatformProperties properties;
    private final SeaTunnelSshClient sshClient;
    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, Path> configs = new ConcurrentHashMap<>();
    private final Map<String, SeaTunnelSshClient.RemoteExecution> remoteExecutions = new ConcurrentHashMap<>();
    private final Map<String, StringBuffer> logs = new ConcurrentHashMap<>();
    private final Map<String, String> terminalStates = new ConcurrentHashMap<>();

    /** Retained for unit tests and local-only callers. */
    public SeaTunnelGatewayImpl(PlatformProperties properties) { this(properties, null); }

    @Autowired
    public SeaTunnelGatewayImpl(PlatformProperties properties, SeaTunnelSshClient sshClient) {
        this.properties = properties;
        this.sshClient = sshClient;
    }

    @Override public ValidationResult validate(String config) { return validate(config, null); }

    @Override public ValidationResult validate(String config, Long clusterId) {
        if (config == null || config.isBlank()) return new ValidationResult(false, "配置不能为空");
        if (!properties.getSeatunnel().isRealEnabled()) return new ValidationResult(false, "SeaTunnel 真实执行未启用");
        if (!hasRequiredSections(config)) return new ValidationResult(false, "SeaTunnel 配置必须包含 env、source 和 sink");
        if (clusterId != null) {
            if (sshClient == null) return new ValidationResult(false, "SeaTunnel SSH 运行组件不可用");
            try {
                return sshClient.executableAvailable(clusterId)
                        ? new ValidationResult(true, "SeaTunnel 远程集群与配置检查通过")
                        : new ValidationResult(false, "无法通过 SSH 验证远程 seatunnel.sh，请检查集群 SSH 和安装目录");
            } catch (RuntimeException ex) {
                return new ValidationResult(false, ex.getMessage());
            }
        }
        Path executable = localExecutable();
        if (!Files.isExecutable(executable)) return new ValidationResult(false, "SeaTunnel 启动脚本不可执行：" + executable);
        return new ValidationResult(true, "SeaTunnel 本机运行环境与配置检查通过");
    }

    @Override public SubmitResult submit(String config) { return submit(config, null); }

    @Override public SubmitResult submit(String config, Long clusterId) {
        requireRealMode();
        if (!hasRequiredSections(config)) throw new IllegalArgumentException("SeaTunnel 配置必须包含 env、source 和 sink");
        return clusterId == null ? submitLocal(config) : submitRemote(config, clusterId);
    }

    private SubmitResult submitLocal(String config) {
        Path configFile = null;
        try {
            Path home = Path.of(properties.getSeatunnel().getHome());
            Path executable = localExecutable();
            if (!Files.isExecutable(executable)) throw new IllegalStateException("SeaTunnel 启动脚本不可执行：" + executable);
            Path configDirectory = home.resolve("config");
            Files.createDirectories(configDirectory);
            configFile = Files.createTempFile(configDirectory, "platform-", ".conf");
            restrictPermissions(configFile);
            Files.writeString(configFile, config, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(executable.toString(), "--config", configFile.toString())
                    .directory(home.toFile()).redirectErrorStream(true).start();
            String executionId = "seatunnel-local-" + UUID.randomUUID();
            StringBuffer output = new StringBuffer();
            logs.put(executionId, output);
            Path finalConfigFile = configFile;
            Thread logReader = new Thread(() -> readLocalOutput(executionId, process, output, finalConfigFile), "seatunnel-local-log-" + executionId);
            logReader.setDaemon(true);
            logReader.start();
            processes.put(executionId, process);
            configs.put(executionId, configFile);
            return new SubmitResult(executionId, "RUNNING");
        } catch (IOException | RuntimeException ex) {
            if (configFile != null) try { Files.deleteIfExists(configFile); } catch (IOException ignored) { }
            throw new IllegalStateException("SeaTunnel 本机任务启动失败：" + safeMessage(ex), ex);
        }
    }

    private SubmitResult submitRemote(String config, long clusterId) {
        if (sshClient == null) throw new IllegalStateException("SeaTunnel SSH 运行组件不可用");
        try {
            SeaTunnelSshClient.RemoteExecution remote = sshClient.start(clusterId, config);
            String executionId = "seatunnel-remote-" + UUID.randomUUID();
            StringBuffer output = new StringBuffer();
            append(output, "[SeaTunnel] " + LocalDateTime.now() + " 已提交到远程集群 " + remote.runtime().name());
            logs.put(executionId, output);
            remoteExecutions.put(executionId, remote);
            Thread logReader = new Thread(() -> readRemoteOutput(executionId, remote, output), "seatunnel-remote-log-" + executionId);
            logReader.setDaemon(true);
            logReader.start();
            return new SubmitResult(executionId, "RUNNING");
        } catch (Exception ex) {
            throw new IllegalStateException("SeaTunnel 远程任务启动失败：" + safeMessage(ex), ex);
        }
    }

    @Override public JobStatus status(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) return new JobStatus(executionId, "NOT_CONFIGURED", "SeaTunnel 真实执行未启用");
        String terminal = terminalStates.get(executionId);
        if (terminal != null) return new JobStatus(executionId, terminal, logText(executionId));
        Process process = processes.get(executionId);
        if (process != null) {
            String status = process.isAlive() ? "RUNNING" : process.exitValue() == 0 ? "FINISHED" : "FAILED";
            if (!process.isAlive()) rememberTerminal(executionId, status);
            return new JobStatus(executionId, status, logText(executionId));
        }
        SeaTunnelSshClient.RemoteExecution remote = remoteExecutions.get(executionId);
        if (remote != null) {
            boolean running = !remote.channel().isClosed();
            String status = running ? "RUNNING" : remote.channel().getExitStatus() == 0 ? "FINISHED" : "FAILED";
            if (!running) rememberTerminal(executionId, status);
            return new JobStatus(executionId, status, logText(executionId));
        }
        return new JobStatus(executionId, "NOT_FOUND", "当前应用实例未持有该 SeaTunnel 执行句柄");
    }

    @Override public void cancel(String executionId) {
        requireRealMode();
        Process process = processes.remove(executionId);
        if (process != null && process.isAlive()) {
            process.destroy();
            rememberTerminal(executionId, "CANCELLED");
            append(logs.computeIfAbsent(executionId, ignored -> new StringBuffer()), "[SeaTunnel] 任务已停止");
            return;
        }
        SeaTunnelSshClient.RemoteExecution remote = remoteExecutions.remove(executionId);
        if (remote != null) {
            remote.close();
            rememberTerminal(executionId, "CANCELLED");
            append(logs.computeIfAbsent(executionId, ignored -> new StringBuffer()), "[SeaTunnel] 远程任务执行通道已停止");
        }
    }

    @Override public String log(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) return "SeaTunnel 真实执行未启用";
        return logs.containsKey(executionId) ? logText(executionId) : "当前应用实例没有该 SeaTunnel 执行日志";
    }

    private void readLocalOutput(String executionId, Process process, StringBuffer output, Path configFile) {
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            reader.lines().forEach(line -> append(output, line));
            int exit = process.waitFor();
            append(output, "[SeaTunnel] process exited with code " + exit);
            rememberTerminal(executionId, exit == 0 ? "FINISHED" : "FAILED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            rememberTerminal(executionId, "FAILED");
            append(output, "[SeaTunnel] 日志读取被中断");
        } catch (Exception ex) {
            rememberTerminal(executionId, "FAILED");
            append(output, "[SeaTunnel] 日志读取失败：" + safeMessage(ex));
        } finally {
            processes.remove(executionId);
            configs.remove(executionId);
            try { Files.deleteIfExists(configFile); } catch (IOException ignored) { }
        }
    }

    private void readRemoteOutput(String executionId, SeaTunnelSshClient.RemoteExecution remote, StringBuffer output) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(remote.output(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) append(output, line);
            while (!remote.channel().isClosed()) Thread.sleep(100);
            int exit = remote.channel().getExitStatus();
            append(output, "[SeaTunnel] remote process exited with code " + exit);
            rememberTerminal(executionId, exit == 0 ? "FINISHED" : "FAILED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            rememberTerminal(executionId, "FAILED");
            append(output, "[SeaTunnel] 远程日志读取被中断");
        } catch (Exception ex) {
            rememberTerminal(executionId, "FAILED");
            append(output, "[SeaTunnel] 远程日志读取失败：" + safeMessage(ex));
        } finally {
            remoteExecutions.remove(executionId);
            remote.close();
        }
    }

    private void rememberTerminal(String executionId, String status) {
        terminalStates.put(executionId, status);
        if (terminalStates.size() > 2000) terminalStates.keySet().stream().findFirst().ifPresent(terminalStates::remove);
    }

    private void append(StringBuffer buffer, String line) {
        synchronized (buffer) { buffer.append(line).append(System.lineSeparator()); }
    }

    private String logText(String executionId) {
        StringBuffer value = logs.get(executionId);
        if (value == null) return "";
        synchronized (value) { return value.toString(); }
    }

    private boolean hasRequiredSections(String config) {
        return config != null && config.contains("env") && config.contains("source") && config.contains("sink");
    }
    private Path localExecutable() { return Path.of(properties.getSeatunnel().getHome(), "bin", "seatunnel.sh"); }
    private void requireRealMode() {
        if (!properties.getSeatunnel().isRealEnabled()) throw new IllegalStateException("SeaTunnel 真实执行未启用，禁止创建模拟任务");
    }
    private String safeMessage(Throwable ex) {
        String message = ex == null ? "未知错误" : ex.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message;
    }
    private void restrictPermissions(Path configFile) throws IOException {
        try {
            Files.setPosixFilePermissions(configFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) { }
    }

    @PreDestroy
    public void shutdown() {
        processes.values().forEach(process -> {
            if (process.isAlive()) try { process.destroy(); } catch (RuntimeException ignored) { }
        });
        remoteExecutions.values().forEach(remote -> {
            try { remote.close(); } catch (RuntimeException ignored) { }
        });
        configs.values().forEach(path -> {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        });
        processes.clear();
        remoteExecutions.clear();
        configs.clear();
    }
}
