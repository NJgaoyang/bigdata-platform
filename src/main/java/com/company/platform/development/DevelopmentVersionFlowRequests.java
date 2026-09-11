package com.company.platform.development;

import jakarta.validation.constraints.NotBlank;

public final class DevelopmentVersionFlowRequests {
    private DevelopmentVersionFlowRequests() { }

    public record PushToProjectRequest(
            long sourceFileId,
            long projectId,
            Long folderId,
            @NotBlank String name,
            String description) { }
}
