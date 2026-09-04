package com.company.platform.scheduler;

public record ScheduleConfigView(long id, long workflowId, String cronExpression, String timezone,
                                 boolean enabled, String failureStrategy, int parallelism) { }
