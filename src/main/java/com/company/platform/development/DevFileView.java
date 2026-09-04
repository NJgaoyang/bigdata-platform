package com.company.platform.development;

public record DevFileView(long id, long projectId, Long folderId, String name, String fileType,
                          String content, String status, int currentVersion) { }
