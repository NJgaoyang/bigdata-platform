package com.company.platform.scheduler;

import jakarta.validation.constraints.NotBlank;

public final class ScheduleRequests {
    private ScheduleRequests() { }
    public record ScheduleRequest(@NotBlank String cronExpression, String timezone, boolean enabled,
                                   String failureStrategy, int parallelism, String workerGroup, String alertGroup) {
        public ScheduleRequest(String cronExpression, String timezone, boolean enabled,
                               String failureStrategy, int parallelism) {
            this(cronExpression, timezone, enabled, failureStrategy, parallelism, "default", "");
        }
        public ScheduleRequest {
            timezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
            failureStrategy = failureStrategy == null || failureStrategy.isBlank() ? "END" : failureStrategy;
            parallelism = parallelism == 0 ? 1 : parallelism;
            workerGroup = workerGroup == null || workerGroup.isBlank() ? "default" : workerGroup;
            alertGroup = alertGroup == null ? "" : alertGroup;
        }
    }
    public record BackfillRequest(@NotBlank String start, @NotBlank String end, int parallelism) {
        public BackfillRequest { if (parallelism == 0) parallelism = 1; }
    }
}
