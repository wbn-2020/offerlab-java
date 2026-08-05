package com.offerlab.community.post.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Read-only boundary for determining whether an author's public posts already
 * have active content-maintenance work. Implementations must not reveal task
 * details, assignees, or task identifiers.
 */
public interface ContentMaintenanceTaskReadFacade {

    Set<Long> findActivePublicSourcePostIds(Long authorUid, Collection<Long> postIds);

    /**
     * Returns only the task status for exact post/revision source keys.
     * Implementations must not reveal task ids, users, titles, notes or timestamps.
     */
    Map<ContentMaintenanceTaskRevisionKey, String> findTaskStatusesBySourceRevision(
            String sourceType,
            Collection<ContentMaintenanceTaskRevisionKey> keys);

    /**
     * Returns the safe maintenance projection for exact post/revision source
     * keys. Implementations must not expose task identifiers, users, notes,
     * delivery data, or attempt details.
     */
    default Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary>
    findTaskRevisionSummariesBySourceRevision(
            String sourceType,
            Collection<ContentMaintenanceTaskRevisionKey> keys) {
        Map<ContentMaintenanceTaskRevisionKey, String> statuses =
                findTaskStatusesBySourceRevision(sourceType, keys);
        Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> summaries =
                new java.util.LinkedHashMap<>();
        for (Map.Entry<ContentMaintenanceTaskRevisionKey, String> entry : statuses.entrySet()) {
            String status = entry.getValue();
            String phase = switch (status) {
                case "OPEN" -> "OPEN";
                case "CLAIMED" -> "IN_PROGRESS";
                case "SUBMITTED" -> "REVIEW_PENDING";
                case "COMPLETED" -> "VERIFIED_DELIVERY";
                case "CLOSED" -> "CLOSED";
                default -> throw new IllegalStateException("maintenance task status is invalid");
            };
            summaries.put(entry.getKey(), new ContentMaintenanceTaskRevisionSummary(
                    status,
                    phase,
                    "COMPLETED".equals(status) ? "VERIFIED_DELIVERY" : null));
        }
        return Map.copyOf(summaries);
    }
}
