package com.company.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {
    private final Features features = new Features();
    private final Query query = new Query();
    private final Starrocks starrocks = new Starrocks();
    private final Seatunnel seatunnel = new Seatunnel();
    private final Scheduler scheduler = new Scheduler();
    private final Security security = new Security();

    public Features getFeatures() { return features; }
    public Query getQuery() { return query; }
    public Starrocks getStarrocks() { return starrocks; }
    public Seatunnel getSeatunnel() { return seatunnel; }
    public Scheduler getScheduler() { return scheduler; }
    public Security getSecurity() { return security; }

    public static class Security {
        private boolean enabled;
        private String adminUsername = "admin";
        private String adminPasswordHash;
        private int sessionTtlMinutes = 480;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAdminUsername() { return adminUsername; }
        public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
        public String getAdminPasswordHash() { return adminPasswordHash; }
        public void setAdminPasswordHash(String adminPasswordHash) { this.adminPasswordHash = adminPasswordHash; }
        public int getSessionTtlMinutes() { return sessionTtlMinutes; }
        public void setSessionTtlMinutes(int sessionTtlMinutes) { this.sessionTtlMinutes = sessionTtlMinutes; }
    }

    public static class Features {
        private boolean realtimeSync;
        public boolean isRealtimeSync() { return realtimeSync; }
        public void setRealtimeSync(boolean realtimeSync) { this.realtimeSync = realtimeSync; }
    }
    public static class Query {
        private int defaultMaxRows = 1000;
        private int timeoutSeconds = 300;
        public int getDefaultMaxRows() { return defaultMaxRows; }
        public void setDefaultMaxRows(int value) { this.defaultMaxRows = value; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int value) { this.timeoutSeconds = value; }
    }
    public static class Starrocks {
        private String version = "3.3.22";
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
    public static class Seatunnel {
        private String version = "2.3.12";
        private String home = "/data/software/seatunnel";
        private boolean realEnabled;
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getHome() { return home; }
        public void setHome(String home) { this.home = home; }
        public boolean isRealEnabled() { return realEnabled; }
        public void setRealEnabled(boolean realEnabled) { this.realEnabled = realEnabled; }
    }
    public static class Scheduler {
        private String type = "dolphinscheduler";
        private final Dolphinscheduler dolphinscheduler = new Dolphinscheduler();
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Dolphinscheduler getDolphinscheduler() { return dolphinscheduler; }
    }
    public static class Dolphinscheduler {
        private String baseUrl = "http://127.0.0.1:12345";
        private String version = "3.1.9";
        private String installDir = "/data/software/dolphinscheduler";
        private String username = "admin";
        private String projectCode = "bigdata-platform";
        private String tenantCode = "bigdata";
        private String password;
        private String token;
        private boolean realEnabled;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getInstallDir() { return installDir; }
        public void setInstallDir(String installDir) { this.installDir = installDir; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getProjectCode() { return projectCode; }
        public void setProjectCode(String projectCode) { this.projectCode = projectCode; }
        public String getTenantCode() { return tenantCode; }
        public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public boolean isRealEnabled() { return realEnabled; }
        public void setRealEnabled(boolean realEnabled) { this.realEnabled = realEnabled; }
    }
}
