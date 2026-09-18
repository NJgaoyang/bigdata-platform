package com.company.platform.system;

import com.company.platform.common.PlatformStore;
import com.company.platform.config.DataSphereProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCredentialInitializer implements ApplicationRunner {
    private final DataSphereProperties properties;
    private final PlatformStore store;

    public AdminCredentialInitializer(DataSphereProperties properties, PlatformStore store) {
        this.properties = properties;
        this.store = store;
    }

    @Override
    public void run(ApplicationArguments args) {
        String initialPassword = properties.getSecurity().getAdminInitialPassword();
        if (initialPassword == null || initialPassword.isBlank()) return;
        String configured = properties.getSecurity().getAdminUsername();
        String username = configured == null || configured.isBlank() ? "admin" : configured.trim();
        UserView user = store.users.values().stream()
                .filter(item -> item.username().equalsIgnoreCase(username))
                .findFirst().orElse(null);
        if (user != null && (user.passwordHash() == null || user.passwordHash().isBlank())) {
            UserView initialized = new UserView(user.id(), user.username(), user.displayName(), user.phone(), user.roleCode(),
                    user.status(), user.createdAt(), PasswordHasher.hash(initialPassword));
            store.persistUser(initialized);
            store.users.put(initialized.id(), initialized);
        }
        properties.getSecurity().setAdminInitialPassword(null);
    }
}
