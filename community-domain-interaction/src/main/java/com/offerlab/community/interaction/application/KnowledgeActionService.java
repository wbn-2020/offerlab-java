package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.interaction.api.dto.KnowledgeActionItemDTO;
import com.offerlab.community.interaction.api.dto.KnowledgeActionPage;
import com.offerlab.community.interaction.api.dto.KnowledgeActionSummaryDTO;
import com.offerlab.community.interaction.api.enums.KnowledgeActionType;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.KnowledgeActionMapper;
import com.offerlab.community.post.api.KnowledgeMaintenanceReadFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KnowledgeActionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int SOURCE_ORDER_SUGGESTION = 0;
    private static final int SOURCE_ORDER_STALE_SUGGESTION = 1;
    private static final int SOURCE_ORDER_FRESHNESS = 2;
    private static final int SOURCE_ORDER_OUTCOME_REVISIT = 3;
    private static final int MAX_SOURCE_ORDER =
            KnowledgeMaintenanceReadFacade.SOURCE_ORDER_MAINTENANCE_TASK;

    private static final Comparator<ActionCandidate> ACTION_ORDER = Comparator
            .comparing(ActionCandidate::updatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(ActionCandidate::sourceOrder)
            .thenComparing(ActionCandidate::sourceId, Comparator.reverseOrder());

    private final KnowledgeActionMapper actionMapper;
    private final PostFacade postFacade;
    private final KnowledgeMaintenanceReadFacade maintenanceReadFacade;

    public KnowledgeActionPage<KnowledgeActionItemDTO> list(Long uid, String type, String status,
                                                            String cursor, int size) {
        requireUid(uid);
        KnowledgeActionType requestedType = normalizeType(type);
        String requestedStatus = normalizeStatus(status);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int sourceLimit = pageSize + 1;
        ActionCursor parsedCursor = ActionCursor.parse(cursor);
        List<String> sourceErrors = new ArrayList<>();
        ActionAggregation aggregation = new ActionAggregation();

        collectSuggestions(uid, requestedType, requestedStatus, parsedCursor,
                sourceLimit, sourceErrors, aggregation);
        collectStaleSuggestions(uid, requestedType, requestedStatus, parsedCursor,
                sourceLimit, sourceErrors, aggregation);
        collectFreshness(uid, requestedType, requestedStatus, parsedCursor,
                sourceLimit, sourceErrors, aggregation);
        collectOutcomeRevisits(uid, requestedType, requestedStatus, parsedCursor,
                sourceLimit, sourceErrors, aggregation);
        collectPostSources(uid, requestedType, requestedStatus, parsedCursor,
                sourceLimit, sourceErrors, aggregation);

        List<ActionCandidate> afterCursor = aggregation.candidates.stream()
                .filter(parsedCursor::before)
                .sorted(ACTION_ORDER)
                .toList();
        boolean hasMore = afterCursor.size() > pageSize;
        List<ActionCandidate> pageCandidates = afterCursor.stream()
                .limit(pageSize)
                .toList();
        List<KnowledgeActionItemDTO> pageItems = pageCandidates.stream()
                .map(ActionCandidate::item)
                .toList();
        String nextCursor = hasMore && !pageCandidates.isEmpty()
                ? ActionCursor.from(pageCandidates.get(pageCandidates.size() - 1)).encode()
                : null;
        return KnowledgeActionPage.<KnowledgeActionItemDTO>builder()
                .items(pageItems)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .total(aggregation.total)
                .sourceErrors(List.copyOf(sourceErrors))
                .build();
    }

    public KnowledgeActionSummaryDTO summary(Long uid) {
        requireUid(uid);
        Map<String, Long> counts = emptyCounts();
        List<String> sourceErrors = new ArrayList<>();
        countDirectSource(counts, KnowledgeActionType.SUGGESTION_RESPONSE,
                "SUGGESTION_ACTION_SOURCE_UNAVAILABLE",
                () -> actionMapper.countSuggestionActions(uid), sourceErrors);
        countDirectSource(counts, KnowledgeActionType.STALE_SUGGESTION,
                "STALE_SUGGESTION_ACTION_SOURCE_UNAVAILABLE",
                () -> actionMapper.countStaleSuggestionActions(uid), sourceErrors);
        countDirectSource(counts, KnowledgeActionType.FRESHNESS_CONFIRMATION,
                "FRESHNESS_ACTION_SOURCE_UNAVAILABLE",
                () -> actionMapper.countFreshnessActions(uid), sourceErrors);
        countDirectSource(counts, KnowledgeActionType.OUTCOME_REVISIT,
                "OUTCOME_REVISIT_SOURCE_UNAVAILABLE",
                () -> actionMapper.countOutcomeRevisitActions(uid, null), sourceErrors);
        try {
            Map<String, Long> postCounts = maintenanceReadFacade.countActionsByType(uid);
            if (postCounts != null) {
                postCounts.forEach((actionType, count) -> {
                    if (counts.containsKey(actionType)) {
                        counts.put(actionType, Math.max(0L, count == null ? 0L : count));
                    }
                });
            }
        } catch (RuntimeException ex) {
            sourceErrors.add("POST_KNOWLEDGE_ACTION_SOURCE_UNAVAILABLE");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        for (int index = 0; index < sourceErrors.size(); index++) {
            errors.put("source" + (index + 1), sourceErrors.get(index));
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return KnowledgeActionSummaryDTO.builder()
                .total(total)
                .counts(counts)
                .degraded(!errors.isEmpty())
                .sourceErrors(errors)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private void collectSuggestions(Long uid, KnowledgeActionType requestedType,
                                    String requestedStatus, ActionCursor cursor, int limit,
                                    List<String> errors, ActionAggregation aggregation) {
        if (!include(requestedType, KnowledgeActionType.SUGGESTION_RESPONSE)
                || !includeStatus(requestedStatus, "PENDING")) {
            return;
        }
        try {
            long count = actionMapper.countSuggestionActions(uid);
            List<KnowledgeActionMapper.ActionRow> rows = safe(
                    actionMapper.listSuggestionActionsAfter(
                            uid,
                            cursor.updatedAt(),
                            cursorIdForSource(cursor, SOURCE_ORDER_SUGGESTION),
                            limit));
            Map<Long, PostBriefDTO> posts = batchAuthorPosts(rows, uid);
            for (KnowledgeActionMapper.ActionRow row : rows) {
                PostBriefDTO post = posts.get(row.getPostId());
                if (post == null) {
                    continue;
                }
                aggregation.add(
                        item(row, post, "Review pending content suggestion",
                                "SUGGESTION_RESPONSE_REQUIRED", "HIGH",
                                "/post/" + row.getPostId() + "#content-suggestions"),
                        SOURCE_ORDER_SUGGESTION,
                        row.getId());
            }
            aggregation.total += Math.max(0L, count);
        } catch (RuntimeException ex) {
            errors.add("SUGGESTION_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private void collectStaleSuggestions(Long uid, KnowledgeActionType requestedType,
                                         String requestedStatus, ActionCursor cursor, int limit,
                                         List<String> errors, ActionAggregation aggregation) {
        if (!include(requestedType, KnowledgeActionType.STALE_SUGGESTION)
                || !includeStatus(requestedStatus, "PENDING")) {
            return;
        }
        try {
            long count = actionMapper.countStaleSuggestionActions(uid);
            List<KnowledgeActionMapper.ActionRow> rows = safe(
                    actionMapper.listStaleSuggestionActionsAfter(
                            uid,
                            cursor.updatedAt(),
                            cursorIdForSource(cursor, SOURCE_ORDER_STALE_SUGGESTION),
                            limit));
            Map<Long, PostBriefDTO> posts = batchAuthorPosts(rows, uid);
            for (KnowledgeActionMapper.ActionRow row : rows) {
                PostBriefDTO post = posts.get(row.getPostId());
                if (post == null) {
                    continue;
                }
                aggregation.add(
                        item(row, post, "Review suggestion based on an older version",
                                "STALE_SUGGESTION_REQUIRES_RESPONSE", "HIGH",
                                "/post/" + row.getPostId() + "#content-suggestions"),
                        SOURCE_ORDER_STALE_SUGGESTION,
                        row.getId());
            }
            aggregation.total += Math.max(0L, count);
        } catch (RuntimeException ex) {
            errors.add("STALE_SUGGESTION_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private void collectFreshness(Long uid, KnowledgeActionType requestedType,
                                  String requestedStatus, ActionCursor cursor, int limit,
                                  List<String> errors, ActionAggregation aggregation) {
        if (!include(requestedType, KnowledgeActionType.FRESHNESS_CONFIRMATION)
                || !includeStatus(requestedStatus, "AWAITING_AUTHOR_CONFIRMATION")) {
            return;
        }
        try {
            long count = actionMapper.countFreshnessActions(uid);
            List<KnowledgeActionMapper.ActionRow> rows = safe(
                    actionMapper.listFreshnessActionsAfter(
                            uid,
                            cursor.updatedAt(),
                            cursorIdForSource(cursor, SOURCE_ORDER_FRESHNESS),
                            limit));
            Map<Long, PostBriefDTO> posts = batchAuthorPosts(rows, uid);
            for (KnowledgeActionMapper.ActionRow row : rows) {
                PostBriefDTO post = posts.get(row.getPostId());
                if (post == null) {
                    continue;
                }
                aggregation.add(
                        item(row, post, "Confirm whether this content is still current",
                                "FRESHNESS_CONFIRMATION_REQUIRED", "HIGH",
                                "/post/" + row.getPostId() + "#trusted-content"),
                        SOURCE_ORDER_FRESHNESS,
                        row.getId());
            }
            aggregation.total += Math.max(0L, count);
        } catch (RuntimeException ex) {
            errors.add("FRESHNESS_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private void collectOutcomeRevisits(Long uid, KnowledgeActionType requestedType,
                                        String requestedStatus, ActionCursor cursor, int limit,
                                        List<String> errors, ActionAggregation aggregation) {
        if (!include(requestedType, KnowledgeActionType.OUTCOME_REVISIT)) {
            return;
        }
        try {
            long count = actionMapper.countOutcomeRevisitActions(uid, requestedStatus);
            List<KnowledgeActionMapper.ActionRow> rows = safe(
                    actionMapper.listOutcomeRevisitActionsAfter(
                            uid,
                            requestedStatus,
                            cursor.updatedAt(),
                            cursorIdForSource(cursor, SOURCE_ORDER_OUTCOME_REVISIT),
                            limit));
            Map<Long, PostBriefDTO> posts = batchVisiblePosts(rows, uid);
            for (KnowledgeActionMapper.ActionRow row : rows) {
                PostBriefDTO post = posts.get(row.getPostId());
                String title = post == null ? "Revisit your practice outcome" : post.getTitle();
                KnowledgeActionItemDTO item = KnowledgeActionItemDTO.builder()
                        .id(KnowledgeActionType.OUTCOME_REVISIT.name() + ":" + row.getId())
                        .type(KnowledgeActionType.OUTCOME_REVISIT.name())
                        .title(title)
                        .reason("OUTCOME_FOLLOW_UP_DUE")
                        .status(row.getActionStatus())
                        .priority("MEDIUM")
                        .canonicalRoute("/post/" + row.getPostId() + "#outcomes")
                        .postId(row.getPostId())
                        .updatedAt(row.getUpdatedAt())
                        .build();
                aggregation.add(item, SOURCE_ORDER_OUTCOME_REVISIT, row.getId());
            }
            aggregation.total += Math.max(0L, count);
        } catch (RuntimeException ex) {
            errors.add("OUTCOME_REVISIT_SOURCE_UNAVAILABLE");
        }
    }

    private void collectPostSources(Long uid, KnowledgeActionType requestedType,
                                    String requestedStatus, ActionCursor cursor, int limit,
                                    List<String> errors, ActionAggregation aggregation) {
        try {
            String actionType = requestedType == null ? null : requestedType.name();
            long count = maintenanceReadFacade.countActions(uid, actionType, requestedStatus);
            List<KnowledgeMaintenanceSourceDTO> sources = safe(
                    maintenanceReadFacade.listActions(
                            uid,
                            actionType,
                            requestedStatus,
                            cursor.updatedAt(),
                            cursor.sourceOrder(),
                            cursor.sourceId(),
                            limit));
            for (KnowledgeMaintenanceSourceDTO source : sources) {
                KnowledgeActionType actualType;
                try {
                    actualType = KnowledgeActionType.parse(source.getActionType());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (!include(requestedType, actualType)
                        || !includeStatus(requestedStatus, source.getStatus())) {
                    continue;
                }
                SourceIdentity identity = SourceIdentity.parse(source.getSourceKey());
                if (identity == null) {
                    continue;
                }
                KnowledgeActionItemDTO item = KnowledgeActionItemDTO.builder()
                        .id(source.getSourceKey())
                        .type(actualType.name())
                        .title(source.getTitle())
                        .reason(source.getReason())
                        .status(source.getStatus())
                        .priority(source.getPriority())
                        .canonicalRoute(source.getCanonicalRoute())
                        .postId(source.getPostId())
                        .updatedAt(source.getUpdatedAt())
                        .build();
                aggregation.add(item, identity.sourceOrder(), identity.sourceId());
            }
            aggregation.total += Math.max(0L, count);
        } catch (RuntimeException ex) {
            errors.add("POST_KNOWLEDGE_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private static KnowledgeActionItemDTO item(KnowledgeActionMapper.ActionRow row, PostBriefDTO post,
                                               String fallbackTitle, String reason, String priority,
                                               String route) {
        return KnowledgeActionItemDTO.builder()
                .id(row.getActionType() + ":" + row.getId())
                .type(row.getActionType())
                .title(StringUtils.hasText(post.getTitle()) ? post.getTitle() : fallbackTitle)
                .reason(reason)
                .status(row.getActionStatus())
                .priority(priority)
                .canonicalRoute(route)
                .postId(row.getPostId())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private static Map<String, Long> emptyCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (KnowledgeActionType type : KnowledgeActionType.values()) {
            counts.put(type.name(), 0L);
        }
        return counts;
    }

    private static void countDirectSource(Map<String, Long> counts,
                                          KnowledgeActionType type,
                                          String error,
                                          CountSupplier supplier,
                                          List<String> errors) {
        try {
            counts.put(type.name(), Math.max(0L, supplier.get()));
        } catch (RuntimeException ex) {
            errors.add(error);
        }
    }

    private static boolean include(KnowledgeActionType requested, KnowledgeActionType actual) {
        return requested == null || requested == actual;
    }

    private static boolean includeStatus(String requested, String actual) {
        return requested == null || requested.equalsIgnoreCase(actual);
    }

    private static KnowledgeActionType normalizeType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return KnowledgeActionType.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "type is invalid");
        }
    }

    private static String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z_]{2,32}")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "status is invalid");
        }
        return normalized;
    }

    private static <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private Map<Long, PostBriefDTO> batchAuthorPosts(List<KnowledgeActionMapper.ActionRow> rows, Long uid) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPostsForAuthor(postIds(rows), uid);
        return posts == null ? Map.of() : posts;
    }

    private Map<Long, PostBriefDTO> batchVisiblePosts(List<KnowledgeActionMapper.ActionRow> rows, Long uid) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostBriefDTO> posts = postFacade.batchGetPosts(postIds(rows), uid, true);
        return posts == null ? Map.of() : posts;
    }

    private List<Long> postIds(List<KnowledgeActionMapper.ActionRow> rows) {
        return rows.stream()
                .map(KnowledgeActionMapper.ActionRow::getPostId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static Long cursorIdForSource(ActionCursor cursor, int sourceOrder) {
        if (cursor.updatedAt() == null) {
            return null;
        }
        if (sourceOrder < cursor.sourceOrder()) {
            return 0L;
        }
        if (sourceOrder > cursor.sourceOrder()) {
            return Long.MAX_VALUE;
        }
        return cursor.sourceId();
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private record ActionCandidate(KnowledgeActionItemDTO item,
                                   int sourceOrder,
                                   Long sourceId) {
        private LocalDateTime updatedAt() {
            return item.getUpdatedAt();
        }
    }

    private static final class ActionAggregation {
        private final List<ActionCandidate> candidates = new ArrayList<>();
        private long total;

        private void add(KnowledgeActionItemDTO item, int sourceOrder, Long sourceId) {
            if (item == null || item.getUpdatedAt() == null || sourceId == null || sourceId <= 0) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "knowledge action source returned an invalid cursor anchor");
            }
            candidates.add(new ActionCandidate(item, sourceOrder, sourceId));
        }
    }

    private record SourceIdentity(int sourceOrder, Long sourceId) {
        private static SourceIdentity parse(String sourceKey) {
            if (!StringUtils.hasText(sourceKey)) {
                return null;
            }
            int separator = sourceKey.lastIndexOf(':');
            if (separator <= 0 || separator == sourceKey.length() - 1) {
                return null;
            }
            try {
                long sourceId = Long.parseLong(sourceKey.substring(separator + 1));
                if (sourceId <= 0) {
                    return null;
                }
                int sourceOrder = switch (sourceKey.substring(0, separator)) {
                    case "SUGGESTION_RESPONSE" -> SOURCE_ORDER_SUGGESTION;
                    case "STALE_SUGGESTION" -> SOURCE_ORDER_STALE_SUGGESTION;
                    case "FRESHNESS_CONFIRMATION" -> SOURCE_ORDER_FRESHNESS;
                    case "OUTCOME_REVISIT" -> SOURCE_ORDER_OUTCOME_REVISIT;
                    case "REFERENCE" -> KnowledgeMaintenanceReadFacade.SOURCE_ORDER_REFERENCE;
                    case "RELATION_PROPOSAL" ->
                            KnowledgeMaintenanceReadFacade.SOURCE_ORDER_RELATION_PROPOSAL;
                    case "RELATION_REVIEW" ->
                            KnowledgeMaintenanceReadFacade.SOURCE_ORDER_RELATION_REVIEW;
                    case "MAINTENANCE_TASK" ->
                            KnowledgeMaintenanceReadFacade.SOURCE_ORDER_MAINTENANCE_TASK;
                    default -> -1;
                };
                return sourceOrder < 0 ? null : new SourceIdentity(sourceOrder, sourceId);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    record ActionCursor(LocalDateTime updatedAt, Integer sourceOrder, Long sourceId) {
        private static ActionCursor parse(String value) {
            if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
                return new ActionCursor(null, null, null);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length == 4 && "v2".equals(parts[0])) {
                    int sourceOrder = Integer.parseInt(parts[2]);
                    long sourceId = Long.parseLong(parts[3]);
                    validate(sourceOrder, sourceId);
                    return new ActionCursor(LocalDateTime.parse(parts[1]), sourceOrder, sourceId);
                }
                if (parts.length == 3) {
                    KnowledgeActionType.parse(parts[2]);
                    SourceIdentity identity = SourceIdentity.parse(parts[1]);
                    if (identity == null) {
                        throw new IllegalArgumentException("legacy cursor source");
                    }
                    return new ActionCursor(
                            LocalDateTime.parse(parts[0]),
                            identity.sourceOrder(),
                            identity.sourceId());
                }
                throw new IllegalArgumentException("cursor shape");
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
            }
        }

        private static ActionCursor from(ActionCandidate candidate) {
            if (candidate == null || candidate.updatedAt() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "knowledge action cursor cannot be generated");
            }
            validate(candidate.sourceOrder(), candidate.sourceId());
            return new ActionCursor(
                    candidate.updatedAt(),
                    candidate.sourceOrder(),
                    candidate.sourceId());
        }

        private static void validate(int sourceOrder, long sourceId) {
            if (sourceOrder < SOURCE_ORDER_SUGGESTION
                    || sourceOrder > MAX_SOURCE_ORDER
                    || sourceId <= 0) {
                throw new IllegalArgumentException("cursor values");
            }
        }

        private boolean before(ActionCandidate candidate) {
            if (updatedAt == null) {
                return true;
            }
            int timeCompare = candidate.updatedAt().compareTo(updatedAt);
            if (timeCompare != 0) {
                return timeCompare < 0;
            }
            if (candidate.sourceOrder() != sourceOrder) {
                return candidate.sourceOrder() > sourceOrder;
            }
            return candidate.sourceId() < sourceId;
        }

        private String encode() {
            String raw = "v2|" + updatedAt + "|" + sourceOrder + "|" + sourceId;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    @FunctionalInterface
    private interface CountSupplier {
        long get();
    }
}
