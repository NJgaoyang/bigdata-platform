package com.company.platform.datasource;

import com.company.platform.common.ForbiddenException;
import com.company.platform.common.NotFoundException;
import com.company.platform.common.PlatformStore;
import com.company.platform.config.PlatformProperties;
import com.company.platform.system.UserView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Resource-level authorization for data sources. Module permission is checked by the HTTP interceptor first. */
@Service
public class DataSourceAccessService {
    public enum Access { VIEW, QUERY, EDIT }

    private final PlatformStore store;
    private final PlatformProperties properties;

    public DataSourceAccessService(PlatformStore store, PlatformProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public List<DataSourceView> filterVisible(List<DataSourceView> sources, String operator) {
        return sources.stream().filter(source -> canAccess(source.id(), operator, Access.VIEW)).toList();
    }

    public void requireView(long dataSourceId, String operator) { require(dataSourceId, operator, Access.VIEW); }
    public void requireQuery(long dataSourceId, String operator) { require(dataSourceId, operator, Access.QUERY); }
    public void requireEdit(long dataSourceId, String operator) { require(dataSourceId, operator, Access.EDIT); }

    public boolean canAccess(long dataSourceId, String operator, Access access) {
        if (!store.dataSources.containsKey(dataSourceId)) return false;
        String username = operator == null ? "" : operator.trim();
        if (isAdministrator(username)) return true;
        UserView user = store.users.values().stream().filter(item -> item.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.status())) return false;
        String prefix = dataSourceId + ":" + user.id() + ":";
        boolean view = store.datasourcePermissions.containsKey(prefix + "VIEW");
        boolean query = store.datasourcePermissions.containsKey(prefix + "QUERY");
        boolean edit = store.datasourcePermissions.containsKey(prefix + "EDIT");
        return switch (access) {
            case VIEW -> view || query || edit;
            case QUERY -> query || edit;
            case EDIT -> edit;
        };
    }

    private void require(long dataSourceId, String operator, Access access) {
        if (!store.dataSources.containsKey(dataSourceId)) throw new NotFoundException("数据源不存在：" + dataSourceId);
        if (!canAccess(dataSourceId, operator, access)) throw new ForbiddenException("当前用户没有该数据源的" + label(access) + "权限");
    }

    private boolean isAdministrator(String username) {
        if (username.isBlank()) return false;
        String configured = properties.getSecurity().getAdminUsername();
        if ("admin".equalsIgnoreCase(username) || configured != null && username.equalsIgnoreCase(configured.trim())) return true;
        return store.users.values().stream().anyMatch(user -> username.equalsIgnoreCase(user.username())
                && "ACTIVE".equalsIgnoreCase(user.status()) && "ADMIN".equalsIgnoreCase(user.roleCode()));
    }

    private String label(Access access) {
        return switch (access) {
            case VIEW -> "查看";
            case QUERY -> "查询";
            case EDIT -> "编辑";
        };
    }
}
