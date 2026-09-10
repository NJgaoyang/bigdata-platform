package com.company.platform.development;

import jakarta.validation.constraints.NotBlank;

public final class DevelopmentRequests {
    private DevelopmentRequests() { }
    public record ProjectRequest(@NotBlank String name, String description) { }
    public record FolderRequest(long projectId, Long parentId, @NotBlank String name) { }
    public record FolderUpdateRequest(@NotBlank String name, Long parentId, Boolean moveToRoot) { }
    public record FileRequest(long projectId, Long folderId, @NotBlank String name,
                              @NotBlank String fileType, String content, String description) { }
    public record SaveFileRequest(@NotBlank String content, String name, String description, Long folderId, Boolean moveToRoot) {
        public SaveFileRequest(String content) { this(content, null, null, null, null); }
    }
    public record VersionRequest(@NotBlank String content) { }
}
