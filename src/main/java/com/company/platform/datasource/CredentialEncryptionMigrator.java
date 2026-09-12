package com.company.platform.datasource;

import com.company.platform.common.PlatformStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;

@Component
public class CredentialEncryptionMigrator {
    private static final Logger log = LoggerFactory.getLogger(CredentialEncryptionMigrator.class);
    private final PlatformStore store;
    private final PasswordCipher cipher;

    public CredentialEncryptionMigrator(PlatformStore store, PasswordCipher cipher) {
        this.store = store;
        this.cipher = cipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void migrate() {
        if (!cipher.masterKeyConfigured()) return;
        int migrated = 0;
        migrated += migrateDataSources();
        migrated += migrateSeaTunnelClusters();
        migrated += migrateDolphinSchedulerClusters();
        if (migrated > 0) log.info("Credential encryption upgraded to v3 for {} persisted secret(s)", migrated);
    }

    private int migrateDataSources() {
        int count = 0;
        for (Map.Entry<Long, String> entry : new ArrayList<>(store.encryptedDataSourcePasswords.entrySet())) {
            if (!cipher.needsRotation(entry.getValue())) continue;
            var source = store.dataSources.get(entry.getKey());
            if (source == null) continue;
            String rotated = cipher.rotate(entry.getValue());
            store.persistDataSource(source, rotated);
            store.encryptedDataSourcePasswords.put(entry.getKey(), rotated);
            count++;
        }
        return count;
    }

    private int migrateSeaTunnelClusters() {
        int count = 0;
        for (Map.Entry<Long, String> entry : new ArrayList<>(store.encryptedClusterPasswords.entrySet())) {
            if (!cipher.needsRotation(entry.getValue())) continue;
            var cluster = store.seaTunnelClusters.get(entry.getKey());
            if (cluster == null) continue;
            String rotated = cipher.rotate(entry.getValue());
            store.persistSeaTunnelCluster(cluster, rotated);
            store.encryptedClusterPasswords.put(entry.getKey(), rotated);
            count++;
        }
        return count;
    }

    private int migrateDolphinSchedulerClusters() {
        int count = 0;
        for (Map.Entry<Long, String> entry : new ArrayList<>(store.encryptedDolphinSchedulerPasswords.entrySet())) {
            if (!cipher.needsRotation(entry.getValue())) continue;
            var cluster = store.dolphinSchedulerClusters.get(entry.getKey());
            if (cluster == null) continue;
            String rotated = cipher.rotate(entry.getValue());
            store.persistDolphinSchedulerCluster(cluster, rotated);
            store.encryptedDolphinSchedulerPasswords.put(entry.getKey(), rotated);
            count++;
        }
        return count;
    }
}
