package com.offerlab.community.post.api;

/**
 * Safe source-revision projection for Analytics. Do not add task, user,
 * delivery, note, or attempt fields to this model.
 */
public record ContentMaintenanceTaskRevisionSummary(
        String status,
        String maintenancePhase,
        String terminalOutcomeCode
) {
}
