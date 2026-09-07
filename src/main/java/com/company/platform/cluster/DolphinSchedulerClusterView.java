package com.company.platform.cluster;

import java.time.LocalDateTime;

public record DolphinSchedulerClusterView(long id, String name, String host, int port, String basePath,
                                          String version, String username, String installDir,
                                          String description, String healthStatus, LocalDateTime createdAt) { }
