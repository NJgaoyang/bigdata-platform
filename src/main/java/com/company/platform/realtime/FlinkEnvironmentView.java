package com.company.platform.realtime;

import java.time.LocalDateTime;

public record FlinkEnvironmentView(long id, String name, String engineType, String deploymentMode, String submitterType,
        String restUrl, String flinkHome, String flinkCdcHome, String javaHome, String flinkVersion, String flinkCdcVersion,
        String sshHost, int sshPort, String sshUsername, boolean enabled, boolean defaultEnvironment,
        LocalDateTime createdAt, LocalDateTime updatedAt) { }
