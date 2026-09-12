package com.company.platform.realtime;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public final class RealtimeRequests {
    private RealtimeRequests() { }
    public record EnvironmentRequest(@NotBlank String name, String deploymentMode, String submitterType, String restUrl,
            String flinkHome, String flinkCdcHome, String javaHome, String flinkVersion, String flinkCdcVersion,
            String sshHost, Integer sshPort, String sshUsername, String sshPassword, Boolean enabled, Boolean defaultEnvironment) { }
    public record CreateJobRequest(@NotBlank String name, String description, Long runtimeEnvironmentId, Map<String,Object> spec) { }
    public record DraftRequest(String name, String description, Long runtimeEnvironmentId, Map<String,Object> spec) { }
}
