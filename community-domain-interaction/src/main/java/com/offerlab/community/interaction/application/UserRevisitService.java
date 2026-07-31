package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.interaction.api.RevisitReadFacade;
import com.offerlab.community.interaction.api.dto.RevisitItemDTO;
import com.offerlab.community.interaction.api.dto.RevisitReadStateDTO;
import com.offerlab.community.interaction.api.dto.RevisitSnoozeCmd;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.UserRevisitItemMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.UserRevisitItemPO;
import com.offerlab.community.interaction.infrastructure.persistence.projection.RevisitSourceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserRevisitService implements RevisitReadFacade {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int SOURCE_SCAN_LIMIT = 50;
    private static final int MAX_SNOOZE_DAYS = 30;

    private final UserRevisitItemMapper revisitMapper;
    private final SnowflakeIdGenerator idGenerator;

    @Transactional
    public void schedulePostOutcome(Long uid, Long outcomeId, Long postId, Integer revision,
                                    String postTitle, LocalDateTime followUpAt) {
        requireUid(uid);
        requireId(outcomeId);
        requireId(postId);
        if (revision == null || revision <= 0 || followUpAt == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!revisitTableReady()) {
            return;
        }
        UserRevisitItemPO item = new UserRevisitItemPO();
        item.setId(idGenerator.nextId());
        item.setUid(uid);
        item.setSourceType("POST_OUTCOME");
        item.setSourceId(String.valueOf(outcomeId));
        item.setReasonType("OUTCOME_FOLLOW_UP");
        item.setActivityCursor(revision.longValue());
        item.setTitle(cleanRequired(postTitle, 160));
        item.setDescription("Revisit your recorded practice outcome");
        item.setTargetPath("/post/" + postId);
        item.setDueAt(followUpAt);
        item.setDedupKey(sha256(uid + "|POST_OUTCOME|" + outcomeId));
        revisitMapper.upsertPostOutcome(item);
    }

    @Transactional
    public void cancelPostOutcome(Long uid, Long outcomeId) {
        requireUid(uid);
        requireId(outcomeId);
        if (!revisitTableReady()) {
            return;
        }
        revisitMapper.completePostOutcome(uid, String.valueOf(outcomeId));
    }

    @Override
    public Map<String, RevisitReadStateDTO> findVisibleStates(Long uid, Collection<String> resourceKeys) {
        if (uid == null || uid <= 0 || resourceKeys == null || resourceKeys.isEmpty() || !revisitTableReady()) {
            return Map.of();
        }
        List<String> keys = resourceKeys.stream()
                .map(UserRevisitService::cleanResourceKey)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        values -> values.stream().limit(100).toList()));
        if (keys.isEmpty()) {
            return Map.of();
        }
        try {
            List<UserRevisitItemPO> rows = revisitMapper.listVisibleByResourceKeys(uid, keys);
            Map<String, RevisitReadStateDTO> result = new LinkedHashMap<>();
            for (UserRevisitItemPO row : rows == null ? List.<UserRevisitItemPO>of() : rows) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                String nativeKey = resourceKey(row);
                if (nativeKey != null && keys.contains(nativeKey)) {
                    result.putIfAbsent(nativeKey, toReadState(row, nativeKey));
                }
            }
            for (UserRevisitItemPO row : rows == null ? List.<UserRevisitItemPO>of() : rows) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                String postKey = postResourceKey(row.getTargetPath());
                if (postKey != null && keys.contains(postKey) && !result.containsKey(postKey)) {
                    result.put(postKey, toReadState(row, postKey));
                }
            }
            return Map.copyOf(result);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    @Transactional
    public PageResult<RevisitItemDTO> list(Long uid, String requestedStatus, String cursor, int size) {
        requireUid(uid);
        if (!revisitTableReady()) {
            return unavailablePage();
        }
        refresh(uid);
        revisitMapper.reopenDueSnoozes(uid);
        String status = normalizeStatus(requestedStatus);
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Long parsedCursor = parseCursor(cursor);
        List<UserRevisitItemPO> rows = revisitMapper.listByUser(uid, status, parsedCursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<UserRevisitItemPO> page = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore && !page.isEmpty() ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return PageResult.of(page.stream().map(this::toDto).toList(), next, hasMore)
                .withDiagnostic("onSiteOnly", true)
                .withDiagnostic("externalPush", false)
                .withDiagnostic("advertising", false)
                .withDiagnostic("payment", false);
    }

    @Transactional
    public RevisitItemDTO complete(Long uid, Long itemId) {
        requireUid(uid);
        requireId(itemId);
        requireRevisitTable();
        UserRevisitItemPO current = requireOwned(uid, itemId);
        if (revisitMapper.complete(itemId, uid) <= 0 && !"COMPLETED".equals(current.getRevisitStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return toDto(requireOwned(uid, itemId));
    }

    @Transactional
    public RevisitItemDTO snooze(Long uid, Long itemId, RevisitSnoozeCmd cmd) {
        requireUid(uid);
        requireId(itemId);
        requireRevisitTable();
        LocalDateTime until = cmd == null ? null : cmd.getUntil();
        LocalDateTime now = LocalDateTime.now();
        if (until == null || !until.isAfter(now) || until.isAfter(now.plusDays(MAX_SNOOZE_DAYS))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "snooze time must be within the next 30 days");
        }
        requireOwned(uid, itemId);
        if (revisitMapper.snooze(itemId, uid, until) <= 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return toDto(requireOwned(uid, itemId));
    }

    @Transactional
    public RevisitItemDTO ignore(Long uid, Long itemId) {
        requireUid(uid);
        requireId(itemId);
        requireRevisitTable();
        UserRevisitItemPO current = requireOwned(uid, itemId);
        if (revisitMapper.ignore(itemId, uid) <= 0 && !"IGNORED".equals(current.getRevisitStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return toDto(requireOwned(uid, itemId));
    }

    private void refresh(Long uid) {
        upsertAll(uid, revisitMapper.selectDiscussionCandidates(uid, SOURCE_SCAN_LIMIT));
        upsertAll(uid, revisitMapper.selectFavoriteCandidates(uid, SOURCE_SCAN_LIMIT));
        upsertAll(uid, revisitMapper.selectFollowingAuthorCandidates(uid, SOURCE_SCAN_LIMIT));
    }

    private PageResult<RevisitItemDTO> unavailablePage() {
        return PageResult.<RevisitItemDTO>empty()
                .withDiagnostic("onSiteOnly", true)
                .withDiagnostic("externalPush", false)
                .withDiagnostic("advertising", false)
                .withDiagnostic("payment", false)
                .withDiagnostic("migrationPending", true);
    }

    private void requireRevisitTable() {
        if (!revisitTableReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Revisit migration is required: db/migration/20260715_trusted_distribution_revisit.sql");
        }
    }

    private boolean revisitTableReady() {
        try {
            return revisitMapper.tableExists() > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void upsertAll(Long uid, List<RevisitSourceRow> rows) {
        if (rows == null) {
            return;
        }
        for (RevisitSourceRow row : rows) {
            if (row == null || !safePath(row.getTargetPath()) || row.getActivityCursor() == null) {
                continue;
            }
            UserRevisitItemPO item = new UserRevisitItemPO();
            item.setId(idGenerator.nextId());
            item.setUid(uid);
            item.setSourceType(cleanRequired(row.getSourceType(), 32));
            item.setSourceId(cleanRequired(row.getSourceId(), 64));
            item.setReasonType(cleanRequired(row.getReasonType(), 32));
            item.setActivityCursor(Math.max(row.getActivityCursor(), 0L));
            item.setTitle(cleanRequired(row.getTitle(), 160));
            item.setDescription(cleanOptional(row.getDescription(), 500));
            item.setTargetPath(row.getTargetPath());
            item.setDedupKey(sha256(uid + "|" + item.getSourceType() + "|" + item.getSourceId()));
            revisitMapper.upsertCandidate(item);
        }
    }

    private UserRevisitItemPO requireOwned(Long uid, Long itemId) {
        UserRevisitItemPO item = revisitMapper.selectOwned(itemId, uid);
        if (item == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return item;
    }

    private RevisitItemDTO toDto(UserRevisitItemPO item) {
        return RevisitItemDTO.builder()
                .id(item.getId())
                .sourceType(item.getSourceType())
                .sourceId(item.getSourceId())
                .reasonType(item.getReasonType())
                .activityCursor(item.getActivityCursor())
                .title(item.getTitle())
                .description(item.getDescription())
                .targetPath(item.getTargetPath())
                .status(item.getRevisitStatus())
                .dueAt(item.getDueAt())
                .snoozedUntil(item.getSnoozedUntil())
                .completedAt(item.getCompletedAt())
                .createTime(item.getCreateTime())
                .updateTime(item.getUpdateTime())
                .onSiteOnly(true)
                .externalPush(false)
                .advertising(false)
                .payment(false)
                .build();
    }

    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OPEN";
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "OPEN", "SNOOZED", "COMPLETED", "IGNORED" -> value;
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private static Long parseCursor(String raw) {
        if (raw == null || raw.isBlank() || "0".equals(raw.trim())) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static boolean safePath(String path) {
        return path != null
                && path.startsWith("/")
                && !path.startsWith("//")
                && !path.contains("://")
                && !path.contains("\\")
                && path.length() <= 255;
    }

    private static String cleanRequired(String value, int maxLength) {
        String normalized = cleanOptional(value, maxLength);
        if (normalized == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String cleanOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String cleanResourceKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 128 || !normalized.matches("[A-Z0-9_:-]+")) {
            return null;
        }
        return normalized;
    }

    private static String resourceKey(UserRevisitItemPO item) {
        if (item == null || item.getSourceType() == null || item.getSourceId() == null) {
            return null;
        }
        return cleanResourceKey(item.getSourceType() + ":" + item.getSourceId());
    }

    private static String postResourceKey(String targetPath) {
        if (targetPath == null || !targetPath.startsWith("/post/")) {
            return null;
        }
        String value = targetPath.substring("/post/".length()).split("[?#]", 2)[0];
        if (!value.matches("[1-9][0-9]*")) {
            return null;
        }
        return "POST:" + value;
    }

    private static RevisitReadStateDTO toReadState(UserRevisitItemPO item, String resourceKey) {
        return RevisitReadStateDTO.builder()
                .itemId(item.getId())
                .resourceKey(resourceKey)
                .status(item.getRevisitStatus())
                .targetPath(item.getTargetPath())
                .dueAt(item.getDueAt())
                .updateTime(item.getUpdateTime())
                .build();
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }
}
