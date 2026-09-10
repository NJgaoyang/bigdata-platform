package com.company.platform.integration;

import com.company.platform.common.PlatformStore;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reconciles in-memory SeaTunnel process/channel state with persisted integration
 * instances. This keeps the dashboard truthful after completion and turns stale
 * RUNNING rows into LOST after an application restart instead of showing them as
 * running forever.
 */
@Component
public class IntegrationExecutionMonitor {
    private static final int MAX_PERSISTED_LOG_CHARS = 60_000;
    private final PlatformStore store;
    private final SeaTunnelGateway gateway;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("integration-execution-monitor").factory());
    private final AtomicBoolean started = new AtomicBoolean();

    public IntegrationExecutionMonitor(PlatformStore store, SeaTunnelGateway gateway) {
        this.store = store;
        this.gateway = gateway;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (started.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::safeRefresh, 0, 2, TimeUnit.SECONDS);
        }
    }

    /** Visible for focused tests and operational diagnostics. */
    public void refreshNow() { refresh(); }

    private void safeRefresh() {
        try { refresh(); }
        catch (RuntimeException ignored) {
            // One transient remote/runtime failure must not kill the monitoring loop.
        }
    }

    private void refresh() {
        List<IntegrationInstanceView> active = store.integrationInstances.values().stream()
                .filter(instance -> active(instance.status()))
                .toList();
        for (IntegrationInstanceView instance : active) refreshInstance(instance);
        active.stream().map(IntegrationInstanceView::taskId).distinct().forEach(this::syncTaskStatus);
    }

    private void refreshInstance(IntegrationInstanceView instance) {
        SeaTunnelGateway.JobStatus runtime;
        try {
            runtime = gateway.status(instance.executionId());
        } catch (RuntimeException ex) {
            // A temporary status lookup error does not prove the SeaTunnel job failed.
            return;
        }
        String next = normalizeRuntimeStatus(runtime.status());
        String message = runtime.message();
        if ("NOT_FOUND".equals(next)) {
            next = "LOST";
            message = "应用重启或 SeaTunnel 执行句柄已丢失，无法继续确认该实例的远程运行状态";
        }
        if (active(next)) {
            if (!next.equalsIgnoreCase(instance.status())) {
                persistInstance(instance, next, null, shortMessage(message));
            }
            return;
        }
        if (terminal(next)) {
            persistInstance(instance, next, LocalDateTime.now(), persistedLog(message));
        }
    }

    private void persistInstance(IntegrationInstanceView current, String status, LocalDateTime finishedAt, String message) {
        IntegrationInstanceView updated = new IntegrationInstanceView(current.id(), current.taskId(), current.executionId(),
                status, current.startedAt(), finishedAt == null ? current.finishedAt() : finishedAt,
                message == null || message.isBlank() ? current.message() : message);
        store.persistIntegrationInstance(updated);
        store.integrationInstances.put(updated.id(), updated);
        syncTaskStatus(updated.taskId());
    }

    private void syncTaskStatus(long taskId) {
        IntegrationTaskView task = store.integrationTasks.get(taskId);
        if (task == null) return;
        List<IntegrationInstanceView> instances = store.integrationInstances.values().stream()
                .filter(instance -> instance.taskId() == taskId)
                .sorted(Comparator.comparing(IntegrationInstanceView::startedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (instances.isEmpty()) return;
        String status = instances.stream().anyMatch(instance -> active(instance.status()))
                ? "RUNNING" : taskStatus(instances.get(0).status());
        if (status.equalsIgnoreCase(task.status())) return;
        IntegrationTaskView updated = new IntegrationTaskView(task.id(), task.name(), task.sourceType(), task.targetType(),
                task.syncMode(), status, task.sourceConfigJson(), task.targetConfigJson(), task.transformConfigJson(),
                task.seatunnelConfig(), task.tables());
        store.persistIntegrationTask(updated);
        store.integrationTasks.put(updated.id(), updated);
    }

    private String taskStatus(String status) {
        String value = normalizeRuntimeStatus(status);
        if (Set.of("SUCCESS", "FINISHED").contains(value)) return "SUCCESS";
        if (Set.of("FAILED", "ERROR", "CANCELLED", "KILLED", "LOST", "STOPPED").contains(value)) return "FAILED";
        if (active(value)) return "RUNNING";
        return value.isBlank() ? "UNKNOWN" : value;
    }

    private String normalizeRuntimeStatus(String status) {
        if (status == null) return "UNKNOWN";
        String value = status.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "SUCCESS", "SUCCEEDED" -> "FINISHED";
            case "FAIL", "ERROR" -> "FAILED";
            case "CANCEL", "CANCELED" -> "CANCELLED";
            default -> value;
        };
    }

    private boolean active(String status) {
        String value = normalizeRuntimeStatus(status);
        return "RUNNING".equals(value) || "SUBMITTED".equals(value);
    }

    private boolean terminal(String status) {
        return Set.of("FINISHED", "FAILED", "CANCELLED", "KILLED", "LOST", "STOPPED").contains(normalizeRuntimeStatus(status));
    }

    private String shortMessage(String message) {
        if (message == null || message.isBlank()) return null;
        String value = message.strip();
        return value.length() <= 1000 ? value : value.substring(value.length() - 1000);
    }

    private String persistedLog(String message) {
        if (message == null || message.isBlank()) return null;
        String value = message.strip();
        if (value.length() <= MAX_PERSISTED_LOG_CHARS) return value;
        return "[日志已截断，仅保留最后 " + MAX_PERSISTED_LOG_CHARS + " 字符]\n"
                + value.substring(value.length() - MAX_PERSISTED_LOG_CHARS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
