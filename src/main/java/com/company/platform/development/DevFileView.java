package com.company.platform.development;

import java.time.LocalDateTime;

public record DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                          String content, String description, String status, int currentVersion,
                          LocalDateTime updatedAt) {
    /**
     * Backwards-compatible constructor for callers that do not have timestamp
     * metadata (for example, in-memory drafts). Persisted files are populated
     * with the database updated_at value by PlatformStore.
     */
    public DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                       String content, String description, String status, int currentVersion) {
        this(id, projectId, folderId, name, fileType, content, description, status, currentVersion, null);
    }
}
