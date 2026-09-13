package com.company.platform.config;

import com.company.platform.datasource.PasswordCipher;

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
    private final PasswordCipher passwordCipher;

    public ProductionStartupValidator(PlatformProperties properties, JdbcTemplate jdbc, PasswordCipher passwordCipher) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.passwordCipher = passwordCipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!properties.getSecurity().isEnabled()) {
            throw new IllegalStateException("生产环境必须开启 PLATFORM_AUTH_ENABLED=true");
        }
        if ((properties.getSecurity().getAdminPasswordHash() == null || properties.getSecurity().getAdminPasswordHash().isBlank())
                && !hasPersistedAdminCredential()) {
            throw new IllegalStateException("生产环境必须配置管理员登录凭据：PLATFORM_ADMIN_PASSWORD_SHA256，或为 admin 用户设置 PBKDF2 密码");
        }
        if (properties.getQuery().getMaxConcurrentQueries() < 1 || properties.getQuery().getMaxConcurrentQueries() > 64) {
            throw new IllegalStateException("PLATFORM_QUERY_MAX_CONCURRENT 必须在 1-64 之间");
        }
        if (!passwordCipher.masterKeyConfigured()) {
            throw new IllegalStateException("生产环境必须配置 DATASOURCE_MASTER_KEY，禁止使用代码内置兼容密钥");
        }
        if (!properties.getSeatunnel().isRealEnabled()) {
            throw new IllegalStateException("生产环境必须开启 SEATUNNEL_REAL_ENABLED=true，禁止使用模拟同步");
        }
        String schedulerType = properties.getScheduler().getType() == null ? "local" : properties.getScheduler().getType().trim().toLowerCase();
        if (!"local".equals(schedulerType) && !"dolphinscheduler".equals(schedulerType)) {
            throw new IllegalStateException("PLATFORM_SCHEDULER_TYPE 仅支持 local / dolphinscheduler");
        }
        if ("dolphinscheduler".equals(schedulerType)) {
            PlatformProperties.Dolphinscheduler ds = properties.getScheduler().getDolphinscheduler();
            if (!ds.isRealEnabled()) throw new IllegalStateException("DolphinScheduler 兼容模式必须开启 DOLPHINSCHEDULER_REAL_ENABLED=true");
            requireText(ds.getBaseUrl(), "DOLPHINSCHEDULER_BASE_URL");
            requireText(ds.getProjectCode(), "DOLPHINSCHEDULER_PROJECT_CODE");
            requireText(ds.getTenantCode(), "DOLPHINSCHEDULER_TENANT_CODE");
            if ((ds.getPassword() == null || ds.getPassword().isBlank()) && (ds.getToken() == null || ds.getToken().isBlank()) && !hasPersistedSchedulerCredentials()) {
                throw new IllegalStateException("DolphinScheduler 兼容模式必须配置密码/Token，或保存可用的集群凭据");
            }
        }
    }


    boolean hasPersistedAdminCredential() {
        try {
            String configured = properties.getSecurity().getAdminUsername();
            String username = configured == null || configured.isBlank() ? "admin" : configured.trim();
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_user "
                    + "WHERE status='ACTIVE' AND (LOWER(username)=LOWER(?) OR LOWER(username)='admin') "
                    + "AND password_hash LIKE 'pbkdf2-sha256$%'", Integer.class, username);
            return count != null && count > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
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
