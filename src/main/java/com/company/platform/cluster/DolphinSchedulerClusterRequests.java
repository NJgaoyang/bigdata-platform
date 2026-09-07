package com.company.platform.cluster;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class DolphinSchedulerClusterRequests {
    private DolphinSchedulerClusterRequests() { }

    public record ClusterRequest(@NotBlank String name, @NotBlank String host, @Min(1) @Max(65535) int port,
                                 String basePath, String version, String username, String password,
                                 String installDir, String description) { }
}
