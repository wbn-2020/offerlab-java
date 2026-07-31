package com.offerlab.community.post.relationship.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.relationship.api.RelationshipItemDTO;
import com.offerlab.community.post.relationship.api.RelationshipSummaryDTO;
import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipMapper;
import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipRows.RelationshipCountRow;
import com.offerlab.community.post.relationship.infrastructure.persistence.RelationshipRows.RelationshipRow;
import com.offerlab.community.user.api.UserRelationshipReadFacade;
import com.offerlab.community.user.api.dto.UserRelationshipItemDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RelationshipQueryService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int FETCH_LIMIT = MAX_PAGE_SIZE + 1;
    private static final String DEFAULT_DELIVERY_MODE = "IMMEDIATE";

    private final RelationshipMapper relationshipMapper;
    private final UserRelationshipReadFacade userRelationshipReadFacade;

    public PageResult<RelationshipItemDTO> list(Long uid,
                                                String sourceType,
                                                String mode,
                                                String cursor,
                                                int size) {
        requireUid(uid);
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedMode = normalizeMode(mode);
        int pageSize = safePageSize(size);
        RelationshipCursor pageCursor = RelationshipCursor.parse(cursor);

        List<RelationshipItemDTO> candidates = new java.util.ArrayList<>();
        if (normalizedSourceType == null || "USER".equals(normalizedSourceType)) {
            List<UserRelationshipItemDTO> users = userRelationshipReadFacade.listFollowing(
                    uid, pageCursor.relationTime(), pageCursor.relationId(),
                    pageCursor.sourceType(), normalizedMode, FETCH_LIMIT);
            for (UserRelationshipItemDTO user : users == null ? List.<UserRelationshipItemDTO>of() : users) {
                candidates.add(RelationshipItemDTO.builder()
                        .sourceType("USER")
                        .sourceId(user.getUid())
                        .relationId(user.getRelationId())
                        .title(user.getNickname())
                        .summary(user.getBio())
                        .targetPath("/u/" + user.getUid())
                        .relationStatus("FOLLOWING")
                        .sourceStatus("ACTIVE")
                        .lastPublicUpdateAt(user.getLastPublicUpdateAt())
                        .relationTime(user.getRelationTime())
                        .deliveryMode(user.getDeliveryMode() == null
                                ? DEFAULT_DELIVERY_MODE : user.getDeliveryMode())
                        .expiresAt(user.getExpiresAt())
                        .build());
            }
        }

        if (normalizedSourceType == null || isPostSourceType(normalizedSourceType)) {
            List<RelationshipRow> rows = relationshipMapper.listPostRelationships(
                    uid, normalizedSourceType, pageCursor.relationTime(), pageCursor.relationId(),
                    pageCursor.sourceType(), normalizedMode, FETCH_LIMIT);
            for (RelationshipRow row : rows == null ? List.<RelationshipRow>of() : rows) {
                candidates.add(toDto(row));
            }
        }

        candidates.sort(Comparator
                .comparing(RelationshipItemDTO::getRelationTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RelationshipItemDTO::getRelationId,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RelationshipItemDTO::getSourceType,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        boolean hasMore = candidates.size() > pageSize;
        List<RelationshipItemDTO> items = candidates.stream().limit(pageSize).toList();
        String nextCursor = hasMore && !items.isEmpty()
                ? RelationshipCursor.from(items.get(items.size() - 1)).encode()
                : null;
        return PageResult.of(items, nextCursor, hasMore)
                .withMetadata("relationship-aggregate", false, null, FETCH_LIMIT);
    }

    public RelationshipSummaryDTO summary(Long uid) {
        requireUid(uid);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String sourceType : sourceTypes()) {
            counts.put(sourceType, 0L);
        }
        counts.put("USER", userRelationshipReadFacade.countFollowing(uid));
        Map<String, Long> deliveryCounts = new LinkedHashMap<>();
        deliveryCounts.put("IMMEDIATE", 0L);
        deliveryCounts.put("DIGEST", 0L);
        deliveryCounts.put("MUTED", 0L);
        userRelationshipReadFacade.countFollowingByDeliveryMode(uid)
                .forEach((mode, count) -> deliveryCounts.put(mode, Math.max(0L, count == null ? 0L : count)));
        List<RelationshipCountRow> rows = relationshipMapper.countPostRelationships(uid);
        for (RelationshipCountRow row : rows == null ? List.<RelationshipCountRow>of() : rows) {
            if (row.getSourceType() != null && isPostSourceType(row.getSourceType())) {
                counts.merge(row.getSourceType(), Math.max(0L, row.getCount() == null ? 0L : row.getCount()), Long::sum);
                if (row.getDeliveryMode() != null) {
                    deliveryCounts.merge(row.getDeliveryMode(),
                            Math.max(0L, row.getCount() == null ? 0L : row.getCount()), Long::sum);
                }
            }
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long muted = deliveryCounts.getOrDefault("MUTED", 0L);
        long immediate = deliveryCounts.getOrDefault("IMMEDIATE", 0L);
        long digest = deliveryCounts.getOrDefault("DIGEST", 0L);
        return RelationshipSummaryDTO.builder()
                .total(total)
                .active(Math.max(0L, total - muted))
                .immediate(immediate)
                .digest(digest)
                .counts(counts)
                .bySourceType(counts)
                .mutedCount(muted)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private RelationshipItemDTO toDto(RelationshipRow row) {
        return RelationshipItemDTO.builder()
                .sourceType(row.getSourceType())
                .sourceId(row.getSourceId())
                .relationId(row.getRelationId())
                .title(row.getTitle())
                .summary(row.getSummary())
                .targetPath(row.getTargetPath())
                .relationStatus(row.getRelationStatus())
                .sourceStatus(row.getSourceStatus())
                .lastPublicUpdateAt(row.getLastPublicUpdateAt())
                .relationTime(row.getRelationTime())
                .deliveryMode(row.getDeliveryMode() == null
                        ? DEFAULT_DELIVERY_MODE : row.getDeliveryMode())
                .expiresAt(row.getExpiresAt())
                .build();
    }

    private static boolean isPostSourceType(String sourceType) {
        return "TOPIC".equals(sourceType)
                || "DISCUSSION".equals(sourceType)
                || "NEED".equals(sourceType)
                || "SERIES".equals(sourceType);
    }

    private static List<String> sourceTypes() {
        return List.of("USER", "TOPIC", "DISCUSSION", "NEED", "SERIES");
    }

    private static String normalizeSourceType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!sourceTypes().contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "sourceType is invalid");
        }
        return normalized;
    }

    private static String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return "ALL";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "ACTIVE", "MUTED").contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "mode is invalid");
        }
        return normalized;
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

    private record RelationshipCursor(LocalDateTime relationTime, Long relationId, String sourceType) {

        private static RelationshipCursor parse(String value) {
            if (!StringUtils.hasText(value) || "0".equals(value.trim())) {
                return new RelationshipCursor(null, null, null);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("cursor shape");
                }
                LocalDateTime time = LocalDateTime.parse(parts[0]);
                long id = Long.parseLong(parts[1]);
                String sourceType = normalizeSourceType(parts[2]);
                if (id <= 0 || sourceType == null) {
                    throw new IllegalArgumentException("cursor id");
                }
                return new RelationshipCursor(time, id, sourceType);
            } catch (RuntimeException ex) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "cursor is invalid");
            }
        }

        private static RelationshipCursor from(RelationshipItemDTO item) {
            if (item == null || item.getRelationTime() == null || item.getRelationId() == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "relationship cursor cannot be generated");
            }
            return new RelationshipCursor(item.getRelationTime(), item.getRelationId(), item.getSourceType());
        }

        private String encode() {
            if (relationTime == null || relationId == null || sourceType == null) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "relationship cursor cannot be generated");
            }
            String raw = relationTime + "|" + relationId + "|" + sourceType;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
