package com.company.platform.integration;

import com.company.platform.config.PlatformProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SeaTunnel boundary. The default profile delegates to a deterministic fake;
 * the process adapter is only reachable after explicit final-acceptance enabling.
 */
@Component
public class SeaTunnelGatewayImpl implements SeaTunnelGateway {
    private final PlatformProperties properties;
    private final FakeSeaTunnelGateway fake = new FakeSeaTunnelGateway();
    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, Path> configs = new ConcurrentHashMap<>();
    private final Map<String, StringBuffer> logs = new ConcurrentHashMap<>();

    public SeaTunnelGatewayImpl(PlatformProperties properties) { this.properties = properties; }

    @Override public ValidationResult validate(String config) {
        if (config == null || config.isBlank()) return new ValidationResult(false, "配置不能为空");
        return fake.validate(config);
    }

    @Override public SubmitResult submit(String config) {
        if (!properties.getSeatunnel().isRealEnabled()) return fake.submit(config);
        try {
            Path home = Path.of(properties.getSeatunnel().getHome());
            Path executable = home.resolve("bin").resolve("seatunnel.sh");
            if (!Files.isExecutable(executable)) throw new IllegalStateException("SeaTunnel 启动脚本不可执行：" + executable);
            Path configDirectory = home.resolve("config");
            Files.createDirectories(configDirectory);
            Path configFile = Files.createTempFile(configDirectory, "platform-", ".conf");
            Files.writeString(configFile, config, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(executable.toString(), "-c", configFile.toString())
                    .redirectErrorStream(true).start();
            String executionId = "seatunnel-" + UUID.randomUUID();
            StringBuffer output = new StringBuffer();
            logs.put(executionId, output);
            Thread logReader = new Thread(() -> readOutput(executionId, process, output), "seatunnel-log-" + executionId);
            logReader.setDaemon(true);
            logReader.start();
            processes.put(executionId, process);
            configs.put(executionId, configFile);
            return new SubmitResult(executionId, "RUNNING");
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("SeaTunnel 任务启动失败：" + ex.getMessage(), ex);
        }
    }

    @Override public JobStatus status(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) return fake.status(executionId);
        Process process = processes.get(executionId);
        if (process == null) return new JobStatus(executionId, "NOT_FOUND", "SeaTunnel 实例不存在");
        String status = process.isAlive() ? "RUNNING" : process.exitValue() == 0 ? "FINISHED" : "FAILED";
        return new JobStatus(executionId, status, logs.getOrDefault(executionId, new StringBuffer()).toString());
    }

    @Override public void cancel(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) { fake.cancel(executionId); return; }
        Process process = processes.get(executionId);
        if (process != null) process.destroy();
    }

    @Override public String log(String executionId) {
        if (!properties.getSeatunnel().isRealEnabled()) return fake.log(executionId);
        return logs.getOrDefault(executionId, new StringBuffer("SeaTunnel 实例不存在")).toString();
    }

    private void readOutput(String executionId, Process process, StringBuffer output) {
        try (var reader = process.inputReader()) {
            reader.lines().forEach(line -> { synchronized (output) { output.append(line).append(System.lineSeparator()); } });
            int exit = process.waitFor();
            synchronized (output) { output.append("[SeaTunnel] process exited with code ").append(exit).append(System.lineSeparator()); }
        } catch (Exception ex) {
            synchronized (output) { output.append("[SeaTunnel] log reader failed: ").append(ex.getMessage()).append(System.lineSeparator()); }
        } finally {
            Path config = configs.remove(executionId);
            if (config != null) try { Files.deleteIfExists(config); } catch (IOException ignored) { }
        }
    }
}
