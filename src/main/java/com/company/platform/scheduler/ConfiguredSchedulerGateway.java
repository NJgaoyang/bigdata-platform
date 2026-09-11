package com.company.platform.scheduler;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Ensures every real scheduler operation uses the latest persisted DS cluster configuration. */
@Component
@Primary
public class ConfiguredSchedulerGateway implements SchedulerGateway {
    private final DolphinSchedulerGatewayImpl delegate;
    private final DolphinSchedulerRuntimeConfigurator runtime;

    public ConfiguredSchedulerGateway(DolphinSchedulerGatewayImpl delegate,
                                      DolphinSchedulerRuntimeConfigurator runtime) {
        this.delegate = delegate;
        this.runtime = runtime;
    }

    private void prepare() { runtime.refresh(true); }

    @Override public PublishResult publish(PublishRequest request) { prepare(); return delegate.publish(request); }
    @Override public RunResult run(String processCode) { prepare(); return delegate.run(processCode); }
    @Override public InstanceStatus status(String instanceId) { prepare(); return delegate.status(instanceId); }
    @Override public void stop(String instanceId) { prepare(); delegate.stop(instanceId); }
    @Override public RunResult rerun(String instanceId) { prepare(); return delegate.rerun(instanceId); }
    @Override public RunResult backfill(String processCode, String start, String end, int parallelism) {
        prepare();
        return delegate.backfill(processCode, start, end, parallelism);
    }
    @Override public void release(String processCode, boolean online) { prepare(); delegate.release(processCode, online); }
    @Override public String upsertSchedule(String processCode, String cronExpression, String timezone, boolean enabled,
                                           String failureStrategy, int parallelism) {
        prepare();
        return delegate.upsertSchedule(processCode, cronExpression, timezone, enabled, failureStrategy, parallelism);
    }
    @Override public String upsertSchedule(String processCode, String cronExpression, String timezone, boolean enabled,
                                           String failureStrategy, int parallelism, String workerGroup, String alertGroup) {
        prepare();
        return delegate.upsertSchedule(processCode, cronExpression, timezone, enabled, failureStrategy,
                parallelism, workerGroup, alertGroup);
    }
    @Override public void scheduleState(String scheduleId, boolean online) { prepare(); delegate.scheduleState(scheduleId, online); }
    @Override public List<Map<String, Object>> listProcessInstances() { prepare(); return delegate.listProcessInstances(); }
    @Override public List<Map<String, Object>> listTaskInstances() { prepare(); return delegate.listTaskInstances(); }
    @Override public String taskLog(String taskInstanceId) { prepare(); return delegate.taskLog(taskInstanceId); }
    @Override public boolean isRealMode() {
        runtime.refresh(false);
        return delegate.isRealMode();
    }
}
