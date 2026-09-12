package com.company.platform.datasource;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.BadRequestException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DataSourceService {
    private final PlatformStore store;
    private final PasswordCipher cipher;
    private final DynamicDataSourceManager dataSourceManager;

    public DataSourceService(PlatformStore store, PasswordCipher cipher, DynamicDataSourceManager dataSourceManager) {
        this.store = store;
        this.cipher = cipher;
        this.dataSourceManager = dataSourceManager;
    }

    public List<DataSourceView> list() { return store.dataSources.values().stream().toList(); }

    @Transactional
    public DataSourceView create(CreateDataSourceRequest request) {
        ensureUniqueName(request.name(), null);
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestException("新建数据源必须填写密码");
        }
        long id = store.nextId();
        boolean metadataVisible = request.metadataVisible() == null || request.metadataVisible();
        DataSourceView view = new DataSourceView(id, request.name(), request.type(), request.host(),
                request.port() == 0 ? (request.type() == DataSourceType.MYSQL ? 3306 : 9030) : request.port(),
                normalizedDatabase(request.databaseName()), request.username(), "UNKNOWN", metadataVisible, null, null);
        String encrypted = cipher.encrypt(request.password());
        store.persistDataSource(view, encrypted);
        store.dataSources.put(id, view);
        store.encryptedDataSourcePasswords.put(id, encrypted);
        return view;
    }

    public DataSourceView get(long id) {
        DataSourceView result = store.dataSources.get(id);
        if (result == null) throw new NotFoundException("数据源不存在：" + id);
        return result;
    }

    @Transactional
    public DataSourceView update(long id, CreateDataSourceRequest request) {
        DataSourceView current = get(id);
        ensureUniqueName(request.name(), id);
        boolean metadataVisible = request.metadataVisible() == null ? current.metadataVisible() : request.metadataVisible();
        DataSourceView updated = new DataSourceView(id, request.name(), request.type(), request.host(),
                request.port() == 0 ? (request.type() == DataSourceType.MYSQL ? 3306 : 9030) : request.port(),
                normalizedDatabase(request.databaseName()), request.username(), "UNKNOWN", metadataVisible, null, null);
        // Passwords are deliberately not sent back to the browser.  An empty field
        // in the edit form therefore means "keep the current credential", not
        // "erase it".
        String encrypted = request.password() == null || request.password().isBlank()
                ? store.encryptedDataSourcePasswords.getOrDefault(id, "")
                : cipher.encrypt(request.password());
        store.persistDataSource(updated, encrypted);
        store.dataSources.put(id, updated);
        store.encryptedDataSourcePasswords.put(id, encrypted);
        dataSourceManager.close(id);
        return updated;
    }

    @Transactional
    public DataSourceView setMetadataVisible(long id, boolean visible) {
        DataSourceView current = get(id);
        DataSourceView updated = new DataSourceView(current.id(), current.name(), current.type(), current.host(),
                current.port(), current.databaseName(), current.username(), current.status(), visible,
                current.lastCheckedAt(), current.lastCheckMessage());
        store.persistDataSource(updated, store.encryptedDataSourcePasswords.getOrDefault(id, ""));
        store.dataSources.put(id, updated);
        return updated;
    }

    @Transactional
    public void delete(long id) {
        get(id);
        store.deleteCore("data_source", id);
        store.dataSources.remove(id);
        store.encryptedDataSourcePasswords.remove(id);
        dataSourceManager.close(id);
    }

    @Transactional
    public ConnectionTestResult test(long id) {
        ConnectionInfo info = connectionInfo(id);
        try (var ignored = dataSourceManager.testConnection(info.jdbcUrl(), info.username(), info.password())) {
            String message = info.type() + " 连接成功";
            updateHealthStatus(id, "ACTIVE", message);
            return new ConnectionTestResult(true, message);
        } catch (Exception ex) {
            // Do not persist raw JDBC messages: driver errors may contain internal
            // addresses, database names or other infrastructure details.
            String message = info.type() + " 连接失败，请检查地址、端口、账号和网络权限";
            updateHealthStatus(id, "UNAVAILABLE", message);
            return new ConnectionTestResult(false, message);
        }
    }

    private void updateHealthStatus(long id, String status, String message) {
        DataSourceView current = get(id);
        DataSourceView updated = new DataSourceView(current.id(), current.name(), current.type(), current.host(), current.port(),
                current.databaseName(), current.username(), status, current.metadataVisible(), LocalDateTime.now(), message);
        store.persistDataSource(updated, store.encryptedDataSourcePasswords.getOrDefault(id, ""));
        store.dataSources.put(id, updated);
    }

    public ConnectionInfo connectionInfo(long id) {
        DataSourceView source = get(id);
        String database = normalizedDatabase(source.databaseName());
        String jdbcUrl = switch (source.type()) {
            case MYSQL -> "jdbc:mysql://" + source.host() + ":" + source.port() + "/" + database
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
            case STARROCKS -> "jdbc:mysql://" + source.host() + ":" + source.port() + "/" + database
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
        };
        String encrypted = store.encryptedDataSourcePasswords.getOrDefault(id, "");
        String password = encrypted.isBlank() ? "" : cipher.decrypt(encrypted);
        return new ConnectionInfo(id, source.type(), jdbcUrl, source.username(), password, database);
    }

    private void ensureUniqueName(String name, Long ignoredId) {
        boolean exists = store.dataSources.values().stream().anyMatch(source ->
                (ignoredId == null || source.id() != ignoredId)
                        && source.name().equalsIgnoreCase(name.trim()));
        if (exists) throw new BadRequestException("数据源名称已存在，请使用其他名称或编辑已有数据源");
    }

    private String normalizedDatabase(String databaseName) {
        return databaseName == null ? "" : databaseName.trim();
    }

    public record ConnectionTestResult(boolean success, String message) { }
    public record ConnectionInfo(long id, DataSourceType type, String jdbcUrl, String username, String password,
                                 String databaseName) { }
}
