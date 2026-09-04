package com.company.platform.datasource;

import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import org.springframework.stereotype.Service;

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

    public DataSourceView create(CreateDataSourceRequest request) {
        long id = store.nextId();
        DataSourceView view = new DataSourceView(id, request.name(), request.type(), request.host(),
                request.port() == 0 ? (request.type() == DataSourceType.MYSQL ? 3306 : 9030) : request.port(),
                request.databaseName(), request.username(), "ACTIVE");
        store.dataSources.put(id, view);
        String encrypted = cipher.encrypt(request.password());
        store.encryptedDataSourcePasswords.put(id, encrypted);
        store.persistDataSource(view, encrypted);
        return view;
    }

    public DataSourceView get(long id) {
        DataSourceView result = store.dataSources.get(id);
        if (result == null) throw new NotFoundException("数据源不存在：" + id);
        return result;
    }

    public DataSourceView update(long id, CreateDataSourceRequest request) {
        get(id);
        DataSourceView updated = new DataSourceView(id, request.name(), request.type(), request.host(),
                request.port() == 0 ? (request.type() == DataSourceType.MYSQL ? 3306 : 9030) : request.port(),
                request.databaseName(), request.username(), "ACTIVE");
        store.dataSources.put(id, updated);
        // Passwords are deliberately not sent back to the browser.  An empty field
        // in the edit form therefore means "keep the current credential", not
        // "erase it".
        String encrypted = request.password() == null || request.password().isBlank()
                ? store.encryptedDataSourcePasswords.getOrDefault(id, "")
                : cipher.encrypt(request.password());
        store.encryptedDataSourcePasswords.put(id, encrypted);
        store.persistDataSource(updated, encrypted);
        dataSourceManager.close(id);
        return updated;
    }

    public void delete(long id) {
        store.dataSources.remove(id);
        store.encryptedDataSourcePasswords.remove(id);
        store.deleteCore("data_source", id);
        dataSourceManager.close(id);
    }

    public ConnectionTestResult test(long id) {
        try (var ignored = dataSourceManager.testConnection(connectionInfo(id).jdbcUrl(), connectionInfo(id).username(), connectionInfo(id).password())) {
            return new ConnectionTestResult(true, connectionInfo(id).type() + " 连接成功");
        } catch (Exception ex) {
            return new ConnectionTestResult(false, connectionInfo(id).type() + " 连接失败：" + ex.getMessage());
        }
    }

    public ConnectionInfo connectionInfo(long id) {
        DataSourceView source = get(id);
        String database = source.databaseName();
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

    public record ConnectionTestResult(boolean success, String message) { }
    public record ConnectionInfo(long id, DataSourceType type, String jdbcUrl, String username, String password,
                                 String databaseName) { }
}
