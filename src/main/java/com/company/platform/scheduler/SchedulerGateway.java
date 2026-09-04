package com.company.platform.scheduler;

import java.util.List;
import java.util.Map;

public interface SchedulerGateway {
    PublishResult publish(PublishRequest request);
    RunResult run(String processCode);
    InstanceStatus status(String instanceId);
    void stop(String instanceId);
    RunResult rerun(String instanceId);
    RunResult backfill(String processCode, String start, String end, int parallelism);

    default void release(String processCode, boolean online) { }
    default String upsertSchedule(String processCode, String cronExpression, String timezone, boolean enabled,
                                  String failureStrategy, int parallelism) { return ""; }
    default void scheduleState(String scheduleId, boolean online) { }
    default List<Map<String, Object>> listProcessInstances() { return List.of(); }
    default List<Map<String, Object>> listTaskInstances() { return List.of(); }
    default String taskLog(String taskInstanceId) { return "暂无任务日志"; }
    default boolean isRealMode() { return false; }

    record PublishRequest(String workflowCode, String name, int version, String definitionJson) { }
    record PublishResult(String processCode, int version, String status) { }
    record RunResult(String instanceId, String status) { }
    record InstanceStatus(String instanceId, String status, String log) { }
}
