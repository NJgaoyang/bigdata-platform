package com.company.platform.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Production profile guardrails. */
@Component
@Profile("prod")
public class ProductionStartupValidator {
    private final PlatformProperties properties;

    public ProductionStartupValidator(PlatformProperties properties) {
        this.properties = properties;
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
                && (ds.getToken() == null || ds.getToken().isBlank())) {
            throw new IllegalStateException("生产环境必须配置 DOLPHINSCHEDULER_PASSWORD 或 DOLPHINSCHEDULER_TOKEN");
        }
        if (ds.getFailRetryTimes() < 0 || ds.getFailRetryTimes() > 10) {
            throw new IllegalStateException("DOLPHINSCHEDULER_FAIL_RETRY_TIMES 必须在 0-10 之间");
        }
        if (ds.getFailRetryInterval() < 1 || ds.getFailRetryInterval() > 60) {
            throw new IllegalStateException("DOLPHINSCHEDULER_FAIL_RETRY_INTERVAL 必须在 1-60 分钟之间");
        }
        requireText(ds.getWorkerGroup(), "DOLPHINSCHEDULER_WORKER_GROUP");
    }

    private void requireText(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalStateException("生产环境缺少配置：" + key);
    }
}
