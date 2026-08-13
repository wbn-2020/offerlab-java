package com.offerlab.community.post.api;

/**
 * Source key for a version-scoped content-maintenance task.
 * It intentionally contains no task or user identifiers.
 */
public record ContentMaintenanceTaskRevisionKey(
        Long sourcePostId,
        Long sourceRefId
) {
    public ContentMaintenanceTaskRevisionKey {
        if (sourcePostId == null || sourcePostId <= 0) {
            throw new IllegalArgumentException("sourcePostId is required");
        }
        if (sourceRefId == null || sourceRefId <= 0) {
            throw new IllegalArgumentException("sourceRefId is required");
        }
    }
}
