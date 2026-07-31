package com.offerlab.community.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.RevisitReadFacade;
import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;
import com.offerlab.community.notification.api.dto.UpdateDigestItemDTO;
import com.offerlab.community.notification.infrastructure.persistence.mapper.SubscriptionUpdateDigestMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.SubscriptionUpdateDigestPO;
import com.offerlab.community.post.api.PublicUpdateResourceFacade;
import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateDigestQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SCAN_SIZE = 200;
    private static final int SCAN_MULTIPLIER = 6;
    private static final int MAX_AGGREGATE_COUNT = 20;
    private static final long AGGREGATION_WINDOW_HOURS = 24L;
    private static final Set<String> SUBSCRIPTION_SOURCE_TYPES = Set.of("TOPIC", "DISCUSSION", "NEED");
    private static final Set<String> RESOURCE_TYPES =
            Set.of("POST", "TOPIC", "NEED", "COLLECTION", "SERIES");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SubscriptionUpdateDigestMapper digestMapper;
    private final SubscriptionUpdateDigestService digestService;
    private final ObjectMapper objectMapper;
    private final PublicUpdateResourceFacade publicUpdateResourceFacade;
    private final RevisitReadFacade revisitReadFacade;

    /**
     * Legacy sourceType/sourceId parameters remain resource filters. New callers should use the
     * overload that also accepts subscriptionSourceType/subscriptionSourceId.
     */
    public PageResult<UpdateDigestItemDTO> list(Long uid,
                                                String requestedSourceType,
                                                boolean unreadOnly,
                                                String cursor,
                                                int size) {
        return list(uid, requestedSourceType, null, null, null, unreadOnly, cursor, size);
    }

    /**
     * Legacy sourceType/sourceId parameters remain resource filters for API compatibility.
     */
    public PageResult<UpdateDigestItemDTO> list(Long uid,
                                                String requestedSourceType,
                                                String requestedSourceId,
                                                boolean unreadOnly,
                                                String cursor,
                                                int size) {
        return list(uid, requestedSourceType, requestedSourceId, null, null, unreadOnly, cursor, size);
    }

    public PageResult<UpdateDigestItemDTO> list(Long uid,
                                                String requestedResourceType,
                                                String requestedResourceId,
                                                String requestedSubscriptionSourceType,
                                                String requestedSubscriptionSourceId,
                                                boolean unreadOnly,
                                                String cursor,
                                                int size) {
        requireUid(uid);
        requireUnreadFilterIsSupported(unreadOnly);
        String resourceType = normalizeResourceType(requestedResourceType);
        Long resourceId = normalizePositiveId(requestedResourceId);
        String subscriptionSourceType = normalizeSubscriptionSourceType(requestedSubscriptionSourceType);
        Long subscriptionSourceId = normalizePositiveId(requestedSubscriptionSourceId);
        DigestCursor parsedCursor = parseCursor(cursor);
        int pageSize = Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE));
        int scanSize = Math.min(MAX_SCAN_SIZE, Math.max(pageSize + 1, pageSize * SCAN_MULTIPLIER));
        if (!digestService.isReady()) {
            return unavailablePage(unreadOnly, resourceType, resourceId, subscriptionSourceType, subscriptionSourceId);
        }

        List<SubscriptionUpdateDigestPO> rows;
        try {
            rows = digestMapper.listByReceiver(
                    uid, resourceType, resourceId, subscriptionSourceType, subscriptionSourceId,
                    parsedCursor.time(), parsedCursor.id(), scanSize + 1);
        } catch (RuntimeException e) {
            log.warn("subscription update digest query failed, uid={}", uid, e);
            return unavailablePage(unreadOnly, resourceType, resourceId, subscriptionSourceType, subscriptionSourceId);
        }
        if (rows == null || rows.isEmpty()) {
            return emptyPage(unreadOnly, resourceType, resourceId, subscriptionSourceType, subscriptionSourceId)
                    .withDiagnostic("digestSourceUnavailable", false);
        }

        boolean sourceHasMore = rows.size() > scanSize;
        List<SubscriptionUpdateDigestPO> scanRows = sourceHasMore ? rows.subList(0, scanSize) : rows;
        LinkedHashMap<String, DigestGroup> groups = new LinkedHashMap<>();
        SubscriptionUpdateDigestPO lastProcessed = null;
        boolean stoppedForNextGroup = false;
        int filteredCount = 0;

        for (SubscriptionUpdateDigestPO row : scanRows) {
            if (row == null || !Objects.equals(uid, row.getReceiverUid())) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            DigestCandidate candidate = candidate(row);
            if (candidate == null
                    || !resourceFilterMatches(resourceType, resourceId, candidate)
                    || !subscriptionSourceFilterMatches(
                    subscriptionSourceType, subscriptionSourceId, candidate)) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            PublicUpdateResourceDTO resource = publicUpdateResourceFacade.resolvePublic(
                    candidate.resourceType(),
                    String.valueOf(candidate.resourceId()),
                    candidate.fallbackPostId(),
                    candidate.requestedPath());
            if (!resourceMatches(candidate, resource)) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            candidate = candidate.withResource(resource);
            DigestGroup group = findCompatibleGroup(groups.values(), candidate);
            if (group == null && groups.size() >= pageSize) {
                stoppedForNextGroup = true;
                break;
            }
            if (group == null) {
                group = new DigestGroup(nextDigestKey(groups, candidate), candidate);
                groups.put(group.digestKey(), group);
            } else {
                group.add(candidate);
            }
            lastProcessed = row;
        }

        Map<String, RevisitReadStateDTO> revisitByResource = visibleRevisits(uid, groups.values());
        List<UpdateDigestItemDTO> items = groups.values().stream()
                .map(group -> group.toDto(revisitByResource.get(group.revisitResourceKey())))
                .toList();
        boolean hasMore = stoppedForNextGroup || sourceHasMore;
        String nextCursor = hasMore && lastProcessed != null && lastProcessed.getOccurredAt() != null
                ? encodeCursor(lastProcessed)
                : null;
        return PageResult.of(items, nextCursor, hasMore && nextCursor != null)
                .withDiagnostic("projectionType", "UPDATE_DIGEST")
                .withDiagnostic("digestReadModel", "t_subscription_update_digest")
                .withDiagnostic("notificationReadMutation", false)
                .withDiagnostic("revisitMutation", false)
                .withDiagnostic("aggregationWindowHours", AGGREGATION_WINDOW_HOURS)
                .withDiagnostic("maxAggregateCount", MAX_AGGREGATE_COUNT)
                .withDiagnostic("scanLimit", scanSize)
                .withDiagnostic("filteredCount", filteredCount)
                .withDiagnostic("requestedResourceType", resourceType)
                .withDiagnostic("requestedResourceId", resourceId)
                .withDiagnostic("requestedSubscriptionSourceType", subscriptionSourceType)
                .withDiagnostic("requestedSubscriptionSourceId", subscriptionSourceId);
    }

    private PageResult<UpdateDigestItemDTO> unavailablePage(boolean unreadOnly,
                                                            String resourceType,
                                                            Long resourceId,
                                                            String subscriptionSourceType,
                                                            Long subscriptionSourceId) {
        return emptyPage(unreadOnly, resourceType, resourceId, subscriptionSourceType, subscriptionSourceId)
                .withDiagnostic("digestSourceUnavailable", true);
    }

    private PageResult<UpdateDigestItemDTO> emptyPage(boolean unreadOnly,
                                                       String resourceType,
                                                       Long resourceId,
                                                       String subscriptionSourceType,
                                                       Long subscriptionSourceId) {
        return PageResult.<UpdateDigestItemDTO>empty()
                .withDiagnostic("projectionType", "UPDATE_DIGEST")
                .withDiagnostic("digestReadModel", "t_subscription_update_digest")
                .withDiagnostic("notificationReadMutation", false)
                .withDiagnostic("revisitMutation", false)
                .withDiagnostic("requestedResourceType", resourceType)
                .withDiagnostic("requestedResourceId", resourceId)
                .withDiagnostic("requestedSubscriptionSourceType", subscriptionSourceType)
                .withDiagnostic("requestedSubscriptionSourceId", subscriptionSourceId);
    }

    private DigestCandidate candidate(SubscriptionUpdateDigestPO row) {
        if (row.getId() == null
                || row.getOccurredAt() == null
                || row.getSourceId() == null
                || row.getSourceId() <= 0
                || row.getResourceId() == null
                || row.getResourceId() <= 0
                || !StringUtils.hasText(row.getEventKey())) {
            return null;
        }
        String subscriptionSourceType = storedEnum(row.getSourceType(), SUBSCRIPTION_SOURCE_TYPES);
        String resourceType = storedEnum(row.getResourceType(), RESOURCE_TYPES);
        String eventType = storedEventType(row.getEventType());
        if (subscriptionSourceType == null || resourceType == null || eventType == null) {
            return null;
        }
        Map<String, Object> payload = parsePayload(row.getPayloadJson());
        Long fallbackPostId = "POST".equals(resourceType)
                ? row.getResourceId()
                : positiveLong(payload.get("postId"));
        return new DigestCandidate(
                row.getId(),
                subscriptionSourceType,
                row.getSourceId(),
                resourceType,
                row.getResourceId(),
                eventType,
                bounded(row.getEventKey().trim(), 160),
                summary(payload, eventType),
                fallbackPostId,
                safeRequestedPath(payload.get("targetPath")),
                row.getOccurredAt(),
                null
        );
    }

    private DigestGroup findCompatibleGroup(Collection<DigestGroup> groups, DigestCandidate candidate) {
        for (DigestGroup group : groups) {
            if (group.canAdd(candidate)) {
                return group;
            }
        }
        return null;
    }

    private String nextDigestKey(Map<String, DigestGroup> groups, DigestCandidate candidate) {
        String base = candidate.subscriptionSourceType() + ":" + candidate.subscriptionSourceId()
                + ":" + candidate.resourceType() + ":" + candidate.resourceId()
                + ":" + candidate.eventType();
        if (!groups.containsKey(base)) {
            return base;
        }
        return base + ":" + candidate.digestId();
    }

    private Map<String, RevisitReadStateDTO> visibleRevisits(Long uid, Collection<DigestGroup> groups) {
        List<String> resourceKeys = groups.stream()
                .map(DigestGroup::revisitResourceKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (resourceKeys.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, RevisitReadStateDTO> states = revisitReadFacade.findVisibleStates(uid, resourceKeys);
            return states == null ? Map.of() : states;
        } catch (RuntimeException e) {
            log.warn("subscription update digest revisit projection failed, uid={}", uid, e);
            return Map.of();
        }
    }

    private boolean resourceMatches(DigestCandidate candidate, PublicUpdateResourceDTO resource) {
        if (candidate == null
                || resource == null
                || !safePath(resource.getCanonicalPath())
                || !StringUtils.hasText(resource.getSourceType())
                || !StringUtils.hasText(resource.getSourceId())) {
            return false;
        }
        String actualType = resource.getSourceType().trim().toUpperCase(Locale.ROOT);
        if (!candidate.resourceType().equals(actualType)) {
            return false;
        }
        if ("TOPIC".equals(candidate.resourceType())) {
            return true;
        }
        if (!String.valueOf(candidate.resourceId()).equals(resource.getSourceId())) {
            return false;
        }
        return !"POST".equals(candidate.resourceType())
                || Objects.equals(candidate.resourceId(), resource.getPostId());
    }

    private static boolean resourceFilterMatches(String resourceType,
                                                 Long resourceId,
                                                 DigestCandidate candidate) {
        return candidate != null
                && (resourceType == null || resourceType.equals(candidate.resourceType()))
                && (resourceId == null || resourceId.equals(candidate.resourceId()));
    }

    private static boolean subscriptionSourceFilterMatches(String sourceType,
                                                           Long sourceId,
                                                           DigestCandidate candidate) {
        return candidate != null
                && (sourceType == null || sourceType.equals(candidate.subscriptionSourceType()))
                && (sourceId == null || sourceId.equals(candidate.subscriptionSourceId()));
    }

    private Map<String, Object> parsePayload(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String summary(Map<String, Object> payload, String eventType) {
        String explicit = text(payload.get("summary"), 300);
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        return switch (eventType) {
            case "TOPIC_POST_PUBLISHED" -> "你关注的话题有新的公开内容。";
            case "DISCUSSION_COMMENT_CREATED" -> "你关注的讨论有新的公开回应。";
            case "NEED_COMPLETED", "NEED_STATE_CHANGED" -> "你关注的共建需求有公开进展。";
            default -> "相关公开内容有新的可见更新。";
        };
    }

    private static String normalizeResourceType(String value) {
        return normalizeFilterType(value, RESOURCE_TYPES);
    }

    private static String normalizeSubscriptionSourceType(String value) {
        return normalizeFilterType(value, SUBSCRIPTION_SOURCE_TYPES);
    }

    private static String normalizeFilterType(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static Long normalizePositiveId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static DigestCursor parseCursor(String value) {
        if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
            return new DigestCursor(null, null);
        }
        try {
            String[] parts = value.trim().split(":", 2);
            long millis = Long.parseLong(parts[0]);
            long id = parts.length > 1 ? Long.parseLong(parts[1]) : 0L;
            if (millis <= 0 || id <= 0) {
                throw new NumberFormatException();
            }
            return new DigestCursor(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC),
                    id
            );
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String storedEnum(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : null;
    }

    private static String storedEventType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() <= 64 && normalized.matches("[A-Z][A-Z0-9_]*") ? normalized : null;
    }

    private static String text(Object value, int maxLength) {
        if (!(value instanceof String raw) || !StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().replaceAll("\\s+", " ");
        return bounded(normalized, maxLength);
    }

    private static String bounded(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Long positiveLong(Object value) {
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String safeRequestedPath(Object value) {
        String path = text(value, 300);
        return safePath(path) ? path : null;
    }

    private static boolean safePath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return path.startsWith("/")
                && !path.startsWith("//")
                && !path.startsWith("/api/")
                && !path.contains("\\")
                && !path.matches(".*\\s+.*")
                && !lower.contains("://")
                && !lower.startsWith("/javascript:")
                && !lower.startsWith("/data:")
                && path.length() <= 300;
    }

    private static String encodeCursor(SubscriptionUpdateDigestPO row) {
        return row.getOccurredAt().toInstant(ZoneOffset.UTC).toEpochMilli() + ":" + row.getId();
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requireUnreadFilterIsSupported(boolean unreadOnly) {
        if (unreadOnly) {
            throw new BizException(
                    ErrorCode.PARAM_ERROR.getCode(),
                    "更新摘要不支持 unreadOnly 筛选；摘要没有独立已读状态");
        }
    }

    private record DigestCursor(LocalDateTime time, Long id) {
    }

    private record DigestCandidate(Long digestId,
                                   String subscriptionSourceType,
                                   Long subscriptionSourceId,
                                   String resourceType,
                                   Long resourceId,
                                   String eventType,
                                   String eventKey,
                                   String summary,
                                   Long fallbackPostId,
                                   String requestedPath,
                                   LocalDateTime occurredAt,
                                   PublicUpdateResourceDTO resource) {

        private DigestCandidate withResource(PublicUpdateResourceDTO value) {
            return new DigestCandidate(
                    digestId,
                    subscriptionSourceType,
                    subscriptionSourceId,
                    resourceType,
                    resourceId,
                    eventType,
                    eventKey,
                    summary,
                    fallbackPostId,
                    requestedPath,
                    occurredAt,
                    value
            );
        }
    }

    private static final class DigestGroup {
        private final String digestKey;
        private final String subscriptionSourceType;
        private final Long subscriptionSourceId;
        private final String resourceType;
        private final Long resourceId;
        private final String eventType;
        private final String title;
        private final String targetPath;
        private final String revisitResourceKey;
        private final List<Long> digestIds = new ArrayList<>();
        private String eventKey;
        private String summary;
        private LocalDateTime latestAt;

        private DigestGroup(String digestKey, DigestCandidate candidate) {
            this.digestKey = digestKey;
            this.subscriptionSourceType = candidate.subscriptionSourceType();
            this.subscriptionSourceId = candidate.subscriptionSourceId();
            this.resourceType = candidate.resourceType();
            this.resourceId = candidate.resourceId();
            this.eventType = candidate.eventType();
            this.title = candidate.resource().getTitle();
            this.targetPath = candidate.resource().getCanonicalPath();
            this.revisitResourceKey = candidate.resource().getSourceType()
                    + ":" + candidate.resource().getSourceId();
            add(candidate);
        }

        private boolean canAdd(DigestCandidate candidate) {
            if (!subscriptionSourceType.equals(candidate.subscriptionSourceType())
                    || !subscriptionSourceId.equals(candidate.subscriptionSourceId())
                    || !resourceType.equals(candidate.resourceType())
                    || !resourceId.equals(candidate.resourceId())
                    || !eventType.equals(candidate.eventType())
                    || digestIds.size() >= MAX_AGGREGATE_COUNT) {
                return false;
            }
            return Duration.between(candidate.occurredAt(), latestAt).abs()
                    .compareTo(Duration.ofHours(AGGREGATION_WINDOW_HOURS)) <= 0;
        }

        private void add(DigestCandidate candidate) {
            digestIds.add(candidate.digestId());
            if (latestAt == null || candidate.occurredAt().isAfter(latestAt)) {
                latestAt = candidate.occurredAt();
                eventKey = candidate.eventKey();
                summary = candidate.summary();
            }
        }

        private String digestKey() {
            return digestKey;
        }

        private String revisitResourceKey() {
            return revisitResourceKey;
        }

        private UpdateDigestItemDTO toDto(RevisitReadStateDTO revisit) {
            return UpdateDigestItemDTO.builder()
                    .projectionType("UPDATE_DIGEST")
                    .digestKey(digestKey)
                    .dedupKey(eventKey)
                    .eventId(eventKey)
                    .eventType(eventType)
                    .sourceType(resourceType)
                    .sourceId(String.valueOf(resourceId))
                    .subscriptionSourceType(subscriptionSourceType)
                    .subscriptionSourceId(String.valueOf(subscriptionSourceId))
                    .resourceType(resourceType)
                    .resourceId(String.valueOf(resourceId))
                    .title(title)
                    .summary(summary)
                    .targetPath(targetPath)
                    .occurredAt(latestAt)
                    .occurrenceCount(digestIds.size())
                    .digestIds(List.copyOf(digestIds))
                    .notificationIds(List.of())
                    .notificationUnread(false)
                    .revisit(revisit == null ? null : UpdateDigestItemDTO.RevisitStateDTO.builder()
                            .itemId(revisit.getItemId())
                            .status(revisit.getStatus())
                            .targetPath(revisit.getTargetPath())
                            .dueAt(revisit.getDueAt())
                            .updateTime(revisit.getUpdateTime())
                            .build())
                    .build();
        }
    }
}
