package com.company.platform.datasource;

import com.company.platform.common.PlatformStore;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.company.platform.common.BadRequestException;

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

    @Test
    void rejectsDuplicateDataSourceNameWithActionableMessage() {
        PlatformStore store = new PlatformStore();
        DataSourceService service = new DataSourceService(store, new PasswordCipher(), new DynamicDataSourceManager());
        service.create(new CreateDataSourceRequest("warehouse", DataSourceType.STARROCKS, "host", 9030, "ods", "user", "secret"));
        BadRequestException error = assertThrows(BadRequestException.class, () ->
                service.create(new CreateDataSourceRequest("WAREHOUSE", DataSourceType.STARROCKS, "other", 9030, "ods", "user", "secret")));
        assertEquals("数据源名称已存在，请使用其他名称或编辑已有数据源", error.getMessage());
    }

    @Test
    void allowsDataSourceWithoutDefaultDatabase() {
        PlatformStore store = new PlatformStore();
        DataSourceService service = new DataSourceService(store, new PasswordCipher(), new DynamicDataSourceManager());

        DataSourceView source = service.create(new CreateDataSourceRequest(
                "mysql-server", DataSourceType.MYSQL, "db.example", 3306,
                null, "root", "secret"));

        DataSourceService.ConnectionInfo info = service.connectionInfo(source.id());
        assertEquals("", source.databaseName());
        assertEquals("", info.databaseName());
        assertTrue(info.jdbcUrl().startsWith("jdbc:mysql://db.example:3306/?"));
    }

    @Test
    void requestValidationTreatsDatabaseAsOptional() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var request = new CreateDataSourceRequest(
                    "mysql-server", DataSourceType.MYSQL, "db.example", 3306,
                    "", "root", "secret");

            assertTrue(validatorFactory.getValidator().validate(request).isEmpty());
        }
    }

    @Test
    void persistsMetadataVisibilityAcrossOrdinaryEdits() {
        PlatformStore store = new PlatformStore();
        DataSourceService service = new DataSourceService(store, new PasswordCipher(), new DynamicDataSourceManager());
        DataSourceView source = service.create(new CreateDataSourceRequest(
                "warehouse", DataSourceType.STARROCKS, "db.example", 9030,
                "ods", "reader", "secret"));

        DataSourceView hidden = service.setMetadataVisible(source.id(), false);
        DataSourceView edited = service.update(source.id(), new CreateDataSourceRequest(
                "warehouse", DataSourceType.STARROCKS, "db.example", 9030,
                "", "reader", ""));

        assertTrue(!hidden.metadataVisible());
        assertTrue(!edited.metadataVisible());
    }
}
