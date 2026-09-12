package com.company.platform.realtime;

import java.time.LocalDateTime;
import java.util.Map;

public final class RealtimeViews {
    private RealtimeViews() { }
    public record Job(long id, String name, String description, Long runtimeEnvironmentId, String releaseState,
                      String desiredState, String observedState, int definitionVersion, Integer publishedVersion,
                      Map<String,Object> spec, String configDigest, String lastError, String createdBy,
                      LocalDateTime createdAt, LocalDateTime updatedAt, boolean publishedUpdateAvailable) { }
    public record Execution(long id, long jobId, int definitionVersion, String engineJobId, String runtimeRevision,
                            String status, boolean resultUncertain, String errorMessage, LocalDateTime startedAt,
                            LocalDateTime finishedAt, LocalDateTime createdAt) { }
    public record Runtime(Job job, Execution execution, FlinkEnvironmentView environment) { }
}
