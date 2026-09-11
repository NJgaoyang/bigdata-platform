package com.company.platform.development;

import java.time.LocalDateTime;

/**
 * Links a project-space file to the personal-development file that produced it.
 * Version numbers are logical version numbers and are preserved when a version
 * moves from personal development to project space and then online.
 */
public record DevFileDeliveryView(
        long projectFileId,
        long sourceFileId,
        int pushedVersionNo,
        Integer onlineVersionNo,
        LocalDateTime updatedAt) { }
