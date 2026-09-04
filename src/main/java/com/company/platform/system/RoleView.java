package com.company.platform.system;

import java.util.Set;

public record RoleView(long id, String roleCode, String roleName, Set<String> permissions) { }
