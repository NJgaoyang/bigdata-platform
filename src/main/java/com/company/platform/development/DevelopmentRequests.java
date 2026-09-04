package com.company.platform.development;

import jakarta.validation.constraints.NotBlank;

public final class DevelopmentRequests {
    private DevelopmentRequests() { }
    public record ProjectRequest(@NotBlank String name, String description) { }
    public record FolderRequest(long projectId, Long parentId, @NotBlank String name) { }
    public record FolderUpdateRequest(@NotBlank String name) { }
    public record FileRequest(long projectId, Long folderId, @NotBlank String name,
                              @NotBlank String fileType, String content) { }
    public record SaveFileRequest(@NotBlank String content, String name) {
        public SaveFileRequest(String content) { this(content, null); }
    }
    public record VersionRequest(@NotBlank String content) { }
}
