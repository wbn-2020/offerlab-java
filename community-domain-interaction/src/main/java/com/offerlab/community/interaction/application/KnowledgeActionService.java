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
import com.offerlab.community.post.api.dto.PostDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KnowledgeActionService {

    private static final int MAX_PAGE_SIZE = 50;
    private final KnowledgeActionMapper actionMapper;
    private final PostFacade postFacade;
    private final KnowledgeMaintenanceReadFacade maintenanceReadFacade;

    public KnowledgeActionPage<KnowledgeActionItemDTO> list(Long uid, String type, String status,
                                                            String cursor, int size) {
        requireUid(uid);
        KnowledgeActionType requestedType = normalizeType(type);
        String requestedStatus = normalizeStatus(status);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        ActionCursor parsedCursor = ActionCursor.parse(cursor);
        List<String> sourceErrors = new ArrayList<>();
        List<KnowledgeActionItemDTO> items = collect(uid, requestedType, requestedStatus, sourceErrors);
        List<KnowledgeActionItemDTO> afterCursor = items.stream()
                .filter(item -> parsedCursor.before(item))
                .toList();
        boolean hasMore = afterCursor.size() > pageSize;
        List<KnowledgeActionItemDTO> pageItems = afterCursor.stream().limit(pageSize).toList();
        String nextCursor = hasMore && !pageItems.isEmpty()
                ? ActionCursor.from(pageItems.get(pageItems.size() - 1)).encode() : null;
        return KnowledgeActionPage.<KnowledgeActionItemDTO>builder()
                .items(pageItems)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .total((long) items.size())
                .sourceErrors(List.copyOf(sourceErrors))
                .build();
    }

    public KnowledgeActionSummaryDTO summary(Long uid) {
        requireUid(uid);
        List<String> sourceErrors = new ArrayList<>();
        List<KnowledgeActionItemDTO> items = collect(uid, null, null, sourceErrors);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (KnowledgeActionType type : KnowledgeActionType.values()) {
            counts.put(type.name(), 0L);
        }
        for (KnowledgeActionItemDTO item : items) {
            counts.computeIfPresent(item.getType(), (key, count) -> count + 1);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (int index = 0; index < sourceErrors.size(); index++) {
            errors.put("source" + (index + 1), sourceErrors.get(index));
        }
        return KnowledgeActionSummaryDTO.builder()
                .total((long) items.size())
                .counts(counts)
                .degraded(!errors.isEmpty())
                .sourceErrors(errors)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private List<KnowledgeActionItemDTO> collect(Long uid, KnowledgeActionType requestedType,
                                                 String requestedStatus, List<String> sourceErrors) {
        List<KnowledgeActionItemDTO> items = new ArrayList<>();
        collectSuggestions(uid, requestedType, sourceErrors, items);
        collectStaleSuggestions(uid, requestedType, sourceErrors, items);
        collectFreshness(uid, requestedType, sourceErrors, items);
        collectOutcomeRevisits(uid, requestedType, sourceErrors, items);
        collectPostSources(uid, requestedType, sourceErrors, items);
        return items.stream()
                .filter(item -> requestedStatus == null
                        || requestedStatus.equalsIgnoreCase(item.getStatus()))
                .sorted(Comparator
                        .comparing(KnowledgeActionItemDTO::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(KnowledgeActionItemDTO::getId, Comparator.reverseOrder())
                        .thenComparing(KnowledgeActionItemDTO::getType))
                .toList();
    }

    private void collectStaleSuggestions(Long uid, KnowledgeActionType requestedType,
                                         List<String> errors, List<KnowledgeActionItemDTO> items) {
        if (!include(requestedType, KnowledgeActionType.STALE_SUGGESTION)) {
            return;
        }
        try {
            for (KnowledgeActionMapper.ActionRow row :
                    safe(actionMapper.listStaleSuggestionActions(uid, 0))) {
                PostDTO post = postFacade.getPostForAuthor(row.getPostId(), uid);
                if (post == null) {
                    continue;
                }
                items.add(item(row, post, "Review suggestion based on an older version",
                        "STALE_SUGGESTION_REQUIRES_RESPONSE", "HIGH",
                        "/post/" + row.getPostId() + "#content-suggestions"));
            }
        } catch (RuntimeException ex) {
            errors.add("STALE_SUGGESTION_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private void collectSuggestions(Long uid, KnowledgeActionType requestedType,
                                    List<String> errors, List<KnowledgeActionItemDTO> items) {
        if (!include(requestedType, KnowledgeActionType.SUGGESTION_RESPONSE)) {
            return;
        }
        try {
            for (KnowledgeActionMapper.ActionRow row :
                    safe(actionMapper.listSuggestionActions(uid, 0))) {
                PostDTO post = postFacade.getPostForAuthor(row.getPostId(), uid);
                if (post == null) {
                    continue;
                }
                items.add(item(row, post, "Review pending content suggestion",
                        "SUGGESTION_RESPONSE_REQUIRED", "HIGH",
                        "/post/" + row.getPostId() + "#content-suggestions"));
            }
        } catch (RuntimeException ex) {
            errors.add("SUGGESTION_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private void collectFreshness(Long uid, KnowledgeActionType requestedType,
                                  List<String> errors, List<KnowledgeActionItemDTO> items) {
        if (!include(requestedType, KnowledgeActionType.FRESHNESS_CONFIRMATION)) {
            return;
        }
        try {
            for (KnowledgeActionMapper.ActionRow row :
                    safe(actionMapper.listFreshnessActions(0))) {
                PostDTO post = postFacade.getPostForAuthor(row.getPostId(), uid);
                if (post == null) {
                    continue;
                }
                items.add(item(row, post, "Confirm whether this content is still current",
                        "FRESHNESS_CONFIRMATION_REQUIRED", "HIGH",
                        "/post/" + row.getPostId() + "#trusted-content"));
            }
        } catch (RuntimeException ex) {
            errors.add("FRESHNESS_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private void collectOutcomeRevisits(Long uid, KnowledgeActionType requestedType,
                                        List<String> errors, List<KnowledgeActionItemDTO> items) {
        if (!include(requestedType, KnowledgeActionType.OUTCOME_REVISIT)) {
            return;
        }
        try {
            for (KnowledgeActionMapper.ActionRow row :
                    safe(actionMapper.listOutcomeRevisitActions(uid, 0))) {
                PostDTO post = postFacade.getPost(row.getPostId(), uid);
                String title = post == null ? "Revisit your practice outcome" : post.getTitle();
                items.add(KnowledgeActionItemDTO.builder()
                        .id(KnowledgeActionType.OUTCOME_REVISIT.name() + ":" + row.getId())
                        .type(KnowledgeActionType.OUTCOME_REVISIT.name())
                        .title(title)
                        .reason("OUTCOME_FOLLOW_UP_DUE")
                        .status(row.getActionStatus())
                        .priority("MEDIUM")
                        .canonicalRoute("/post/" + row.getPostId() + "#outcomes")
                        .postId(row.getPostId())
                        .updatedAt(row.getUpdatedAt())
                        .build());
            }
        } catch (RuntimeException ex) {
            errors.add("OUTCOME_REVISIT_SOURCE_UNAVAILABLE");
        }
    }

    private void collectPostSources(Long uid, KnowledgeActionType requestedType,
                                    List<String> errors, List<KnowledgeActionItemDTO> items) {
        try {
            for (KnowledgeMaintenanceSourceDTO source :
                    safe(maintenanceReadFacade.listActions(uid, 0))) {
                KnowledgeActionType actualType;
                try {
                    actualType = KnowledgeActionType.parse(source.getActionType());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (!include(requestedType, actualType)) {
                    continue;
                }
                items.add(KnowledgeActionItemDTO.builder()
                        .id(source.getSourceKey())
                        .type(actualType.name())
                        .title(source.getTitle())
                        .reason(source.getReason())
                        .status(source.getStatus())
                        .priority(source.getPriority())
                        .canonicalRoute(source.getCanonicalRoute())
                        .postId(source.getPostId())
                        .updatedAt(source.getUpdatedAt())
                        .build());
            }
        } catch (RuntimeException ex) {
            errors.add("POST_KNOWLEDGE_ACTION_SOURCE_UNAVAILABLE");
        }
    }

    private static KnowledgeActionItemDTO item(KnowledgeActionMapper.ActionRow row, PostDTO post,
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

    private static boolean include(KnowledgeActionType requested, KnowledgeActionType actual) {
        return requested == null || requested == actual;
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

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private record ActionCursor(LocalDateTime updatedAt, String id, String type) {
        private static ActionCursor parse(String value) {
            if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
                return new ActionCursor(null, null, null);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 3 || parts[1].isBlank() || parts[2].isBlank()) {
                    throw new IllegalArgumentException("cursor shape");
                }
                KnowledgeActionType.parse(parts[2]);
                return new ActionCursor(LocalDateTime.parse(parts[0]), parts[1], parts[2]);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
            }
        }

        private static ActionCursor from(KnowledgeActionItemDTO item) {
            if (item == null || item.getUpdatedAt() == null || item.getId() == null || item.getType() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "knowledge action cursor cannot be generated");
            }
            return new ActionCursor(item.getUpdatedAt(), item.getId(), item.getType());
        }

        private boolean before(KnowledgeActionItemDTO item) {
            if (updatedAt == null) {
                return true;
            }
            int timeCompare = item.getUpdatedAt().compareTo(updatedAt);
            if (timeCompare != 0) {
                return timeCompare < 0;
            }
            int idCompare = item.getId().compareTo(id);
            if (idCompare != 0) {
                return idCompare < 0;
            }
            return item.getType().compareTo(type) > 0;
        }

        private String encode() {
            String raw = updatedAt + "|" + id + "|" + type;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
