package com.company.platform.system;

import com.company.platform.config.DataSphereProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCredentialInitializer implements ApplicationRunner {
    private final DataSphereProperties properties;
    private final JdbcTemplate jdbc;

    public AdminCredentialInitializer(DataSphereProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String initialPassword = properties.getSecurity().getAdminInitialPassword();
        if (initialPassword == null || initialPassword.isBlank()) return;
        String configured = properties.getSecurity().getAdminUsername();
        String username = configured == null || configured.isBlank() ? "admin" : configured.trim();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,password_hash FROM platform_user WHERE LOWER(username)=LOWER(?) LIMIT 1", username);
        String hash = PasswordHasher.hash(initialPassword);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO platform_user(username,display_name,role_code,status,created_at,password_hash) "
                    + "VALUES(?,?,'ADMIN','ACTIVE',CURRENT_TIMESTAMP,?)", username, "平台管理员", hash);
        } else {
            Object current = rows.getFirst().get("password_hash");
            if (current == null || current.toString().isBlank()) {
                jdbc.update("UPDATE platform_user SET password_hash=? WHERE id=?", hash, rows.getFirst().get("id"));
            }
        }
        properties.getSecurity().setAdminInitialPassword(null);
    }
}
