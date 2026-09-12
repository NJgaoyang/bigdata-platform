package com.company.platform.development;

import jakarta.validation.constraints.NotBlank;

public final class DevelopmentAccessRequests {
    private DevelopmentAccessRequests() { }

    public record SaveToProjectRequest(
            long projectId,
            Long folderId,
            @NotBlank String name,
            @NotBlank String fileType,
            String content,
            String description) { }
}
