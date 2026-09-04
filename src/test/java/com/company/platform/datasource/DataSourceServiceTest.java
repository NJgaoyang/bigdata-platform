package com.company.platform.datasource;

import com.company.platform.common.PlatformStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceServiceTest {
    @Test
    void buildsMysqlConnectionInfoAndDecryptsPassword() {
        PlatformStore store = new PlatformStore();
        DataSourceService service = new DataSourceService(store, new PasswordCipher(), new DynamicDataSourceManager());

        DataSourceView source = service.create(new CreateDataSourceRequest(
                "mysql-prod", DataSourceType.MYSQL, "192.168.113.135", 3306,
                "bigdata_platform", "root", "secret"));

        DataSourceService.ConnectionInfo info = service.connectionInfo(source.id());
        assertTrue(info.jdbcUrl().startsWith("jdbc:mysql://192.168.113.135:3306/bigdata_platform"));
        assertEquals("root", info.username());
        assertEquals("secret", info.password());
    }
}
