package com.company.platform.development;

import java.time.LocalDateTime;

public record DevFolderView(long id, long projectId, Long parentId, String name, LocalDateTime createdAt) { }
