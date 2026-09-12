package com.company.platform.development;

public final class DevelopmentVersionFlowRequests {
    private DevelopmentVersionFlowRequests() { }

    /**
     * projectId/folderId/name are required only for the first push. Once a source
     * file is bound to a project file, subsequent pushes resolve the immutable
     * target from dev_file_delivery and these fields may be omitted.
     */
    public record PushToProjectRequest(
            long sourceFileId,
            Long projectId,
            Long folderId,
            String name,
            String description) { }
}
