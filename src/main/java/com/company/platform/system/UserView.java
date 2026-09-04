package com.company.platform.system;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public record UserView(long id, String username, String displayName, String roleCode, String status,
                       LocalDateTime createdAt, String passwordHash) {
    public UserView(long id, String username, String displayName, String status) {
        this(id, username, displayName, defaultRole(username), status, LocalDateTime.now(), null);
    }

    public UserView(long id, String username, String displayName, String status, String passwordHash) {
        this(id, username, displayName, defaultRole(username), status, LocalDateTime.now(), passwordHash);
    }

    @Override
    @JsonIgnore
    public String passwordHash() { return passwordHash; }

    private static String defaultRole(String username) {
        return "admin".equalsIgnoreCase(username) ? "ADMIN" : "USER";
    }
}
