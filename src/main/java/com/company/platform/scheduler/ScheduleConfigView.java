package com.company.platform.scheduler;

public record ScheduleConfigView(long id, long workflowId, String cronExpression, String timezone,
                                 boolean enabled, String failureStrategy, int parallelism,
                                 String workerGroup, String alertGroup, String dsScheduleId) {
    public ScheduleConfigView(long id, long workflowId, String cronExpression, String timezone,
                              boolean enabled, String failureStrategy, int parallelism) {
        this(id, workflowId, cronExpression, timezone, enabled, failureStrategy, parallelism, "default", "", null);
    }
}
