package com.company.platform.integration;

import com.company.platform.config.PlatformProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** SeaTunnel process boundary. Runtime execution never falls back to simulated jobs. */
@Component
public class SeaTunnelGatewayImpl implements SeaTunnelGateway {
    private final PlatformProperties properties;
    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, Path> configs = new ConcurrentHashMap<>();
    private final Map<String, StringBuffer> logs = new ConcurrentHashMap<>();

    public SeaTunnelGatewayImpl(PlatformProperties properties) { this.properties = properties; }

    @Override public ValidationResult validate(String config) {
        if (config == null || config.isBlank()) return new ValidationResult(false, "配置不能为空");
        if (properties.getSeatunnel().isRealEnabled()) {
            Path executable = Path.of(properties.getSeatunnel().getHome()).resolve("bin").resolve("seatunnel.sh");
            if (!Files.isExecutable(executable)) return new ValidationResult(false, "SeaTunnel 启动脚本不可执行：" + executable);
            if (!config.contains("env") || !config.contains("source") || !config.contains("sink")) {
                return new ValidationResult(false, "SeaTunnel 配置必须包含 env、source 和 sink");
            }
            return new ValidationResult(true, "SeaTunnel 配置与真实运行环境检查通过");
        }
        return new ValidationResult(false, "SeaTunnel 真实执行未启用");
    }

    @Override public SubmitResult submit(String config) {
        if (!properties.getSeatunnel().isRealEnabled()) throw new IllegalStateException("SeaTunnel 真实执行未启用，禁止创建模拟任务");
        Path configFile = null;
        try {
            Path home = Path.of(properties.getSeatunnel().getHome());
            Path executable = home.resolve("bin").resolve("seatunnel.sh");
            if (!Files.isExecutable(executable)) throw new IllegalStateException("SeaTunnel 启动脚本不可执行：" + executable);
            Path configDirectory = home.resolve("config");
            Files.createDirectories(configDirectory);
            configFile = Files.createTempFile(configDirectory, "platform-", ".conf");
            restrictPermissions(configFile);
            Files.writeString(configFile, config, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(executable.toString(), "-c", configFile.toString())
                    .redirectErrorStream(true).start();
            String executionId = "seatunnel-" + UUID.randomUUID();
            StringBuffer output = new StringBuffer();
            logs.put(executionId, output);
            Path finalConfigFile = configFile;
            Thread logReader = new Thread(() -> readOutput(executionId, process, output, finalConfigFile), "seatunnel-log-" + executionId);
            logReader.setDaemon(true);
            logReader.start();
            processes.put(executionId, process);
            configs.put(executionId, configFile);
            return new SubmitResult(executionId, "RUNNING");
        } catch (IOException | RuntimeException ex) {
            if (configFile != null) try { Files.deleteIfExists(configFile); } catch (IOException ignored) { }
            throw new IllegalStateException("SeaTunnel 任务启动失败：" + ex.getMessage(), ex);
        }
    }

    @Override public JobStatus status(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) return new JobStatus(executionId, "NOT_CONFIGURED", "SeaTunnel 真实执行未启用");
        Process process = processes.get(executionId);
        if (process == null) return new JobStatus(executionId, "NOT_FOUND", "SeaTunnel 实例不存在");
        String status = process.isAlive() ? "RUNNING" : process.exitValue() == 0 ? "FINISHED" : "FAILED";
        return new JobStatus(executionId, status, logs.getOrDefault(executionId, new StringBuffer()).toString());
    }

    @Override public void cancel(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) throw new IllegalStateException("SeaTunnel 真实执行未启用");
        Process process = processes.get(executionId);
        if (process != null) process.destroy();
    }

    @Override public String log(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) return "SeaTunnel 真实执行未启用";
        return logs.getOrDefault(executionId, new StringBuffer("SeaTunnel 实例不存在")).toString();
    }

    private void readOutput(String executionId, Process process, StringBuffer output, Path configFile) {
        try (var reader = process.inputReader()) {
            reader.lines().forEach(line -> { synchronized (output) { output.append(line).append(System.lineSeparator()); } });
            int exit = process.waitFor();
            synchronized (output) { output.append("[SeaTunnel] process exited with code ").append(exit).append(System.lineSeparator()); }
        } catch (Exception ex) {
            synchronized (output) { output.append("[SeaTunnel] log reader failed: ").append(ex.getMessage()).append(System.lineSeparator()); }
        } finally {
            configs.remove(executionId);
            try { Files.deleteIfExists(configFile); } catch (IOException ignored) { }
        }
    }

    private void restrictPermissions(Path configFile) throws IOException {
        try {
            Files.setPosixFilePermissions(configFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX development filesystems do not expose chmod semantics.
        }
    }

    @PreDestroy
    public void shutdown() {
        processes.values().forEach(process -> {
            if (process.isAlive()) {
                try { process.destroy(); } catch (RuntimeException ignored) { }
            }
        });
        configs.values().forEach(path -> {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        });
        configs.clear();
    }
}
