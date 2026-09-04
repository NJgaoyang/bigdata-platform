package com.company.platform.integration;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class IntegrationService {
    private final PlatformStore store;
    private final SeaTunnelConfigBuilder builder;
    private final SeaTunnelGateway gateway;

    public IntegrationService(PlatformStore store, SeaTunnelConfigBuilder builder, SeaTunnelGateway gateway) {
        this.store = store;
        this.builder = builder;
        this.gateway = gateway;
    }
    public List<IntegrationTaskView> list() { return store.integrationTasks.values().stream().map(this::masked).toList(); }
    public IntegrationTaskView create(IntegrationRequests.TaskRequest request) {
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), request.source(), request.target(), request.mappings(), request.options());
        String config = builder.build(task);
        long id = store.nextId();
        IntegrationTaskView view = new IntegrationTaskView(id, request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), "DRAFT", config);
        store.integrationTasks.put(id, view);
        store.persistIntegrationTask(view);
        return masked(view);
    }
    public IntegrationTaskView update(long id, IntegrationRequests.TaskRequest request) {
        raw(id);
        IntegrationTask task = new IntegrationTask(request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), request.source(), request.target(), request.mappings(), request.options());
        IntegrationTaskView view = new IntegrationTaskView(id, request.name(), request.sourceType(), request.targetType(),
                request.syncMode(), "DRAFT", builder.build(task));
        store.integrationTasks.put(id, view);
        store.persistIntegrationTask(view);
        return masked(view);
    }
    public IntegrationTaskView get(long id) {
        IntegrationTaskView view = store.integrationTasks.get(id);
        if (view == null) throw new NotFoundException("离线同步任务不存在：" + id);
        return masked(view);
    }
    public void delete(long id) { if (store.integrationTasks.remove(id) == null) throw new NotFoundException("离线同步任务不存在：" + id); store.deleteIntegrationTask(id); }
    public SeaTunnelGateway.ValidationResult validate(long id) { return gateway.validate(raw(id).seatunnelConfig()); }
    public SeaTunnelGateway.SubmitResult execute(long id) {
        SeaTunnelGateway.SubmitResult result = gateway.submit(raw(id).seatunnelConfig());
        long instanceId = store.nextId();
        IntegrationInstanceView instance = new IntegrationInstanceView(instanceId, id, result.executionId(), result.status(), LocalDateTime.now(),
                "SUCCESS".equals(result.status()) ? LocalDateTime.now() : null, "SeaTunnel 执行实例");
        store.integrationInstances.put(instanceId, instance);
        store.persistIntegrationInstance(instance);
        return result;
    }
    public void stop(String executionId) { gateway.cancel(executionId); }
    public List<IntegrationInstanceView> instances(long taskId) { return store.integrationInstances.values().stream().filter(item -> item.taskId() == taskId).toList(); }
    public SeaTunnelGateway.JobStatus status(String executionId) {
        SeaTunnelGateway.JobStatus status = gateway.status(executionId);
        store.integrationInstances.values().stream().filter(item -> executionId.equals(item.executionId())).findFirst().ifPresent(instance -> {
            IntegrationInstanceView updated = new IntegrationInstanceView(instance.id(), instance.taskId(), instance.executionId(),
                    status.status(), instance.startedAt(), "RUNNING".equals(status.status()) ? null : LocalDateTime.now(), status.message());
            store.integrationInstances.put(instance.id(), updated);
            store.persistIntegrationInstance(updated);
        });
        return status;
    }
    public String log(String executionId) { return gateway.log(executionId); }
    public void cancel(String executionId) { gateway.cancel(executionId); }

    private IntegrationTaskView raw(long id) {
        IntegrationTaskView view = store.integrationTasks.get(id);
        if (view == null) throw new NotFoundException("离线同步任务不存在：" + id);
        return view;
    }
    private IntegrationTaskView masked(IntegrationTaskView view) {
        String masked = view.seatunnelConfig() == null ? null : view.seatunnelConfig()
                .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*(\")", "$1***$2")
                .replaceAll("(?m)(password\\s*=\\s*\")[^\"]*(\")", "$1***$2");
        return new IntegrationTaskView(view.id(), view.name(), view.sourceType(), view.targetType(), view.syncMode(), view.status(), masked);
    }
}
