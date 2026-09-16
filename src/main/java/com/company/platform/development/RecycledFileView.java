package com.company.platform.development;

import java.time.LocalDateTime;

public record RecycledFileView(long id, long projectId, Long folderId, String folderName, String name,
                               String fileType, String description, String status, int currentVersion,
                               LocalDateTime recycledAt, String recycledBy) { }
