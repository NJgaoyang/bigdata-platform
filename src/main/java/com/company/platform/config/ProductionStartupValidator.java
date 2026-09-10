package com.company.platform.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Production profile guardrails. The application must fail fast instead of
 * silently running with mock/disabled integrations or missing scheduler credentials.
 */
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
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("生产环境缺少配置：" + key);
        }
    }
}
