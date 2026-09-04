package com.company.platform.cluster;

import java.time.LocalDateTime;

public record SeaTunnelClusterView(long id, String name, String host, int port, String sshUsername,
                                   int sshPort, String seatunnelHome, String description,
                                   String healthStatus, LocalDateTime createdAt) { }
