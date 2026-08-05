package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDispositionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityReviewCandidateDispositionDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewCandidateDispositionMapper;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityReviewCandidateDispositionRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceTaskReadFacade;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.PublicContentFilter;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityReviewCandidateDispositionService {

    public static final String SOURCE_TYPE = "CHANNEL_HEALTH";
    public static final String STATE_DISMISSED = "DISMISSED";
    public static final String STATE_SNOOZED = "SNOOZED";
    public static final String STATE_CLEARED = "CLEARED";
    private static final int MAX_REVISION_KEYS = 100;
    private static final LocalDateTime EARLIEST_REVISION_WINDOW_START =
            LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final Set<String> ACTIONS = Set.of("DISMISS", "SNOOZE", "RESTORE");
    private static final Set<String> REASON_CODES = Set.of(
            "NOT_ACTIONABLE", "OUT_OF_SCOPE", "DUPLICATE", "WAIT_FOR_AUTHOR");

    private final ChannelQualityReviewCandidateDispositionMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminAuditService adminAuditService;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final PostFacade postFacade;
    private final PostContentRevisionQueryFacade postContentRevisionQuery;
    private final ContentMaintenanceTaskReadFacade maintenanceTaskReadFacade;

    public Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow>
    findActiveBySourceRevision(Collection<ContentMaintenanceTaskRevisionKey> keys) {
        requireTable();
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
        List<ChannelQualityReviewCandidateDispositionRow> rows =
                mapper.listActiveBySourceRevision(SOURCE_TYPE, requestedKeys);
        if (rows == null) {
            throw new IllegalStateException("candidate disposition source state is unavailable");
        }
        Map<ContentMaintenanceTaskRevisionKey, ChannelQualityReviewCandidateDispositionRow> result =
                new LinkedHashMap<>();
        for (ChannelQualityReviewCandidateDispositionRow row : rows) {
            ContentMaintenanceTaskRevisionKey key = requireValidSourceKey(row);
            if (!requestedKeys.contains(key)
                    || !isActiveState(row.getState())
                    || !validActiveState(row)) {
                throw new IllegalStateException("candidate disposition source state is invalid");
            }
            if (result.putIfAbsent(key, row) != null) {
                throw new IllegalStateException("candidate disposition source state is duplicated");
            }
        }
        return Map.copyOf(result);
    }

    @Transactional
    public ChannelQualityReviewCandidateDispositionDTO dispose(
            ChannelQualityReviewCandidateDispositionCmd cmd,
            Long operatorUid) {
        requireTable();
        requireOperator(operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int domain = requireDomain(cmd.getDomain());
        requireModerate(operatorUid, domain);
        long sourcePostId = requireId(cmd.getSourcePostId());
        long sourceRefId = requireId(cmd.getSourceRefId());
        String action = enumValue(cmd.getAction(), ACTIONS);
        DispositionChange change = changeFor(action, cmd.getReasonCode(), cmd.getSnoozeDays());
        ContentMaintenanceTaskRevisionKey key = new ContentMaintenanceTaskRevisionKey(sourcePostId, sourceRefId);

        requireCurrentPublicRevision(sourcePostId, sourceRefId, domain, operatorUid);
        Map<ContentMaintenanceTaskRevisionKey, String> taskStatuses =
                maintenanceTaskReadFacade.findTaskStatusesBySourceRevision(SOURCE_TYPE, List.of(key));
        if (taskStatuses == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (taskStatuses.containsKey(key)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }

        ChannelQualityReviewCandidateDispositionRow before =
                mapper.selectBySourceRevision(SOURCE_TYPE, sourcePostId, sourceRefId);
        LocalDateTime snoozedUntil = change.snoozeDays() == null
                ? null
                : LocalDateTime.now(ZoneOffset.UTC).plusDays(change.snoozeDays());
        int changed = mapper.upsert(
                idGenerator.nextId(),
                domain,
                SOURCE_TYPE,
                sourcePostId,
                sourceRefId,
                change.state(),
                change.reasonCode(),
                snoozedUntil,
                operatorUid);
        if (changed <= 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        ChannelQualityReviewCandidateDispositionRow after =
                mapper.selectBySourceRevision(SOURCE_TYPE, sourcePostId, sourceRefId);
        if (after == null || !key.equals(requireValidSourceKey(after))
                || !change.state().equals(after.getState())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        adminAuditService.recordRequired(
                operatorUid,
                "CHANNEL_HEALTH_CANDIDATE_DISPOSITION",
                "CHANNEL_HEALTH_CANDIDATE",
                sourcePostId + ":" + sourceRefId,
                auditView(before),
                auditView(after),
                "channel health candidate disposition");
        return toDto(after);
    }

    private void requireCurrentPublicRevision(long sourcePostId, long sourceRefId, int domain, Long operatorUid) {
        Map<Long, PostBriefDTO> posts;
        try {
            posts = postFacade.batchGetPosts(List.of(sourcePostId), null, false);
        } catch (RuntimeException ignored) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        PostBriefDTO post = posts == null ? null : posts.get(sourcePostId);
        if (post == null
                || !sourcePostIdEquals(post.getId(), sourcePostId)
                || !Integer.valueOf(domain).equals(post.getDomain())
                || !Post.isSupportedType(post.getPostType())
                || Boolean.TRUE.equals(post.getAnonymous())
                || !StringUtils.hasText(post.getTitle())
                || !PublicContentFilter.isDistributablePost(post)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        PostContentRevisionQueryResult result;
        try {
            result = postContentRevisionQuery.query(PostContentRevisionQuery.authorizedChannel(
                    operatorUid,
                    List.of(sourcePostId),
                    EARLIEST_REVISION_WINDOW_START,
                    List.of(domain)));
        } catch (RuntimeException ignored) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (result == null || !result.available() || result.items() == null || result.items().size() != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        PostContentRevisionSnapshot snapshot = result.items().get(0);
        if (snapshot == null
                || !Long.valueOf(sourcePostId).equals(snapshot.postId())
                || snapshot.status() != PostContentRevisionSnapshot.Status.FOUND
                || !snapshot.hasEffectiveRevision()
                || snapshot.effectivePublishedPostVersion() == null
                || snapshot.effectivePublishedPostVersion() <= 0
                || sourceRefId != snapshot.effectivePublishedPostVersion().longValue()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private void requireModerate(Long uid, int domain) {
        if (isGlobalModerator(uid)) {
            return;
        }
        if (!domainModeratorService.canModerateDomain(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isGlobalModerator(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private void requireTable() {
        try {
            if (mapper.tableExists() > 0) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Convert metadata failures to a stable dependency contract.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Channel quality candidate disposition migration is required");
    }

    private static DispositionChange changeFor(String action, String rawReasonCode, Integer snoozeDays) {
        if ("RESTORE".equals(action)) {
            if (StringUtils.hasText(rawReasonCode) || snoozeDays != null) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            return new DispositionChange(STATE_CLEARED, null, null);
        }
        String reasonCode = enumValue(rawReasonCode, REASON_CODES);
        if ("DISMISS".equals(action)) {
            if (snoozeDays != null) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            return new DispositionChange(STATE_DISMISSED, reasonCode, null);
        }
        if (snoozeDays == null || snoozeDays < 1 || snoozeDays > 30) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new DispositionChange(STATE_SNOOZED, reasonCode, snoozeDays);
    }

    private static boolean isActiveState(String state) {
        return STATE_DISMISSED.equals(state) || STATE_SNOOZED.equals(state);
    }

    private static boolean validActiveState(ChannelQualityReviewCandidateDispositionRow row) {
        if (row == null || !REASON_CODES.contains(row.getReasonCode())) {
            return false;
        }
        if (STATE_DISMISSED.equals(row.getState())) {
            return row.getSnoozedUntil() == null;
        }
        return row.getSnoozedUntil() != null
                && row.getSnoozedUntil().isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    private static ContentMaintenanceTaskRevisionKey requireValidSourceKey(
            ChannelQualityReviewCandidateDispositionRow row) {
        if (row == null || !SOURCE_TYPE.equals(row.getSourceType())) {
            throw new IllegalStateException("candidate disposition source type is invalid");
        }
        try {
            return new ContentMaintenanceTaskRevisionKey(row.getSourcePostId(), row.getSourceRefId());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("candidate disposition source key is invalid", ex);
        }
    }

    private static Map<String, Object> auditView(ChannelQualityReviewCandidateDispositionRow row) {
        if (row == null) {
            return Map.of();
        }
        return Map.of(
                "sourcePostId", row.getSourcePostId(),
                "sourceRefId", row.getSourceRefId(),
                "state", row.getState(),
                "reasonCode", row.getReasonCode() == null ? "" : row.getReasonCode(),
                "snoozedUntil", row.getSnoozedUntil() == null ? "" : row.getSnoozedUntil().toString());
    }

    private static ChannelQualityReviewCandidateDispositionDTO toDto(
            ChannelQualityReviewCandidateDispositionRow row) {
        return ChannelQualityReviewCandidateDispositionDTO.builder()
                .sourcePostId(row.getSourcePostId())
                .sourceRefId(row.getSourceRefId())
                .state(row.getState())
                .reasonCode(row.getReasonCode())
                .snoozedUntil(row.getSnoozedUntil() == null
                        ? null
                        : row.getSnoozedUntil().toInstant(ZoneOffset.UTC))
                .build();
    }

    private static boolean sourcePostIdEquals(Long value, long expected) {
        return value != null && value == expected;
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static void requireOperator(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static String enumValue(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private record DispositionChange(String state, String reasonCode, Integer snoozeDays) {
    }
}
