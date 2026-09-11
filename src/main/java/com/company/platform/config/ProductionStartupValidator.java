package com.company.platform.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Production profile guardrails. */
@Component
@Profile("prod")
public class ProductionStartupValidator {
    private final PlatformProperties properties;
    private final JdbcTemplate jdbc;

    public ProductionStartupValidator(PlatformProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!properties.getSecurity().isEnabled()) {
            throw new IllegalStateException("生产环境必须开启 PLATFORM_AUTH_ENABLED=true");
        }
        requireText(properties.getSecurity().getAdminPasswordHash(), "PLATFORM_ADMIN_PASSWORD_SHA256");
        if (properties.getQuery().getMaxConcurrentQueries() < 1 || properties.getQuery().getMaxConcurrentQueries() > 64) {
            throw new IllegalStateException("PLATFORM_QUERY_MAX_CONCURRENT 必须在 1-64 之间");
        }
        String masterKey = System.getenv("DATASOURCE_MASTER_KEY");
        if (masterKey == null || masterKey.length() < 32) {
            throw new IllegalStateException("生产环境必须配置至少 32 位的 DATASOURCE_MASTER_KEY，禁止使用代码内置兼容密钥");
        }
        if (!properties.getSeatunnel().isRealEnabled()) {
            throw new IllegalStateException("生产环境必须开启 SEATUNNEL_REAL_ENABLED=true，禁止使用模拟同步");
        }
        PlatformProperties.Dolphinscheduler ds = properties.getScheduler().getDolphinscheduler();
        if (!ds.isRealEnabled()) {
            throw new IllegalStateException("生产环境必须开启 DOLPHINSCHEDULER_REAL_ENABLED=true，禁止使用模拟调度");
        }
        requireText(ds.getBaseUrl(), "DOLPHINSCHEDULER_BASE_URL");
        requireText(ds.getProjectCode(), "DOLPHINSCHEDULER_PROJECT_CODE");
        requireText(ds.getTenantCode(), "DOLPHINSCHEDULER_TENANT_CODE");
        if ((ds.getPassword() == null || ds.getPassword().isBlank())
                && (ds.getToken() == null || ds.getToken().isBlank())
                && !hasPersistedSchedulerCredentials()) {
            throw new IllegalStateException("生产环境必须配置 DolphinScheduler 密码/Token，或在系统设置中保存可用的 DolphinScheduler 集群凭据");
        }
        if (ds.getFailRetryTimes() < 0 || ds.getFailRetryTimes() > 10) {
            throw new IllegalStateException("DOLPHINSCHEDULER_FAIL_RETRY_TIMES 必须在 0-10 之间");
        }
        if (ds.getFailRetryInterval() < 1 || ds.getFailRetryInterval() > 60) {
            throw new IllegalStateException("DOLPHINSCHEDULER_FAIL_RETRY_INTERVAL 必须在 1-60 分钟之间");
        }
        requireText(ds.getWorkerGroup(), "DOLPHINSCHEDULER_WORKER_GROUP");
    }

    private boolean hasPersistedSchedulerCredentials() {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dolphinscheduler_cluster "
                    + "WHERE username IS NOT NULL AND TRIM(username)<>'' "
                    + "AND password_encrypted IS NOT NULL AND TRIM(password_encrypted)<>''", Integer.class);
            return count != null && count > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void requireText(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalStateException("生产环境缺少配置：" + key);
    }
}
