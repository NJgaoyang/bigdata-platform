package com.company.platform.development;

import java.time.LocalDateTime;

public record DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                          String content, String description, String status, int currentVersion,
                          LocalDateTime updatedAt, String lifecycleStatus, boolean everOnline,
                          String ownerName) {
    public DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                       String content, String description, String status, int currentVersion,
                       LocalDateTime updatedAt, String lifecycleStatus, boolean everOnline) {
        this(id, projectId, folderId, name, fileType, content, description, status, currentVersion,
                updatedAt, lifecycleStatus, everOnline, "admin");
    }

    public DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                       String content, String description, String status, int currentVersion,
                       LocalDateTime updatedAt) {
        this(id, projectId, folderId, name, fileType, content, description, status, currentVersion,
                updatedAt, "OFFLINE", false, "admin");
    }

    public DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                       String content, String description, String status, int currentVersion) {
        this(id, projectId, folderId, name, fileType, content, description, status, currentVersion,
                null, "OFFLINE", false, "admin");
    }
}
