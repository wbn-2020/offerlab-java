package com.offerlab.community.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.interaction.api.RevisitReadFacade;
import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;
import com.offerlab.community.notification.api.dto.UpdateDigestItemDTO;
import com.offerlab.community.notification.infrastructure.persistence.mapper.NotificationMessageMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import com.offerlab.community.post.api.PublicUpdateResourceFacade;
import com.offerlab.community.post.api.dto.PublicUpdateResourceDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceKeyDTO;
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
import java.util.LinkedHashSet;
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

    private static final int TYPE_LIKE = 1;
    private static final int TYPE_COMMENT = 2;
    private static final int TYPE_FAVORITE = 3;
    private static final int TYPE_FOLLOWER = 4;
    private static final int TYPE_SYSTEM = 5;
    private static final int TYPE_MENTION = 6;
    private static final int TARGET_POST = 1;

    private static final Set<String> UPDATE_ACTIONS = Set.of(
            "comment",
            "answeraccepted",
            "discussion_follow_comment",
            "discussion_follow_featured_reply",
            "discussion_follow_author_pinned",
            "discussion_follow_author_reply",
            "topic_post_published",
            "collaboration_need_state_changed",
            "answer_accepted",
            "freshness_changed",
            "content_updated",
            "maintenance_completed"
    );

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final UserFacade userFacade;
    private final UserSubscriptionPreferenceFacade subscriptionPreferenceFacade;
    private final PublicUpdateResourceFacade publicUpdateResourceFacade;
    private final RevisitReadFacade revisitReadFacade;

    public PageResult<UpdateDigestItemDTO> list(Long uid,
                                                String requestedSourceType,
                                                boolean unreadOnly,
                                                String cursor,
                                                int size) {
        return list(uid, requestedSourceType, null, unreadOnly, cursor, size);
    }

    public PageResult<UpdateDigestItemDTO> list(Long uid,
                                                String requestedSourceType,
                                                String requestedSourceId,
                                                boolean unreadOnly,
                                                String cursor,
                                                int size) {
        requireUid(uid);
        String sourceType = normalizeSourceType(requestedSourceType);
        String sourceId = normalizeSourceId(requestedSourceId, sourceType);
        DigestCursor parsedCursor = parseCursor(cursor);
        int pageSize = Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE));
        int scanSize = Math.min(MAX_SCAN_SIZE, Math.max(pageSize + 1, pageSize * SCAN_MULTIPLIER));
        if (!messageTableReady()) {
            return PageResult.<UpdateDigestItemDTO>empty()
                    .withDiagnostic("projectionType", "UPDATE_DIGEST")
                    .withDiagnostic("notificationSourceUnavailable", true);
        }

        List<NotificationMessagePO> rows;
        try {
            rows = messageMapper.listUpdateDigestCandidates(
                    uid, sourceType, sourceId, unreadOnly,
                    parsedCursor.time(), parsedCursor.id(), scanSize + 1);
        } catch (RuntimeException e) {
            log.warn("update digest notification source query failed, uid={}", uid, e);
            return PageResult.<UpdateDigestItemDTO>empty()
                    .withDiagnostic("projectionType", "UPDATE_DIGEST")
                    .withDiagnostic("notificationSourceUnavailable", true);
        }
        if (rows == null || rows.isEmpty()) {
            return PageResult.<UpdateDigestItemDTO>empty()
                    .withDiagnostic("projectionType", "UPDATE_DIGEST")
                    .withDiagnostic("revisitMutation", false);
        }

        boolean sourceHasMore = rows.size() > scanSize;
        List<NotificationMessagePO> scanRows = sourceHasMore ? rows.subList(0, scanSize) : rows;
        List<ParsedDigestRow> parsedRows = parseRows(uid, sourceType, sourceId, scanRows);
        SourcePreferenceBatch sourcePreferences = sourcePreferences(uid, parsedRows);
        Map<Integer, PreferenceDecision> notificationPreferences =
                notificationPreferences(uid, parsedRows);
        LinkedHashMap<String, DigestGroup> groups = new LinkedHashMap<>();
        Set<String> eventIds = new LinkedHashSet<>();
        NotificationMessagePO lastProcessed = null;
        boolean stoppedForNextGroup = false;
        int filteredCount = 0;
        boolean preferenceDegraded = sourcePreferences.degraded()
                || notificationPreferences.values().stream().anyMatch(PreferenceDecision::degraded);

        for (ParsedDigestRow parsedRow : parsedRows) {
            NotificationMessagePO row = parsedRow.row();
            if (row == null || !Objects.equals(uid, row.getReceiverUid())) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            DigestCandidate candidate = parsedRow.candidate();
            if (candidate == null || !sourceFilterMatches(sourceType, sourceId, candidate)) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            PreferenceDecision preference = notificationPreferences.get(
                    normalizedNotificationType(row.getNotifType()));
            if (preference == null || !preference.allowed()) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            if (!digestDeliveryEnabled(candidate, sourcePreferences.preferences())) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            PublicUpdateResourceDTO resource = publicUpdateResourceFacade.resolvePublic(
                    candidate.sourceType(),
                    candidate.sourceId(),
                    candidate.fallbackPostId(),
                    candidate.requestedPath());
            if (!resourceMatches(candidate, resource)) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            candidate = candidate.withResource(resource);
            if (!sourceFilterMatches(sourceType, sourceId, candidate)) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }
            if (!eventIds.add(candidate.eventId())) {
                filteredCount++;
                lastProcessed = row;
                continue;
            }

            String groupKey = groupKey(candidate, groups);
            DigestGroup group = groups.get(groupKey);
            if (group == null && groups.size() >= pageSize) {
                stoppedForNextGroup = true;
                break;
            }
            if (group == null) {
                group = new DigestGroup(groupKey, candidate);
                groups.put(groupKey, group);
            } else {
                group.add(candidate);
            }
            lastProcessed = row;
        }

        Map<String, RevisitReadStateDTO> revisitByResource = visibleRevisits(uid, groups.values());
        List<UpdateDigestItemDTO> items = groups.values().stream()
                .map(group -> group.toDto(revisitByResource.get(group.resourceKey())))
                .toList();

        boolean hasMore = stoppedForNextGroup || sourceHasMore;
        String nextCursor = hasMore && lastProcessed != null && lastProcessed.getCreateTime() != null
                ? encodeCursor(lastProcessed)
                : null;
        return PageResult.of(items, nextCursor, hasMore && nextCursor != null)
                .withDiagnostic("projectionType", "UPDATE_DIGEST")
                .withDiagnostic("notificationReadMutation", false)
                .withDiagnostic("revisitMutation", false)
                .withDiagnostic("aggregationWindowHours", AGGREGATION_WINDOW_HOURS)
                .withDiagnostic("maxAggregateCount", MAX_AGGREGATE_COUNT)
                .withDiagnostic("scanLimit", scanSize)
                .withDiagnostic("filteredCount", filteredCount)
                .withDiagnostic("preferenceCheckDegraded", preferenceDegraded)
                .withDiagnostic("sourcePreferenceMode", "DIGEST")
                .withDiagnostic("requestedSourceType", sourceType)
                .withDiagnostic("requestedSourceId", sourceId);
    }

    private List<ParsedDigestRow> parseRows(Long uid,
                                            String sourceType,
                                            String sourceId,
                                            List<NotificationMessagePO> rows) {
        List<ParsedDigestRow> parsed = new ArrayList<>(rows.size());
        for (NotificationMessagePO row : rows) {
            DigestCandidate candidate = row != null && Objects.equals(uid, row.getReceiverUid())
                    ? candidate(row)
                    : null;
            parsed.add(new ParsedDigestRow(
                    row,
                    candidate != null && sourceFilterMatches(sourceType, sourceId, candidate)
                            ? candidate
                            : null));
        }
        return parsed;
    }

    private SourcePreferenceBatch sourcePreferences(Long uid, List<ParsedDigestRow> rows) {
        LinkedHashMap<String, UserSubscriptionPreferenceKeyDTO> keys = new LinkedHashMap<>();
        for (ParsedDigestRow row : rows) {
            DigestCandidate candidate = row.candidate();
            if (candidate == null || candidate.preferenceKey() == null) {
                continue;
            }
            keys.putIfAbsent(candidate.preferenceResourceKey(), candidate.preferenceKey());
        }
        if (keys.isEmpty()) {
            return new SourcePreferenceBatch(Map.of(), false);
        }
        try {
            Map<String, UserSubscriptionPreferenceDTO> preferences =
                    subscriptionPreferenceFacade.findEffective(uid, keys.values());
            return new SourcePreferenceBatch(preferences == null ? Map.of() : preferences, false);
        } catch (RuntimeException e) {
            log.warn("update digest source preference batch failed, uid={} sourceCount={}",
                    uid, keys.size(), e);
            return new SourcePreferenceBatch(Map.of(), true);
        }
    }

    private Map<Integer, PreferenceDecision> notificationPreferences(
            Long uid, List<ParsedDigestRow> rows) {
        Map<Integer, PreferenceDecision> decisions = new LinkedHashMap<>();
        for (ParsedDigestRow row : rows) {
            if (row.candidate() == null || row.row() == null) {
                continue;
            }
            int notifType = normalizedNotificationType(row.row().getNotifType());
            decisions.computeIfAbsent(notifType, ignored -> preferenceDecision(uid, notifType));
        }
        return decisions;
    }

    private DigestCandidate candidate(NotificationMessagePO row) {
        if (row == null || row.getId() == null || row.getCreateTime() == null) {
            return null;
        }
        Map<String, Object> content = parseContent(row.getContentJson());
        String action = normalizedText(content.get("action"));
        if (!UPDATE_ACTIONS.contains(action)) {
            return null;
        }
        Long postId = positiveLong(content.get("postId"));
        if (postId == null && Integer.valueOf(TARGET_POST).equals(row.getTargetType())) {
            postId = positiveLong(row.getTargetId());
        }
        Long needId = positiveLong(content.get("needId"));
        Long seriesId = positiveLong(content.get("seriesId"));
        Long collectionId = positiveLong(content.get("collectionId"));
        String topicSlug = text(content.get("topicSlug"), 64);
        Long topicId = positiveLong(content.get("topicId"));

        String sourceType;
        String sourceId;
        Long fallbackPostId = postId;
        UserSubscriptionPreferenceKeyDTO preferenceKey = null;
        if ("collaboration_need_state_changed".equals(action) && needId != null) {
            sourceType = "NEED";
            sourceId = String.valueOf(needId);
            preferenceKey = preferenceKey("NEED", needId);
        } else if ("topic_post_published".equals(action) && (StringUtils.hasText(topicSlug) || topicId != null)) {
            sourceType = "TOPIC";
            sourceId = StringUtils.hasText(topicSlug) ? topicSlug : String.valueOf(topicId);
            preferenceKey = preferenceKey("TOPIC", topicId);
        } else if (seriesId != null) {
            sourceType = "SERIES";
            sourceId = String.valueOf(seriesId);
            preferenceKey = preferenceKey("SERIES", seriesId);
        } else if (collectionId != null) {
            sourceType = "COLLECTION";
            sourceId = String.valueOf(collectionId);
        } else if (postId != null) {
            sourceType = "POST";
            sourceId = String.valueOf(postId);
            if (action.startsWith("discussion_follow_")) {
                preferenceKey = preferenceKey("DISCUSSION", postId);
            }
        } else {
            return null;
        }

        String eventType = eventType(action, content);
        String explicitEventId = firstText(content.get("eventId"), content.get("dedupKey"));
        String eventId = StringUtils.hasText(explicitEventId)
                ? bounded(explicitEventId, 160)
                : StringUtils.hasText(row.getDedupKey())
                ? bounded(row.getDedupKey(), 160)
                : "NOTIFICATION:" + row.getId();
        String dedupKey = StringUtils.hasText(row.getDedupKey())
                ? bounded(row.getDedupKey(), 160)
                : eventId;
        return new DigestCandidate(
                row.getId(),
                sourceType,
                sourceId,
                preferenceKey,
                fallbackPostId,
                safeRequestedPath(content.get("targetPath")),
                eventType,
                eventId,
                dedupKey,
                summary(action),
                row.getCreateTime(),
                row.getIsRead() == null || row.getIsRead() == 0,
                null
        );
    }

    private Map<String, RevisitReadStateDTO> visibleRevisits(Long uid, Collection<DigestGroup> groups) {
        List<String> resourceKeys = groups.stream()
                .map(DigestGroup::resourceKey)
                .distinct()
                .toList();
        if (resourceKeys.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, RevisitReadStateDTO> states = revisitReadFacade.findVisibleStates(uid, resourceKeys);
            return states == null ? Map.of() : states;
        } catch (RuntimeException e) {
            log.warn("update digest revisit projection failed, uid={}", uid, e);
            return Map.of();
        }
    }

    private PreferenceDecision preferenceDecision(Long uid, Integer notifType) {
        try {
            boolean allowed = switch (notifType == null ? TYPE_SYSTEM : notifType) {
                case TYPE_LIKE -> userFacade.allowsLikeNotification(uid);
                case TYPE_COMMENT -> userFacade.allowsCommentNotification(uid);
                case TYPE_FAVORITE -> userFacade.allowsFavoriteNotification(uid);
                case TYPE_FOLLOWER -> userFacade.allowsFollowNotification(uid);
                case TYPE_MENTION -> userFacade.allowsMentionNotification(uid);
                case TYPE_SYSTEM -> userFacade.allowsSystemNotification(uid);
                default -> userFacade.allowsInteractionNotification(uid);
            };
            return new PreferenceDecision(allowed, false);
        } catch (RuntimeException e) {
            log.warn("update digest preference check failed, uid={} notifType={}", uid, notifType, e);
            return new PreferenceDecision(false, true);
        }
    }

    private boolean digestDeliveryEnabled(
            DigestCandidate candidate, Map<String, UserSubscriptionPreferenceDTO> preferences) {
        if (candidate.preferenceKey() == null || candidate.preferenceResourceKey() == null) {
            return false;
        }
        UserSubscriptionPreferenceDTO preference = preferences.get(candidate.preferenceResourceKey());
        return preference != null
                && Objects.equals(candidate.preferenceKey().getSourceId(), preference.getSourceId())
                && candidate.preferenceKey().getSourceType().equals(preference.getSourceType())
                && "DIGEST".equals(preference.getDeliveryMode());
    }

    private boolean resourceMatches(
            DigestCandidate candidate, PublicUpdateResourceDTO resource) {
        if (candidate == null || resource == null || !safePath(resource.getCanonicalPath())
                || !StringUtils.hasText(resource.getSourceType())
                || !StringUtils.hasText(resource.getSourceId())
                || !candidate.sourceType().equals(resource.getSourceType())) {
            return false;
        }
        if (candidate.fallbackPostId() != null
                && !Objects.equals(candidate.fallbackPostId(), resource.getPostId())) {
            return false;
        }
        if ("TOPIC".equals(candidate.sourceType())) {
            return candidate.sourceId().equalsIgnoreCase(resource.getSourceId())
                    || (candidate.preferenceKey() != null
                    && String.valueOf(candidate.preferenceKey().getSourceId())
                    .equals(candidate.sourceId()));
        }
        return candidate.sourceId().equals(resource.getSourceId());
    }

    private boolean messageTableReady() {
        try {
            return messageMapper.tableExists() > 0
                    && messageMapper.dedupKeyColumnExists() > 0;
        } catch (RuntimeException e) {
            log.warn("update digest notification table readiness check failed", e);
            return false;
        }
    }

    private Map<String, Object> parseContent(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String normalizeSourceType(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "POST", "TOPIC", "NEED", "COLLECTION", "SERIES" -> normalized;
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private String normalizeSourceId(String value, String sourceType) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 80
                || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (sourceType != null
                && !"TOPIC".equals(sourceType)
                && !normalized.matches("[1-9][0-9]*")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private DigestCursor parseCursor(String value) {
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

    private String groupKey(DigestCandidate candidate, Map<String, DigestGroup> groups) {
        String base = candidate.sourceType() + ":" + candidate.sourceId() + ":" + candidate.eventType();
        DigestGroup existing = groups.get(base);
        if (existing == null) {
            return base;
        }
        Duration distance = Duration.between(candidate.occurredAt(), existing.latestAt()).abs();
        if (distance.compareTo(Duration.ofHours(AGGREGATION_WINDOW_HOURS)) <= 0
                && existing.count() < MAX_AGGREGATE_COUNT) {
            return base;
        }
        return base + ":" + candidate.notificationId();
    }

    private boolean sourceFilterMatches(
            String requestedSourceType, String requestedSourceId, DigestCandidate candidate) {
        if (candidate == null
                || (requestedSourceType != null
                && !requestedSourceType.equals(candidate.sourceType()))) {
            return false;
        }
        if (requestedSourceId == null) {
            return true;
        }
        if ("TOPIC".equals(candidate.sourceType())) {
            return requestedSourceId.equalsIgnoreCase(candidate.sourceId())
                    || (candidate.preferenceKey() != null
                    && requestedSourceId.equals(String.valueOf(candidate.preferenceKey().getSourceId())));
        }
        return requestedSourceId.equals(candidate.sourceId());
    }

    private String eventType(String action, Map<String, Object> content) {
        String explicit = text(content.get("eventType"), 64);
        if (StringUtils.hasText(explicit)) {
            return explicit.trim().toUpperCase(Locale.ROOT);
        }
        return action.toUpperCase(Locale.ROOT);
    }

    private String summary(String action) {
        return switch (action) {
            case "topic_post_published" -> "你关注的话题有新的公开内容。";
            case "discussion_follow_comment", "comment" -> "你关注的讨论有新的公开回应。";
            case "discussion_follow_featured_reply" -> "你关注的讨论有一条新的精选回应。";
            case "discussion_follow_author_pinned" -> "作者在你关注的讨论中置顶了公开回应。";
            case "discussion_follow_author_reply" -> "作者在你关注的讨论中补充了公开回应。";
            case "collaboration_need_state_changed" -> "你关注的共建需求有公开进展。";
            case "answeraccepted", "answer_accepted" -> "相关公开内容已有回应被采纳。";
            case "maintenance_completed" -> "相关公开内容的维护已经完成。";
            case "freshness_changed" -> "相关公开内容的时效状态发生变化。";
            case "content_updated" -> "相关公开内容已完成实质更新。";
            default -> "相关公开内容有新的可见更新。";
        };
    }

    private static String normalizedText(Object value) {
        String text = text(value, 80);
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String firstText(Object first, Object second) {
        String value = text(first, 500);
        return StringUtils.hasText(value) ? value : text(second, 500);
    }

    private static String text(Object value, int maxLength) {
        if (!(value instanceof String raw) || !StringUtils.hasText(raw)) {
            return null;
        }
        return bounded(raw.trim(), maxLength);
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
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

    private static UserSubscriptionPreferenceKeyDTO preferenceKey(String sourceType, Long sourceId) {
        if (sourceId == null || sourceId <= 0) {
            return null;
        }
        return UserSubscriptionPreferenceKeyDTO.builder()
                .sourceType(sourceType)
                .sourceId(sourceId)
                .build();
    }

    private static int normalizedNotificationType(Integer notifType) {
        return notifType == null ? TYPE_SYSTEM : notifType;
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

    private static String encodeCursor(NotificationMessagePO row) {
        return row.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli() + ":" + row.getId();
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private record DigestCursor(LocalDateTime time, Long id) {
    }

    private record PreferenceDecision(boolean allowed, boolean degraded) {
    }

    private record SourcePreferenceBatch(
            Map<String, UserSubscriptionPreferenceDTO> preferences, boolean degraded) {
    }

    private record ParsedDigestRow(NotificationMessagePO row, DigestCandidate candidate) {
    }

    private record DigestCandidate(Long notificationId,
                                   String sourceType,
                                   String sourceId,
                                   UserSubscriptionPreferenceKeyDTO preferenceKey,
                                   Long fallbackPostId,
                                   String requestedPath,
                                   String eventType,
                                   String eventId,
                                   String dedupKey,
                                   String summary,
                                   LocalDateTime occurredAt,
                                   boolean unread,
                                   PublicUpdateResourceDTO resource) {

        private DigestCandidate withResource(PublicUpdateResourceDTO value) {
            return new DigestCandidate(
                    notificationId,
                    value.getSourceType(),
                    value.getSourceId(),
                    preferenceKey,
                    value.getPostId(),
                    value.getCanonicalPath(),
                    eventType,
                    eventId,
                    dedupKey,
                    summary,
                    occurredAt,
                    unread,
                    value
            );
        }

        private String preferenceResourceKey() {
            return preferenceKey == null
                    ? null
                    : preferenceKey.getSourceType() + ":" + preferenceKey.getSourceId();
        }
    }

    private static final class DigestGroup {
        private final String digestKey;
        private final String eventType;
        private final String sourceType;
        private final String sourceId;
        private final String title;
        private final String targetPath;
        private final List<Long> notificationIds = new ArrayList<>();
        private String eventId;
        private String dedupKey;
        private String summary;
        private LocalDateTime latestAt;
        private boolean unread;

        private DigestGroup(String digestKey, DigestCandidate candidate) {
            this.digestKey = digestKey;
            this.eventType = candidate.eventType();
            this.sourceType = candidate.sourceType();
            this.sourceId = candidate.sourceId();
            this.title = candidate.resource().getTitle();
            this.targetPath = candidate.resource().getCanonicalPath();
            add(candidate);
        }

        private void add(DigestCandidate candidate) {
            notificationIds.add(candidate.notificationId());
            if (latestAt == null || candidate.occurredAt().isAfter(latestAt)) {
                latestAt = candidate.occurredAt();
                eventId = candidate.eventId();
                dedupKey = candidate.dedupKey();
                summary = candidate.summary();
            }
            unread |= candidate.unread();
        }

        private int count() {
            return notificationIds.size();
        }

        private LocalDateTime latestAt() {
            return latestAt;
        }

        private String resourceKey() {
            return sourceType + ":" + sourceId;
        }

        private UpdateDigestItemDTO toDto(RevisitReadStateDTO revisit) {
            return UpdateDigestItemDTO.builder()
                    .projectionType("UPDATE_DIGEST")
                    .digestKey(digestKey)
                    .dedupKey(dedupKey)
                    .eventId(eventId)
                    .eventType(eventType)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .title(title)
                    .summary(summary)
                    .targetPath(targetPath)
                    .occurredAt(latestAt)
                    .occurrenceCount(notificationIds.size())
                    .notificationIds(List.copyOf(notificationIds))
                    .notificationUnread(unread)
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
