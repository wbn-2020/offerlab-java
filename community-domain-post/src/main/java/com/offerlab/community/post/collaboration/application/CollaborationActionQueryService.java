package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.CollaborationActionItemDTO;
import com.offerlab.community.post.collaboration.api.CollaborationActionSummaryDTO;
import com.offerlab.community.post.collaboration.api.CollaborationActionType;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationActionQueryMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationActionQueryRows;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CollaborationActionQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CollaborationActionQueryMapper mapper;

    public CollaborationActionSummaryDTO summary(Long uid) {
        requireUid(uid);
        Map<String, Integer> counts = emptyCounts();
        Map<String, String> sourceErrors = new LinkedHashMap<>();
        boolean degraded = false;
        try {
            List<CollaborationActionQueryRows.ActionCountRow> rows = mapper.countActions(uid);
            for (CollaborationActionQueryRows.ActionCountRow row : rows == null ? List.<CollaborationActionQueryRows.ActionCountRow>of() : rows) {
                if (row.getActionType() != null && counts.containsKey(row.getActionType())) {
                    counts.put(row.getActionType(), Math.max(0, row.getCount() == null ? 0 : row.getCount()));
                }
            }
        } catch (RuntimeException ex) {
            degraded = true;
            sourceErrors.put("collaboration", "ACTION_READ_UNAVAILABLE");
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return CollaborationActionSummaryDTO.builder()
                .total(total)
                .counts(counts)
                .generatedAt(LocalDateTime.now())
                .degraded(degraded)
                .sourceErrors(sourceErrors.isEmpty() ? Map.of() : sourceErrors)
                .build();
    }

    public PageResult<CollaborationActionItemDTO> list(Long uid, String actionType, String cursor, int size) {
        requireUid(uid);
        String normalizedActionType = normalizeActionType(actionType);
        int pageSize = safePageSize(size);
        ActionCursor pageCursor = ActionCursor.parse(cursor);
        try {
            List<CollaborationActionQueryRows.ActionRow> rows = mapper.listActions(
                    uid,
                    normalizedActionType,
                    pageCursor.updatedAt(),
                    pageCursor.sourceId(),
                    pageCursor.actionType(),
                    pageSize + 1);
            List<CollaborationActionQueryRows.ActionRow> safeRows =
                    rows == null ? List.of() : rows;
            boolean hasMore = safeRows.size() > pageSize;
            List<CollaborationActionQueryRows.ActionRow> visibleRows = safeRows.stream()
                    .limit(pageSize)
                    .toList();
            List<CollaborationActionItemDTO> items = visibleRows.stream()
                    .map(this::toDto)
                    .toList();
            String nextCursor = hasMore && !visibleRows.isEmpty()
                    ? ActionCursor.from(visibleRows.get(visibleRows.size() - 1)).encode()
                    : null;
            return PageResult.of(items, nextCursor, hasMore)
                    .withMetadata("collaboration-actions", false, null, pageSize + 1);
        } catch (RuntimeException ex) {
            return PageResult.<CollaborationActionItemDTO>empty()
                    .withMetadata("collaboration-actions", true, "ACTION_READ_UNAVAILABLE", pageSize + 1);
        }
    }

    private CollaborationActionItemDTO toDto(CollaborationActionQueryRows.ActionRow row) {
        return CollaborationActionItemDTO.builder()
                .id(row.getSourceId())
                .actionType(row.getActionType())
                .sourceType(row.getSourceType())
                .sourceId(row.getSourceId())
                .sourceStatus(row.getSourceStatus())
                .title(row.getTitle())
                .reason(row.getReason())
                .targetPath(row.getTargetPath())
                .lastEventId(row.getLastEventId())
                .updatedAt(row.getUpdatedAt())
                .canAct(row.getCanAct() != null && row.getCanAct() == 1)
                .build();
    }

    private static Map<String, Integer> emptyCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CollaborationActionType type : CollaborationActionType.values()) {
            counts.put(type.name(), 0);
        }
        return counts;
    }

    private static String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return null;
        }
        try {
            return CollaborationActionType.parse(actionType).name();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "actionType is invalid");
        }
    }

    private static int safePageSize(int size) {
        if (size <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private record ActionCursor(LocalDateTime updatedAt, Long sourceId, String actionType) {

        private static ActionCursor parse(String value) {
            if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
                return new ActionCursor(null, null, null);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("cursor shape");
                }
                long id = Long.parseLong(parts[1]);
                if (id <= 0 || parts[2].isBlank()) {
                    throw new IllegalArgumentException("cursor values");
                }
                CollaborationActionType.parse(parts[2]);
                return new ActionCursor(LocalDateTime.parse(parts[0]), id, parts[2]);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
            }
        }

        private static ActionCursor from(CollaborationActionQueryRows.ActionRow row) {
            if (row == null || row.getUpdatedAt() == null || row.getSourceId() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), "action cursor cannot be generated");
            }
            return new ActionCursor(row.getUpdatedAt(), row.getSourceId(), row.getActionType());
        }

        private String encode() {
            String raw = updatedAt + "|" + sourceId + "|" + actionType;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
