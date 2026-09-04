package com.company.platform.cluster;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class SeaTunnelClusterRequests {
    private SeaTunnelClusterRequests() { }

    public record ClusterRequest(@NotBlank String name, @NotBlank String host, @Min(1) @Max(65535) int port,
                                 String sshUsername, @Min(1) @Max(65535) int sshPort, String sshPassword,
                                 @NotBlank String seatunnelHome, String description) { }
}
