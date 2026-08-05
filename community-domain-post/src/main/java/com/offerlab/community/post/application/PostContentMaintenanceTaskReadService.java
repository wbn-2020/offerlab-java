package com.offerlab.community.post.application;

import com.offerlab.community.post.api.ContentMaintenanceTaskReadFacade;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionSummary;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostContentMaintenanceTaskReadService implements ContentMaintenanceTaskReadFacade {

    private static final int MAX_POST_IDS = 250;
    private static final int MAX_REVISION_KEYS = 100;
    private static final Set<String> TASK_STATUSES = Set.of(
            "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");
    private static final Set<String> ATTEMPT_DECISIONS = Set.of(
            "APPROVED", "REJECTED", "CLOSED");

    private final ContentMaintenanceTaskMapper maintenanceTaskMapper;

    @Override
    public Set<Long> findActivePublicSourcePostIds(Long authorUid, Collection<Long> postIds) {
        if (authorUid == null || authorUid <= 0 || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<Long> requestedPostIds = new LinkedHashSet<>();
        for (Long postId : postIds) {
            if (postId != null && postId > 0) {
                requestedPostIds.add(postId);
            }
            if (requestedPostIds.size() >= MAX_POST_IDS) {
                break;
            }
        }
        if (requestedPostIds.isEmpty()) {
            return Set.of();
        }
        List<Long> matchedPostIds = maintenanceTaskMapper.listActivePublicSourcePostIdsForAuthor(
                authorUid, requestedPostIds);
        if (matchedPostIds == null || matchedPostIds.isEmpty()) {
            return Set.of();
        }
        return matchedPostIds.stream()
                .filter(requestedPostIds::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public Map<ContentMaintenanceTaskRevisionKey, String> findTaskStatusesBySourceRevision(
            String sourceType,
            Collection<ContentMaintenanceTaskRevisionKey> keys) {
        Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRow> rows =
                taskRowsBySourceRevision(sourceType, keys);
        Map<ContentMaintenanceTaskRevisionKey, String> matched = new LinkedHashMap<>();
        for (Map.Entry<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRow> entry : rows.entrySet()) {
            matched.put(entry.getKey(), entry.getValue().getStatus());
        }
        return Map.copyOf(matched);
    }

    @Override
    public Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary>
    findTaskRevisionSummariesBySourceRevision(
            String sourceType,
            Collection<ContentMaintenanceTaskRevisionKey> keys) {
        Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRow> rows =
                taskRowsBySourceRevision(sourceType, keys);
        Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRevisionSummary> summaries =
                new LinkedHashMap<>();
        for (Map.Entry<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRow> entry : rows.entrySet()) {
            ContentMaintenanceTaskRow row = entry.getValue();
            summaries.put(entry.getKey(), new ContentMaintenanceTaskRevisionSummary(
                    row.getStatus(),
                    maintenancePhase(row),
                    "COMPLETED".equals(row.getStatus()) ? "VERIFIED_DELIVERY" : null));
        }
        return Map.copyOf(summaries);
    }

    private Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRow> taskRowsBySourceRevision(
            String sourceType,
            Collection<ContentMaintenanceTaskRevisionKey> keys) {
        if (!"CHANNEL_HEALTH".equals(sourceType)) {
            throw new IllegalArgumentException("unsupported maintenance source type");
        }
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<ContentMaintenanceTaskRevisionKey> requestedKeys = new LinkedHashSet<>();
        for (ContentMaintenanceTaskRevisionKey key : keys) {
            if (key != null) {
                requestedKeys.add(key);
            }
            if (requestedKeys.size() >= MAX_REVISION_KEYS) {
                break;
            }
        }
        if (requestedKeys.isEmpty()) {
            return Map.of();
        }
        List<ContentMaintenanceTaskRow> rows =
                maintenanceTaskMapper.listTaskStatusesBySourceRevision(sourceType, requestedKeys);
        if (rows == null) {
            throw new IllegalStateException("maintenance task source status is unavailable");
        }
        Map<ContentMaintenanceTaskRevisionKey, ContentMaintenanceTaskRow> matched = new LinkedHashMap<>();
        for (ContentMaintenanceTaskRow row : rows) {
            if (row == null || row.getSourcePostId() == null || row.getSourceRefId() == null
                    || !TASK_STATUSES.contains(row.getStatus())
                    || (row.getLatestAttemptDecision() != null
                    && !ATTEMPT_DECISIONS.contains(row.getLatestAttemptDecision()))
                    || (row.getTerminalOutcomeCode() != null
                    && (!"COMPLETED".equals(row.getStatus())
                    || !"VERIFIED_DELIVERY".equals(row.getTerminalOutcomeCode())))) {
                throw new IllegalStateException("maintenance task source status is invalid");
            }
            ContentMaintenanceTaskRevisionKey key;
            try {
                key = new ContentMaintenanceTaskRevisionKey(row.getSourcePostId(), row.getSourceRefId());
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("maintenance task source key is invalid", ex);
            }
            if (requestedKeys.contains(key)) {
                if (matched.put(key, row) != null) {
                    throw new IllegalStateException("maintenance task source status is duplicated");
                }
            }
        }
        return Map.copyOf(matched);
    }

    private static String maintenancePhase(ContentMaintenanceTaskRow row) {
        return switch (row.getStatus()) {
            case "OPEN" -> "OPEN";
            case "CLAIMED" -> "REJECTED".equals(row.getLatestAttemptDecision())
                    ? "REWORK" : "IN_PROGRESS";
            case "SUBMITTED" -> "REVIEW_PENDING";
            case "COMPLETED" -> "VERIFIED_DELIVERY";
            case "CLOSED" -> "CLOSED";
            default -> throw new IllegalStateException("maintenance task status is invalid");
        };
    }

}
