package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import com.offerlab.community.post.application.DomainConfigService;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.CollaborationContributionAcceptedEvent;
import com.offerlab.community.post.collaboration.api.CollaborationNeedFollowFacade;
import com.offerlab.community.post.collaboration.api.CollaborationNeedStateChangedEvent;
import com.offerlab.community.post.collaboration.api.CollaborationModels.*;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.*;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollaborationService implements CollaborationNeedFollowFacade {

    private static final int REQUIRED_TABLES = 18;
    private static final int REQUIRED_CRITICAL_COLUMNS = 28;
    private static final int REQUIRED_CLAIM_CYCLE_TABLES = 2;
    private static final int REQUIRED_CLAIM_CYCLE_COLUMNS = 32;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_FOLLOWER_PAGE_SIZE = 200;
    private static final int REPORT_DAILY_LIMIT = 20;
    private static final int APPEAL_DAILY_LIMIT = 5;
    private static final long CLAIM_STALE_AFTER_DAYS = 14;
    private static final long OFFICE_HOUR_CONFIRMATION_GRACE_HOURS = 72;
    private static final long OFFICE_HOUR_FEEDBACK_BLIND_HOURS = 72;
    private static final String EVENT_VISIBILITY_PUBLIC = "PUBLIC";
    private static final String EVENT_VISIBILITY_PARTICIPANTS = "PARTICIPANTS";
    private static final String MIGRATION = "db/migration/20260718_collab_need_lifecycle.sql";
    private static final String CLAIM_CYCLE_MIGRATION =
            "db/migration/20260719_collab_need_claim_cycle_revision.sql";
    private volatile boolean schemaReady;

    private final CollaborationMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final ContentModerationService moderationService;
    private final AdminPermissionService adminPermissionService;
    private final CommunityRoleAccessService communityRoleAccessService;
    private final DomainModeratorService domainModeratorService;
    private final DomainConfigService domainConfigService;
    private final EventPublisher eventPublisher;

    public PageResult<NeedDTO> listNeeds(Integer domain, String status, Long viewerUid, long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        String activeStatus = optionalStatus(status,
                "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED", "MERGED");
        int pageSize = pageSize(size);
        long total = mapper.countNeeds(activeDomain, activeStatus);
        List<NeedRow> rows = mapper.listNeeds(activeDomain, activeStatus, viewerUid, safeCursor(cursor), pageSize + 1);
        Map<Integer, Boolean> moderation = new HashMap<>();
        PageResult<NeedDTO> result = page(rows, pageSize,
                row -> toNeed(row, viewerUid,
                        canManageCached(row.getCreatorUid(), viewerUid, row.getDomain(), moderation)),
                NeedRow::getId);
        result.setTotal(total);
        return result;
    }

    public PageResult<NeedDTO> listMyClaimedNeeds(Long uid, String status, long cursor, int size) {
        requireSchema();
        String activeStatus = optionalStatus(status,
                "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED", "MERGED");
        int pageSize = pageSize(size);
        long total = mapper.countNeedsByClaimant(uid, activeStatus);
        List<NeedRow> rows = mapper.listNeedsByClaimant(uid, activeStatus, safeCursor(cursor), pageSize + 1);
        Map<Integer, Boolean> moderation = new HashMap<>();
        PageResult<NeedDTO> result = page(rows, pageSize,
                row -> toNeed(row, uid,
                        canManageCached(row.getCreatorUid(), uid, row.getDomain(), moderation)),
                NeedRow::getId);
        result.setTotal(total);
        return result;
    }

    public PageResult<NeedDTO> listMyCreatedNeeds(Long uid, String status, long cursor, int size) {
        requireSchema();
        String activeStatus = optionalStatus(status,
                "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED", "MERGED");
        int pageSize = pageSize(size);
        long total = mapper.countNeedsByCreator(uid, activeStatus);
        List<NeedRow> rows = mapper.listNeedsByCreator(uid, activeStatus, safeCursor(cursor), pageSize + 1);
        PageResult<NeedDTO> result =
                page(rows, pageSize, row -> toNeed(row, uid, true), NeedRow::getId);
        result.setTotal(total);
        return result;
    }

    public PageResult<NeedDTO> listMyFollowedNeeds(Long uid, String status, long cursor, int size) {
        requireSchema();
        String activeStatus = optionalStatus(status,
                "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED", "MERGED");
        int pageSize = pageSize(size);
        List<NeedRow> rows = mapper.listFollowedNeeds(uid, activeStatus, safeCursor(cursor), pageSize + 1);
        Map<Integer, Boolean> moderation = new HashMap<>();
        return page(rows, pageSize,
                row -> toNeed(row, uid,
                        canManageCached(row.getCreatorUid(), uid, row.getDomain(), moderation)),
                NeedRow::getFollowId);
    }

    @Override
    public PageResult<Long> listActiveFollowerUids(Long needId, long cursor, int size) {
        requireSchema();
        int pageSize = followerPageSize(size);
        List<NeedFollowRow> rows = mapper.listActiveNeedFollowers(
                requireId(needId), safeCursor(cursor), pageSize + 1);
        List<NeedFollowRow> safe = rows == null ? List.of() : rows;
        boolean hasMore = safe.size() > pageSize;
        List<NeedFollowRow> visible = safe.stream().limit(pageSize).toList();
        List<Long> uids = visible.stream().map(NeedFollowRow::getUid).toList();
        String next = hasMore && !visible.isEmpty()
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        return PageResult.of(uids, next, hasMore);
    }

    public PageResult<NeedDTO> listNeedReviewQueue(Integer domain, Long uid, long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        if (activeDomain == null) {
            adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        } else {
            domainModeratorService.requireModerateDomain(uid, activeDomain);
        }
        int pageSize = pageSize(size);
        List<NeedRow> rows = mapper.listNeedReviewQueue(
                activeDomain, uid, safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, row -> toNeed(row, uid, true), NeedRow::getId);
    }

    public NeedDTO getNeed(Long id, Long viewerUid) {
        requireSchema();
        return toNeed(requireNeed(mapper.selectNeed(requireId(id), viewerUid)), viewerUid);
    }

    public PageResult<NeedEventDTO> listNeedEvents(Long id, Long viewerUid, long cursor, int size) {
        requireSchema();
        NeedRow need = requireNeed(mapper.selectNeed(requireId(id), viewerUid));
        boolean canManage = Objects.equals(need.getCreatorUid(), viewerUid)
                || canModerate(viewerUid, need.getDomain());
        int pageSize = pageSize(size);
        List<NeedEventRow> rows = mapper.listNeedEvents(
                need.getId(), viewerUid, canManage ? 1 : 0,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize,
                this::toNeedEvent,
                NeedEventRow::getId);
    }

    @Transactional
    public NeedDTO createNeed(NeedCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        Integer domain = requireDomain(cmd.getDomain());
        requireCreationRisk(domain, cmd.getRiskAcknowledged(), uid);
        String sourceType = enumValue(cmd.getSourceType(), "COMMUNITY", "POST", "TOPIC", "ACTIVITY", "EXTERNAL");
        String contentFormat = enumValue(cmd.getContentFormat(), "ARTICLE", "QUESTION", "GUIDE", "CHECKLIST", "RESOURCE");
        Long id = idGenerator.nextId();
        String title = required(cmd.getTitle(), 120);
        String description = required(cmd.getDescription(), 2000);
        String criteria = clean(cmd.getAcceptanceCriteria(), 1000);
        moderate(uid, "CONTENT_NEED", id, title, description, criteria);
        mapper.insertNeed(id, uid, domain, sourceType, positiveOrNull(cmd.getSourceRefId()),
                contentFormat, title, description, criteria, flag(cmd.getRiskAcknowledged()));
        appendNeedEvent(id, "CREATED", uid, uid, null, domain,
                null, "OPEN", null, null, null, EVENT_VISIBILITY_PUBLIC, false);
        return getNeed(id, uid);
    }

    /**
     * Creates or reuses a need generated by an approved search-content gap.
     * This is intentionally not exposed through the public collaboration controller.
     */
    @Transactional
    public NeedDTO createOrGetSearchGapNeed(Long gapId, SearchGapNeedCreateCmd cmd, Long operatorUid) {
        requireSchema();
        Long sourceRefId = requireId(gapId);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Integer domain = requireDomain(cmd.getDomain());
        domainModeratorService.requireModerateDomain(operatorUid, domain);
        requireCreationRisk(domain, cmd.getRiskAcknowledged(), operatorUid);

        NeedRow existing = mapper.selectNeedBySource("SEARCH_GAP", sourceRefId);
        if (existing != null) {
            if (Objects.equals(existing.getHidden(), 1)) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "search gap is already linked to a hidden content need");
            }
            return toNeed(existing, operatorUid);
        }

        String format = StringUtils.hasText(cmd.getContentFormat())
                ? enumValue(cmd.getContentFormat(), "ARTICLE", "QUESTION", "GUIDE", "CHECKLIST", "RESOURCE")
                : "GUIDE";
        String title = required(cmd.getTitle(), 120);
        String description = required(cmd.getDescription(), 2000);
        String criteria = clean(cmd.getAcceptanceCriteria(), 1000);
        Long needId = idGenerator.nextId();
        moderate(operatorUid, "SEARCH_GAP_NEED", needId, title, description, criteria);
        mapper.insertNeed(needId, operatorUid, domain, "SEARCH_GAP", sourceRefId, format,
                title, description, criteria, flag(cmd.getRiskAcknowledged()));
        appendNeedEvent(needId, "CREATED", operatorUid, operatorUid, null, domain,
                null, "OPEN", null, null, null, EVENT_VISIBILITY_PUBLIC, false);
        return getNeed(needId, operatorUid);
    }

    @Transactional
    public NeedDTO followNeed(Long id, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        requireOpenForParticipation(need.getStatus());
        Long followId = idGenerator.nextId();
        int changed = mapper.reactivateNeedFollow(followId, need.getId(), uid);
        if (changed == 0) {
            changed = mapper.insertNeedFollow(followId, need.getId(), uid);
        }
        if (changed == 1) {
            mapper.incrementNeedFollowerCount(need.getId(), 1);
        }
        return getNeed(need.getId(), uid);
    }

    @Transactional
    public NeedDTO unfollowNeed(Long id, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        if (mapper.unfollowNeed(need.getId(), uid) == 1) {
            mapper.incrementNeedFollowerCount(need.getId(), -1);
        }
        return getNeed(need.getId(), uid);
    }

    @Transactional
    public NeedDTO claimNeed(Long id, NeedClaimCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        requireRisk(need.getDomain(), cmd == null ? null : cmd.getRiskAcknowledged());
        if (Objects.equals(need.getCreatorUid(), uid)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(), "Creator cannot claim their own content need");
        }
        if (mapper.claimNeed(need.getId(), uid) != 1) {
            throw invalidState();
        }
        Long cycleId = idGenerator.nextId();
        if (mapper.insertNeedClaimCycle(cycleId, need.getId(), uid) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to record collaboration claim cycle");
        }
        appendNeedEvent(need.getId(), "CLAIMED", uid, need.getCreatorUid(), uid, need.getDomain(),
                need.getStatus(), "CLAIMED", null, null, null, EVENT_VISIBILITY_PUBLIC, true);
        return getNeed(need.getId(), uid);
    }

    @Transactional
    public NeedDTO mergeNeed(Long id, NeedMergeCmd cmd, Long uid) {
        requireSchema();
        Long sourceId = requireId(id);
        Long targetId = requireId(cmd == null ? null : cmd.getTargetNeedId());
        if (sourceId.equals(targetId)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long firstId = Math.min(sourceId, targetId);
        Long secondId = Math.max(sourceId, targetId);
        NeedRow first = requireNeed(mapper.lockNeed(firstId));
        NeedRow second = requireNeed(mapper.lockNeed(secondId));
        NeedRow source = sourceId.equals(firstId) ? first : second;
        NeedRow target = targetId.equals(firstId) ? first : second;
        requireManage(source.getCreatorUid(), source.getDomain(), uid);
        if (!Objects.equals(source.getDomain(), target.getDomain())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireOpenForParticipation(target.getStatus());
        if (!"OPEN".equals(source.getStatus()) || mapper.mergeNeed(source.getId(), target.getId()) != 1) {
            throw invalidState();
        }
        appendNeedEvent(source.getId(), "MERGED", uid, source.getCreatorUid(),
                source.getClaimedByUid(), source.getDomain(), source.getStatus(), "MERGED",
                "NEED", target.getId(), clean(cmd.getNote(), 500), EVENT_VISIBILITY_PUBLIC, true);
        return getNeed(source.getId(), uid);
    }

    @Transactional
    public NeedDTO fulfillNeed(Long id, NeedCompleteCmd cmd, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        if (!Objects.equals(need.getCreatorUid(), uid) && !canModerate(uid, need.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"OPEN".equals(need.getStatus()) && !"CLAIMED".equals(need.getStatus())) {
            throw invalidState();
        }
        NeedResolution resolution = resolveNeedResolution(need, cmd, uid);
        if (need.getClaimedByUid() != null
                && !Objects.equals(resolution.contributorUid(), need.getClaimedByUid())) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "claimed content needs must be fulfilled by the claimant's contribution");
        }
        if (mapper.completeNeed(need.getId(), resolution.type(), resolution.id(),
                resolution.compatibilityPostId()) != 1) {
            throw invalidState();
        }
        if (need.getClaimedByUid() != null) {
            endCurrentClaimCycle(need, "COMPLETED", "FULFILLED");
        }
        appendNeedEvent(need.getId(), "COMPLETED", uid, need.getCreatorUid(),
                need.getClaimedByUid(), need.getDomain(), need.getStatus(), "COMPLETED",
                resolution.type(), resolution.id(), clean(cmd == null ? null : cmd.getNote(), 500),
                EVENT_VISIBILITY_PUBLIC, true);
        if (!Objects.equals(resolution.contributorUid(), uid)
                && !Objects.equals(resolution.contributorUid(), need.getCreatorUid())) {
            publishNeedFulfilled(resolution.contributorUid(), need, resolution, "NEED_FULFILLED",
                    "COLLAB:NEED:" + need.getId());
        } else {
            // Keep the fulfillment callback observable without turning a creator/admin action into a reward event.
            publishNeedFulfilled(null, need, resolution, "NEED_FULFILLED_SYNC",
                    "COLLAB:NEED_SYNC:" + need.getId());
        }
        return getNeed(need.getId(), uid);
    }

    @Transactional
    public NeedDTO closeNeed(Long id, CloseCmd cmd, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        requireManage(need.getCreatorUid(), need.getDomain(), uid);
        String reason = required(cmd == null ? null : cmd.getNote(), 500);
        NeedClaimCycleRow cycle = ensureCurrentClaimCycle(need);
        NeedRevisionRow revision = currentSubmittedRevisionOrHandoff(cycle, need);
        if (mapper.closeNeed(need.getId(), reason) != 1) {
            throw invalidState();
        }
        if (revision != null) {
            decideRevision(revision, "CLOSED", uid, reason);
        }
        if (cycle != null) {
            endClaimCycle(cycle, "CLOSED", reason);
        }
        appendNeedEvent(need.getId(), "CLOSED", uid, need.getCreatorUid(),
                need.getClaimedByUid(), need.getDomain(), need.getStatus(), "CLOSED",
                null, null, reason, EVENT_VISIBILITY_PUBLIC, true);
        return getNeed(need.getId(), uid);
    }

    /**
     * Claimant submits their produced content for creator acceptance.
     * CLAIMED -> SUBMITTED. Only the claimant may submit, and the resolution's contributor must be the claimant.
     */
    @Transactional
    public NeedDTO submitNeed(Long id, NeedSubmitCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        if (!Objects.equals(need.getClaimedByUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "only the claimant can submit content for this need");
        }
        if (!"CLAIMED".equals(need.getStatus())) {
            throw invalidState();
        }
        NeedClaimCycleRow cycle = ensureCurrentClaimCycle(need);
        NeedResolution resolution = resolveNeedResolution(need, toCompleteCmd(cmd), uid);
        if (!Objects.equals(resolution.contributorUid(), uid)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "submitted content must be authored or owned by the claimant");
        }
        String submissionNote = clean(cmd == null ? null : cmd.getNote(), 1000);
        if (mapper.submitNeed(need.getId(), uid, resolution.type(), resolution.id(), submissionNote) != 1) {
            throw invalidState();
        }
        if (cycle != null) {
            if (mapper.insertNeedRevision(idGenerator.nextId(), need.getId(), cycle.getId(),
                    cycle.getCycleNo(), uid, resolution.type(), resolution.id(),
                    resolution.compatibilityPostId(), submissionNote,
                    EVENT_VISIBILITY_PARTICIPANTS, "SUBMISSION", null) != 1
                    || mapper.touchNeedClaimCycle(cycle.getId()) != 1) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "failed to record collaboration submission revision");
            }
        }
        appendNeedEvent(need.getId(), "SUBMITTED", uid, need.getCreatorUid(),
                need.getClaimedByUid(), need.getDomain(), need.getStatus(), "SUBMITTED",
                resolution.type(), resolution.id(), submissionNote, EVENT_VISIBILITY_PARTICIPANTS, true);
        return getNeed(need.getId(), uid);
    }

    /**
     * Creator/moderator accepts the submitted content. SUBMITTED -> COMPLETED.
     * Reuses the existing resolution + completion + reward path so acceptance is a
     * third-party contribution (contributorUid = claimant, actor = creator) and rewards fire normally.
     */
    @Transactional
    public NeedDTO acceptNeed(Long id, NeedAcceptCmd cmd, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        requireManage(need.getCreatorUid(), need.getDomain(), uid);
        if (!"SUBMITTED".equals(need.getStatus())) {
            throw invalidState();
        }
        Long claimant = need.getClaimedByUid();
        if (claimant == null || !Objects.equals(need.getSubmittedByUid(), claimant)
                || need.getSubmissionResolutionType() == null
                || need.getSubmissionResolutionId() == null) {
            throw invalidState();
        }
        requireIndependentNeedReviewer(claimant, uid);
        NeedClaimCycleRow cycle = ensureCurrentClaimCycle(need);
        NeedRevisionRow revision = currentSubmittedRevisionOrHandoff(cycle, need);
        // Re-resolve the claimant's stored submission with the claimant as the acting uid,
        // so series-ownership checks resolve against the claimant, not the accepting creator.
        NeedResolution resolution = resolveNeedResolution(need, toCompleteCmd(need), claimant);
        if (!Objects.equals(resolution.contributorUid(), claimant)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "submitted content must still be authored or owned by the claimant");
        }
        if (mapper.completeNeed(need.getId(), resolution.type(), resolution.id(),
                resolution.compatibilityPostId()) != 1) {
            throw invalidState();
        }
        String acceptanceNote = clean(cmd == null ? null : cmd.getNote(), 500);
        if (revision != null) {
            decideRevision(revision, "ACCEPTED", uid, acceptanceNote);
        }
        if (cycle != null) {
            endClaimCycle(cycle, "COMPLETED", acceptanceNote);
        }
        appendNeedEvent(need.getId(), "ACCEPTED", uid, need.getCreatorUid(), claimant,
                need.getDomain(), need.getStatus(), "COMPLETED", resolution.type(), resolution.id(),
                acceptanceNote, EVENT_VISIBILITY_PUBLIC, true);
        if (!Objects.equals(resolution.contributorUid(), uid)
                && !Objects.equals(resolution.contributorUid(), need.getCreatorUid())) {
            publishNeedFulfilled(resolution.contributorUid(), need, resolution, "NEED_FULFILLED",
                    "COLLAB:NEED:" + need.getId());
        } else {
            publishNeedFulfilled(null, need, resolution, "NEED_FULFILLED_SYNC",
                    "COLLAB:NEED_SYNC:" + need.getId());
        }
        return getNeed(need.getId(), uid);
    }

    /**
     * Creator/moderator sends the submission back for revision. SUBMITTED -> CLAIMED.
     * The stored submission is kept so the claimant can see it and iterate.
     */
    @Transactional
    public NeedDTO rejectNeed(Long id, NeedRejectCmd cmd, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        requireManage(need.getCreatorUid(), need.getDomain(), uid);
        if (!"SUBMITTED".equals(need.getStatus())) {
            throw invalidState();
        }
        requireIndependentNeedReviewer(need.getClaimedByUid(), uid);
        String reason = required(cmd == null ? null : cmd.getReason(), 500);
        NeedClaimCycleRow cycle = ensureCurrentClaimCycle(need);
        NeedRevisionRow revision = currentSubmittedRevisionOrHandoff(cycle, need);
        if (mapper.revertSubmittedToClaimed(need.getId(), null, reason) != 1) {
            throw invalidState();
        }
        if (revision != null) {
            decideRevision(revision, "REJECTED", uid, reason);
        }
        if (cycle != null) {
            touchClaimCycle(cycle);
        }
        appendNeedEvent(need.getId(), "REJECTED", uid, need.getCreatorUid(),
                need.getClaimedByUid(), need.getDomain(), need.getStatus(), "CLAIMED",
                need.getSubmissionResolutionType(), need.getSubmissionResolutionId(), reason,
                EVENT_VISIBILITY_PARTICIPANTS, true);
        return getNeed(need.getId(), uid);
    }

    /**
     * Claimant withdraws their own not-yet-accepted submission. SUBMITTED -> CLAIMED.
     * The stored submission is kept so the claimant can amend and resubmit; no reject reason is written.
     */
    @Transactional
    public NeedDTO withdrawNeed(Long id, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        if (!Objects.equals(need.getClaimedByUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "only the claimant can withdraw this submission");
        }
        if (!"SUBMITTED".equals(need.getStatus())
                || !Objects.equals(need.getSubmittedByUid(), uid)) {
            throw invalidState();
        }
        NeedClaimCycleRow cycle = ensureCurrentClaimCycle(need);
        NeedRevisionRow revision = currentSubmittedRevisionOrHandoff(cycle, need);
        if (mapper.revertSubmittedToClaimed(need.getId(), uid, null) != 1) {
            throw invalidState();
        }
        if (revision != null) {
            decideRevision(revision, "WITHDRAWN", uid, null);
        }
        if (cycle != null) {
            touchClaimCycle(cycle);
        }
        appendNeedEvent(need.getId(), "WITHDRAWN", uid, need.getCreatorUid(),
                need.getClaimedByUid(), need.getDomain(), need.getStatus(), "CLAIMED",
                need.getSubmissionResolutionType(), need.getSubmissionResolutionId(), null,
                EVENT_VISIBILITY_PARTICIPANTS, true);
        return getNeed(need.getId(), uid);
    }

    @Transactional
    public NeedDTO releaseNeed(Long id, NeedReleaseCmd cmd, Long uid) {
        requireSchema();
        NeedRow need = requireNeed(mapper.lockNeed(requireId(id)));
        if (!Objects.equals(need.getClaimedByUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "only the current claimant can release this content need");
        }
        if (!"CLAIMED".equals(need.getStatus())) {
            throw invalidState();
        }
        String note = clean(cmd == null ? null : cmd.getNote(), 500);
        NeedClaimCycleRow cycle = ensureCurrentClaimCycle(need);
        if (mapper.releaseNeed(need.getId(), uid) != 1) {
            throw invalidState();
        }
        if (cycle != null) {
            endClaimCycle(cycle, "RELEASED", note);
        }
        appendNeedEvent(need.getId(), "RELEASED", uid, need.getCreatorUid(),
                need.getClaimedByUid(), need.getDomain(), need.getStatus(), "OPEN",
                null, null, note, EVENT_VISIBILITY_PUBLIC, true);
        return getNeed(need.getId(), uid);
    }

    private NeedCompleteCmd toCompleteCmd(NeedSubmitCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        NeedCompleteCmd complete = new NeedCompleteCmd();
        complete.setResolutionType(cmd.getResolutionType());
        complete.setResolutionId(cmd.getResolutionId());
        complete.setResolutionPostId(cmd.getResolutionPostId());
        complete.setNote(cmd.getNote());
        return complete;
    }

    private NeedCompleteCmd toCompleteCmd(NeedRow need) {
        NeedCompleteCmd complete = new NeedCompleteCmd();
        complete.setResolutionType(need.getSubmissionResolutionType());
        complete.setResolutionId(need.getSubmissionResolutionId());
        if ("POST".equals(need.getSubmissionResolutionType())
                || "QUESTION".equals(need.getSubmissionResolutionType())) {
            complete.setResolutionPostId(need.getSubmissionResolutionId());
        }
        return complete;
    }

    public PageResult<SeriesDTO> listSeries(Integer domain, String status, Long viewerUid, long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        String activeStatus = optionalStatus(status, "OPEN", "CLOSED");
        int pageSize = pageSize(size);
        List<SeriesRow> rows = mapper.listSeries(activeDomain, activeStatus, viewerUid, safeCursor(cursor), pageSize + 1);
        Map<Integer, Boolean> moderation = new HashMap<>();
        return page(rows, pageSize,
                row -> toSeries(row, viewerUid,
                        canManageCached(row.getOwnerUid(), viewerUid, row.getDomain(), moderation)),
                SeriesRow::getId);
    }

    public SeriesDTO getSeries(Long id, Long viewerUid) {
        requireSchema();
        return toSeries(requireSeries(mapper.selectSeries(requireId(id), viewerUid)), viewerUid);
    }

    @Transactional
    public SeriesDTO createSeries(SeriesCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        Integer domain = requireDomain(cmd.getDomain());
        requireCreationRisk(domain, cmd.getRiskAcknowledged(), uid);
        requireAnyCommunityRole(uid, domain, "COLLABORATION_INITIATOR");
        Long id = idGenerator.nextId();
        String title = required(cmd.getTitle(), 120);
        String description = required(cmd.getDescription(), 2000);
        String instructions = clean(cmd.getSubmissionInstructions(), 1000);
        moderate(uid, "COLLAB_SERIES", id, title, description, instructions);
        mapper.insertSeries(id, uid, domain, title, description, instructions, flag(cmd.getRiskAcknowledged()));
        mapper.upsertSeriesMember(idGenerator.nextId(), id, uid, "OWNER", uid);
        return getSeries(id, uid);
    }

    @Transactional
    public SeriesDTO addSeriesMember(Long seriesId, SeriesMemberCmd cmd, Long uid) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.lockSeries(requireId(seriesId)));
        requireSeriesOwner(series, uid);
        requireSeriesOpen(series);
        Long memberUid = requireId(cmd.getUid());
        if (Objects.equals(memberUid, series.getOwnerUid())) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "series owner membership cannot be overwritten");
        }
        if (mapper.userExists(memberUid) <= 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        String role = enumValue(cmd.getRole(), "EDITOR", "CONTRIBUTOR");
        mapper.upsertSeriesMember(idGenerator.nextId(), series.getId(), memberUid, role, uid);
        mapper.refreshSeriesMemberCount(series.getId());
        return getSeries(series.getId(), uid);
    }

    @Transactional
    public SeriesDTO removeSeriesMember(Long seriesId, Long memberUid, Long uid) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.lockSeries(requireId(seriesId)));
        requireSeriesOwner(series, uid);
        requireSeriesOpen(series);
        if (Objects.equals(memberUid, series.getOwnerUid())) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "series owner cannot be removed");
        }
        if (mapper.removeSeriesMember(series.getId(), requireId(memberUid), uid) != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        mapper.refreshSeriesMemberCount(series.getId());
        return getSeries(series.getId(), uid);
    }

    @Transactional
    public SeriesDTO exitSeries(Long seriesId, Long uid) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.lockSeries(requireId(seriesId)));
        requireSeriesOpen(series);
        if (mapper.exitSeries(series.getId(), uid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        mapper.refreshSeriesMemberCount(series.getId());
        return getSeries(series.getId(), uid);
    }

    public PageResult<SeriesMemberDTO> listSeriesMembers(Long seriesId, Long viewerUid, long cursor, int size) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.selectSeries(requireId(seriesId), viewerUid));
        boolean reviewer = canReviewSeries(series.getId(), viewerUid)
                || canModerate(viewerUid, series.getDomain());
        int pageSize = pageSize(size);
        List<SeriesMemberRow> rows = mapper.listSeriesMembers(seriesId, safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, reviewer ? this::toSeriesMember : this::toPublicSeriesMember,
                SeriesMemberRow::getId);
    }

    @Transactional
    public SubmissionDTO submitSeriesPost(Long seriesId, PostSubmissionCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        SeriesRow series = requireSeries(mapper.lockSeries(requireId(seriesId)));
        if (!"OPEN".equals(series.getStatus()) || mapper.selectSeriesRole(series.getId(), uid) == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        requireRisk(series.getDomain(), cmd.getRiskAcknowledged());
        PostRefRow post = requireOwnedPublicPost(cmd.getPostId(), uid);
        requireMatchingDomain(series.getDomain(), post.getDomain());
        Long id = idGenerator.nextId();
        try {
            mapper.insertSeriesSubmission(id, series.getId(), post.getId(), uid, clean(cmd.getNote(), 1000));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        return toSubmission(requireSubmission(mapper.lockSeriesSubmission(id)));
    }

    public PageResult<SubmissionDTO> listSeriesSubmissions(Long seriesId, String status,
                                                           Long viewerUid, long cursor, int size) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.selectSeries(requireId(seriesId), viewerUid));
        boolean reviewer = canReviewSeries(series.getId(), viewerUid)
                || canModerate(viewerUid, series.getDomain());
        String activeStatus = reviewer
                ? optionalStatus(status, "PENDING", "APPROVED", "REJECTED")
                : "APPROVED";
        int pageSize = pageSize(size);
        List<SubmissionRow> rows = mapper.listSeriesSubmissions(series.getId(), activeStatus, reviewer ? 0 : 1,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, reviewer ? this::toSubmission : this::toPublicSubmission,
                SubmissionRow::getId);
    }

    @Transactional
    public SubmissionDTO decideSeriesSubmission(Long seriesId, Long submissionId, ReviewCmd cmd, Long uid) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.lockSeries(requireId(seriesId)));
        requireSeriesReviewer(series.getId(), uid);
        requireSeriesOpen(series);
        SubmissionRow submission = requireSubmission(mapper.lockSeriesSubmission(requireId(submissionId)));
        if (!series.getId().equals(submission.getParentId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (Objects.equals(submission.getSubmitterUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "contributors cannot review their own submission");
        }
        String decision = decision(cmd.getDecision());
        if ("APPROVED".equals(decision)) {
            requirePublicPost(submission.getPostId());
        }
        if (mapper.reviewSeriesSubmission(submission.getId(), decision, uid, clean(cmd.getNote(), 1000)) != 1) {
            throw invalidState();
        }
        if ("APPROVED".equals(decision)) {
            mapper.insertSeriesContribution(idGenerator.nextId(), series.getId(), submission.getPostId(),
                    submission.getSubmitterUid(), submission.getId(), uid);
            publishContribution(submission.getSubmitterUid(), series.getDomain(), "SERIES_SUBMISSION_ACCEPTED",
                    submission.getId(), submission.getPostId(),
                    "COLLAB:SERIES_SUBMISSION:" + submission.getId());
        }
        mapper.refreshSeriesPostCount(series.getId());
        return toSubmission(requireSubmission(mapper.lockSeriesSubmission(submission.getId())));
    }

    @Transactional
    public SeriesDTO closeSeries(Long seriesId, Long uid) {
        requireSchema();
        SeriesRow series = requireSeries(mapper.lockSeries(requireId(seriesId)));
        requireManage(series.getOwnerUid(), series.getDomain(), uid);
        requireSeriesOpen(series);
        if (mapper.closeSeries(series.getId()) != 1) {
            throw invalidState();
        }
        mapper.rejectPendingSeriesSubmissions(series.getId(), uid, "Series closed before review");
        return getSeries(series.getId(), uid);
    }

    public PageResult<ActivityDTO> listActivities(Integer domain, String status, Long viewerUid,
                                                   long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        String activeStatus = optionalStatus(status, "DRAFT", "OPEN", "REVIEWING", "SUMMARIZED", "ARCHIVED");
        if (viewerUid == null && (activeStatus == null || "DRAFT".equals(activeStatus))) {
            activeStatus = activeStatus == null ? "OPEN" : activeStatus;
        }
        int pageSize = pageSize(size);
        Map<Integer, Boolean> moderation = new HashMap<>();
        List<ActivityRow> rows = mapper.listActivities(activeDomain, activeStatus, viewerUid,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize,
                row -> toActivity(row, viewerUid,
                        canManageCached(row.getOwnerUid(), viewerUid, row.getDomain(), moderation)),
                ActivityRow::getId);
    }

    public ActivityDTO getActivity(Long id, Long viewerUid) {
        requireSchema();
        ActivityRow row = requireActivity(mapper.selectActivity(requireId(id)));
        if ("DRAFT".equals(row.getStatus()) && !Objects.equals(row.getOwnerUid(), viewerUid)
                && !canModerate(viewerUid, row.getDomain())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toActivity(row, viewerUid);
    }

    @Transactional
    public ActivityDTO createActivity(ActivityCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        Integer domain = requireDomain(cmd.getDomain());
        requireCreationRisk(domain, cmd.getRiskAcknowledged(), uid);
        requireAnyCommunityRole(uid, domain,
                "EXPANDED_CAMPAIGN_CONTRIBUTOR", "CAMPAIGN_CONTRIBUTOR");
        if (cmd.getStartsAt() != null && cmd.getEndsAt() != null && !cmd.getEndsAt().isAfter(cmd.getStartsAt())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long id = idGenerator.nextId();
        String title = required(cmd.getTitle(), 120);
        String description = required(cmd.getDescription(), 2000);
        String rules = clean(cmd.getSubmissionRule(), 1000);
        String type = enumValue(cmd.getActivityType(), "OPEN_CALL", "SPRINT", "CHALLENGE", "RESEARCH", "CURATION");
        moderate(uid, "COLLAB_ACTIVITY", id, title, description, rules);
        mapper.insertActivity(id, uid, domain, type, title, description, rules,
                cmd.getStartsAt(), cmd.getEndsAt(), flag(cmd.getRiskAcknowledged()));
        return getActivity(id, uid);
    }

    @Transactional
    public ActivityDTO updateActivityStatus(Long id, ActivityStatusCmd cmd, Long uid) {
        requireSchema();
        ActivityRow activity = requireActivity(mapper.lockActivity(requireId(id)));
        requireManage(activity.getOwnerUid(), activity.getDomain(), uid);
        String target = enumValue(cmd.getStatus(), "OPEN", "REVIEWING", "ARCHIVED");
        if (!validActivityTransition(activity.getStatus(), target)
                || mapper.updateActivityStatus(activity.getId(), target) != 1) {
            throw invalidState();
        }
        if ("ARCHIVED".equals(target)) {
            mapper.rejectPendingActivitySubmissions(activity.getId(), uid, "Activity archived before review");
        }
        return getActivity(activity.getId(), uid);
    }

    @Transactional
    public ActivityDTO summarizeActivity(Long id, ActivitySummaryCmd cmd, Long uid) {
        requireSchema();
        ActivityRow activity = requireActivity(mapper.lockActivity(requireId(id)));
        requireManage(activity.getOwnerUid(), activity.getDomain(), uid);
        if (!"REVIEWING".equals(activity.getStatus())) {
            throw invalidState();
        }
        if (mapper.countPendingActivitySubmissions(activity.getId()) > 0) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "all activity submissions must be reviewed before publishing a summary");
        }
        String summary = required(cmd.getResultSummary(), 4000);
        moderate(uid, "COLLAB_ACTIVITY_SUMMARY", activity.getId(), summary);
        if (mapper.summarizeActivity(activity.getId(), summary) != 1) {
            throw invalidState();
        }
        return getActivity(activity.getId(), uid);
    }

    @Transactional
    public SubmissionDTO submitActivityPost(Long activityId, PostSubmissionCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        ActivityRow activity = requireActivity(mapper.lockActivity(requireId(activityId)));
        if (!"OPEN".equals(activity.getStatus()) || !withinActivityWindow(activity)) {
            throw invalidState();
        }
        requireRisk(activity.getDomain(), cmd.getRiskAcknowledged());
        PostRefRow post = requireOwnedPublicPost(cmd.getPostId(), uid);
        requireMatchingDomain(activity.getDomain(), post.getDomain());
        Long id = idGenerator.nextId();
        try {
            mapper.insertActivitySubmission(id, activity.getId(), post.getId(), uid, clean(cmd.getNote(), 1000));
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        return toSubmission(requireSubmission(mapper.lockActivitySubmission(id)));
    }

    public PageResult<SubmissionDTO> listActivitySubmissions(Long activityId, String status,
                                                             Long viewerUid, long cursor, int size) {
        requireSchema();
        ActivityRow activity = requireActivity(mapper.selectActivity(requireId(activityId)));
        if ("DRAFT".equals(activity.getStatus())
                && !Objects.equals(activity.getOwnerUid(), viewerUid)
                && !canModerate(viewerUid, activity.getDomain())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean reviewer = Objects.equals(activity.getOwnerUid(), viewerUid) || canModerate(viewerUid, activity.getDomain());
        String activeStatus = reviewer
                ? optionalStatus(status, "PENDING", "APPROVED", "REJECTED")
                : "APPROVED";
        int pageSize = pageSize(size);
        List<SubmissionRow> rows = mapper.listActivitySubmissions(activity.getId(), activeStatus, reviewer ? 0 : 1,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, reviewer ? this::toSubmission : this::toPublicSubmission,
                SubmissionRow::getId);
    }

    @Transactional
    public SubmissionDTO decideActivitySubmission(Long activityId, Long submissionId, ReviewCmd cmd, Long uid) {
        requireSchema();
        ActivityRow activity = requireActivity(mapper.lockActivity(requireId(activityId)));
        requireManage(activity.getOwnerUid(), activity.getDomain(), uid);
        if (!"REVIEWING".equals(activity.getStatus())) {
            throw invalidState();
        }
        SubmissionRow submission = requireSubmission(mapper.lockActivitySubmission(requireId(submissionId)));
        if (!activity.getId().equals(submission.getParentId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (Objects.equals(submission.getSubmitterUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "contributors cannot review their own submission");
        }
        String decision = decision(cmd.getDecision());
        if ("APPROVED".equals(decision)) {
            requirePublicPost(submission.getPostId());
        }
        if (mapper.reviewActivitySubmission(submission.getId(), decision, uid, clean(cmd.getNote(), 1000)) != 1) {
            throw invalidState();
        }
        if ("APPROVED".equals(decision)) {
            publishContribution(submission.getSubmitterUid(), activity.getDomain(),
                    "ACTIVITY_SUBMISSION_ACCEPTED", submission.getId(), submission.getPostId(),
                    "COLLAB:ACTIVITY_SUBMISSION:" + submission.getId());
        }
        mapper.refreshActivitySubmissionCount(activity.getId());
        return toSubmission(requireSubmission(mapper.lockActivitySubmission(submission.getId())));
    }

    @Transactional
    public CurationSuggestionDTO createCurationSuggestion(CurationSuggestionCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        TopicRefRow topic = mapper.lockTopicRef(requireId(cmd.getTopicId()));
        if (topic == null || Objects.equals(topic.getDeleted(), 1) || !Objects.equals(topic.getStatus(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostRefRow post = requirePublicPost(cmd.getPostId());
        requireTopicDomain(topic, post.getDomain());
        if (mapper.selectTopicPostResultId(topic.getId(), post.getId()) != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "the post is already curated into this topic");
        }
        requireCreationRisk(post.getDomain(), cmd.getRiskAcknowledged(), uid);
        String type = enumValue(cmd.getSuggestionType(), "CONTENT", "TOPIC", "RESOURCE", "FRESHNESS", "QUESTION");
        requireCurationRole(uid, post.getDomain(), type);
        String rationale = required(cmd.getRationale(), 1000);
        Long id = idGenerator.nextId();
        moderate(uid, "CURATION_SUGGESTION", id, rationale);
        try {
            mapper.insertCurationSuggestion(id, topic.getId(), post.getId(), uid, post.getDomain(), rationale, type);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        return toCuration(mapper.listCurationSuggestions(null, null, uid, 0, 50).stream()
                .filter(row -> id.equals(row.getId())).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    public PageResult<CurationSuggestionDTO> listMyCurationSuggestions(Long uid, String status,
                                                                        long cursor, int size) {
        requireSchema();
        String activeStatus = optionalStatus(status, "PENDING", "APPROVED", "REJECTED");
        int pageSize = pageSize(size);
        List<CurationRow> rows = mapper.listCurationSuggestions(activeStatus, null, uid,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, this::toCuration, CurationRow::getId);
    }

    public PageResult<CurationSuggestionDTO> listCurationReviewQueue(Integer domain, String status,
                                                                     Long uid, long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        if (activeDomain == null) {
            adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        } else {
            domainModeratorService.requireModerateDomain(uid, activeDomain);
        }
        String activeStatus = optionalStatus(status, "PENDING", "APPROVED", "REJECTED");
        int pageSize = pageSize(size);
        List<CurationRow> rows = mapper.listCurationSuggestions(activeStatus, activeDomain, null,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, this::toCuration, CurationRow::getId);
    }

    @Transactional
    public CurationSuggestionDTO decideCurationSuggestion(Long id, ReviewCmd cmd, Long uid) {
        requireSchema();
        CurationRow row = mapper.lockCurationSuggestion(requireId(id));
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        domainModeratorService.requireModerateDomain(uid, row.getDomain());
        if (Objects.equals(row.getSubmitterUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "curators cannot review their own suggestion");
        }
        String decision = decision(cmd.getDecision());
        String resultType = null;
        Long resultId = null;
        String resultStatus = "NOT_APPLICABLE";
        boolean contributionCreated = false;
        if ("APPROVED".equals(decision)) {
            TopicRefRow topic = mapper.lockTopicRef(row.getTopicId());
            if (topic == null || Objects.equals(topic.getDeleted(), 1) || !Objects.equals(topic.getStatus(), 1)) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            PostRefRow post = requirePublicPost(row.getPostId());
            requireMatchingDomain(row.getDomain(), post.getDomain());
            requireTopicDomain(topic, post.getDomain());
            requireCurationRole(row.getSubmitterUid(), post.getDomain(), row.getSuggestionType());
            if (mapper.selectTopicPostResultId(row.getTopicId(), row.getPostId()) != null) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                        "the post is already curated into this topic");
            }
            if ("CONTENT".equals(row.getSuggestionType()) || "TOPIC".equals(row.getSuggestionType())) {
                Long relationId = idGenerator.nextId();
                try {
                    if (mapper.insertTopicPostResult(
                            relationId, row.getTopicId(), row.getPostId(), row.getId(), uid) != 1) {
                        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                                "topic-post curation result could not be persisted");
                    }
                } catch (DuplicateKeyException e) {
                    throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                            "the post is already curated into this topic");
                }
                resultId = relationId;
                resultType = "TOPIC_POST";
                resultStatus = "COMPLETED";
                contributionCreated = true;
            } else {
                resultType = "MAINTENANCE_TASK";
                resultId = row.getId();
                resultStatus = "PENDING_EXECUTION";
            }
        }
        if (mapper.reviewCurationSuggestion(row.getId(), decision, uid, clean(cmd.getNote(), 1000),
                resultType, resultId, resultStatus) != 1) {
            throw invalidState();
        }
        if (contributionCreated) {
            publishContribution(row.getSubmitterUid(), row.getDomain(), "CURATION_SUGGESTION_ACCEPTED",
                    row.getId(), row.getPostId(),
                    "COLLAB:CURATION:" + row.getTopicId() + ":" + row.getPostId());
        }
        return toCuration(mapper.lockCurationSuggestion(row.getId()));
    }

    public PageResult<OfficeHourDTO> listOfficeHours(Integer domain, String status, Long viewerUid,
                                                      long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        String activeStatus = optionalStatus(status, "DRAFT", "OPEN", "CLOSED", "CANCELLED");
        if (viewerUid == null && (activeStatus == null || "DRAFT".equals(activeStatus))) {
            activeStatus = activeStatus == null ? "OPEN" : activeStatus;
        }
        int pageSize = pageSize(size);
        Map<Integer, Boolean> moderation = new HashMap<>();
        List<OfficeHourRow> rows = mapper.listOfficeHours(activeDomain, activeStatus, viewerUid,
                safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize,
                row -> toOfficeHour(row,
                        canManageCached(row.getHostUid(), viewerUid, row.getDomain(), moderation)),
                OfficeHourRow::getId);
    }

    public OfficeHourDTO getOfficeHour(Long id, Long viewerUid) {
        requireSchema();
        OfficeHourRow row = requireOfficeHour(mapper.selectOfficeHour(requireId(id)));
        if ("DRAFT".equals(row.getStatus())
                && !Objects.equals(row.getHostUid(), viewerUid)
                && !canModerate(viewerUid, row.getDomain())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toOfficeHour(row,
                Objects.equals(row.getHostUid(), viewerUid) || canModerate(viewerUid, row.getDomain()));
    }

    @Transactional
    public OfficeHourDTO createOfficeHour(OfficeHourCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        Integer domain = requireDomain(cmd.getDomain());
        requireCreationRisk(domain, cmd.getRiskAcknowledged(), uid);
        LocalDateTime startsAt = cmd.getStartsAt();
        LocalDateTime endsAt = cmd.getEndsAt();
        if (startsAt == null || endsAt == null || !startsAt.isAfter(LocalDateTime.now())
                || !endsAt.isAfter(startsAt)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int capacity = cmd.getCapacity() == null ? 0 : cmd.getCapacity();
        if (capacity < 1 || capacity > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long id = idGenerator.nextId();
        String title = required(cmd.getTitle(), 120);
        String description = required(cmd.getDescription(), 2000);
        String guidance = clean(cmd.getTopicGuidance(), 1000);
        moderate(uid, "COLLAB_OFFICE_HOUR", id, title, description, guidance);
        mapper.insertOfficeHour(id, uid, domain, title, description, guidance,
                startsAt, endsAt, capacity, flag(cmd.getRiskAcknowledged()));
        return getOfficeHour(id, uid);
    }

    @Transactional
    public OfficeHourDTO updateOfficeHourStatus(Long id, OfficeHourStatusCmd cmd, Long uid) {
        requireSchema();
        OfficeHourRow row = requireOfficeHour(mapper.lockOfficeHour(requireId(id)));
        expireOfficeHourIfNecessary(row);
        requireVisibleOfficeHour(row);
        requireManage(row.getHostUid(), row.getDomain(), uid);
        String target = enumValue(cmd.getStatus(), "OPEN", "CLOSED", "CANCELLED");
        if (!validOfficeHourTransition(row.getStatus(), target)) {
            throw invalidState();
        }
        if ("OPEN".equals(target) && !row.getEndsAt().isAfter(LocalDateTime.now())) {
            throw invalidState();
        }
        String note = clean(cmd.getNote(), 500);
        if (note != null) {
            moderate(uid, "COLLAB_OFFICE_HOUR_STATUS", row.getId(), note);
        }
        if (mapper.updateOfficeHourStatus(row.getId(), row.getStatus(), target) != 1) {
            throw invalidState();
        }
        if ("CANCELLED".equals(target)) {
            mapper.cancelActiveOfficeHourReservations(row.getId(), uid, note);
            mapper.refreshOfficeHourReservedCount(row.getId());
        }
        return getOfficeHour(row.getId(), uid);
    }

    @Transactional
    public OfficeHourReservationDTO reserveOfficeHour(Long officeHourId,
                                                       OfficeHourReservationCreateCmd cmd,
                                                       Long uid) {
        requireSchema();
        requirePublisher(uid);
        OfficeHourRow row = requireOfficeHour(mapper.lockOfficeHour(requireId(officeHourId)));
        expireOfficeHourIfNecessary(row);
        requireVisibleOfficeHour(row);
        if (!"OPEN".equals(row.getStatus()) || !row.getEndsAt().isAfter(LocalDateTime.now())) {
            throw invalidState();
        }
        if (Objects.equals(row.getHostUid(), uid)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "hosts cannot reserve their own office hour");
        }
        requireRisk(row.getDomain(), cmd.getRiskAcknowledged());
        Long id = idGenerator.nextId();
        String topic = required(cmd.getTopic(), 160);
        String context = clean(cmd.getContextDetail(), 1500);
        moderate(uid, "COLLAB_OFFICE_RESERVATION", id, topic, context);
        if (mapper.reserveOfficeHourCapacity(row.getId()) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "office hour has no available capacity");
        }
        try {
            mapper.insertOfficeHourReservation(id, row.getId(), uid, topic, context,
                    flag(cmd.getRiskAcknowledged()));
        } catch (DuplicateKeyException e) {
            mapper.releaseOfficeHourCapacity(row.getId());
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        } catch (RuntimeException e) {
            mapper.releaseOfficeHourCapacity(row.getId());
            throw e;
        }
        return toOfficeHourReservation(requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(id)), uid);
    }

    @Transactional
    public PageResult<OfficeHourReservationDTO> listOfficeHourReservations(
            Long officeHourId, String status, Long uid, long cursor, int size) {
        requireSchema();
        OfficeHourRow row = requireOfficeHour(mapper.lockOfficeHour(requireId(officeHourId)));
        expireOfficeHourIfNecessary(row);
        requireVisibleOfficeHour(row);
        boolean manager = Objects.equals(row.getHostUid(), uid) || canModerate(uid, row.getDomain());
        String activeStatus = optionalStatus(status,
                "PENDING", "ACCEPTED", "REJECTED", "COMPLETED", "CANCELLED", "EXPIRED");
        int pageSize = pageSize(size);
        List<OfficeHourReservationRow> rows = mapper.listOfficeHourReservations(
                row.getId(), manager ? null : uid, activeStatus, safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, item -> toOfficeHourReservation(item, uid),
                OfficeHourReservationRow::getId);
    }

    public PageResult<OfficeHourReservationDTO> listMyOfficeHourReservations(
            Long uid, String status, long cursor, int size) {
        requireSchema();
        String activeStatus = optionalStatus(status,
                "PENDING", "ACCEPTED", "REJECTED", "COMPLETED", "CANCELLED", "EXPIRED");
        int pageSize = pageSize(size);
        List<OfficeHourReservationRow> rows = mapper.listOfficeHourReservations(
                null, uid, activeStatus, safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, item -> toOfficeHourReservation(item, uid),
                OfficeHourReservationRow::getId);
    }

    @Transactional
    public OfficeHourReservationDTO decideOfficeHourReservation(Long officeHourId, Long reservationId,
                                                                 OfficeHourReservationDecisionCmd cmd,
                                                                 Long uid) {
        requireSchema();
        OfficeHourRow officeHour = requireOfficeHour(mapper.lockOfficeHour(requireId(officeHourId)));
        expireOfficeHourIfNecessary(officeHour);
        requireVisibleOfficeHour(officeHour);
        requireManage(officeHour.getHostUid(), officeHour.getDomain(), uid);
        OfficeHourReservationRow reservation = requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(requireId(reservationId)));
        requireReservationOfficeHour(reservation, officeHour.getId());
        requireVisibleReservation(reservation);
        String decision = enumValue(cmd.getDecision(), "ACCEPTED", "REJECTED");
        if ("ACCEPTED".equals(decision) && !"OPEN".equals(officeHour.getStatus())) {
            throw invalidState();
        }
        String note = clean(cmd.getNote(), 1000);
        if (note != null) {
            moderate(uid, "COLLAB_OFFICE_RESERVATION_DECISION", reservation.getId(), note);
        }
        if (mapper.decideOfficeHourReservation(reservation.getId(), decision, note, uid) != 1) {
            throw invalidState();
        }
        if ("REJECTED".equals(decision)) {
            mapper.releaseOfficeHourCapacity(officeHour.getId());
        }
        return toOfficeHourReservation(requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(reservation.getId())), uid);
    }

    @Transactional
    public OfficeHourReservationDTO cancelOfficeHourReservation(Long officeHourId, Long reservationId,
                                                                 Long uid) {
        requireSchema();
        OfficeHourRow officeHour = requireOfficeHour(mapper.lockOfficeHour(requireId(officeHourId)));
        expireOfficeHourIfNecessary(officeHour);
        requireVisibleOfficeHour(officeHour);
        OfficeHourReservationRow reservation = requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(requireId(reservationId)));
        requireReservationOfficeHour(reservation, officeHour.getId());
        requireVisibleReservation(reservation);
        if (!Objects.equals(reservation.getAttendeeUid(), uid)
                && !Objects.equals(officeHour.getHostUid(), uid)
                && !canModerate(uid, officeHour.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (mapper.cancelOfficeHourReservation(reservation.getId(), uid) != 1) {
            throw invalidState();
        }
        mapper.releaseOfficeHourCapacity(officeHour.getId());
        return toOfficeHourReservation(requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(reservation.getId())), uid);
    }

    @Transactional
    public OfficeHourReservationDTO confirmOfficeHourReservation(Long officeHourId, Long reservationId,
                                                                  Long uid) {
        requireSchema();
        OfficeHourRow officeHour = requireOfficeHour(mapper.lockOfficeHour(requireId(officeHourId)));
        expireOfficeHourIfNecessary(officeHour);
        requireVisibleOfficeHour(officeHour);
        OfficeHourReservationRow reservation = requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(requireId(reservationId)));
        requireReservationOfficeHour(reservation, officeHour.getId());
        requireVisibleReservation(reservation);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime confirmationDeadline = officeHour.getEndsAt()
                .plusHours(OFFICE_HOUR_CONFIRMATION_GRACE_HOURS);
        if (!"ACCEPTED".equals(reservation.getStatus())
                || now.isBefore(officeHour.getEndsAt())
                || now.isAfter(confirmationDeadline)) {
            throw invalidState();
        }
        if (Objects.equals(officeHour.getHostUid(), uid)) {
            mapper.confirmOfficeHourReservationByHost(reservation.getId());
        } else if (Objects.equals(reservation.getAttendeeUid(), uid)) {
            mapper.confirmOfficeHourReservationByAttendee(reservation.getId());
        } else {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        mapper.completeOfficeHourReservationIfConfirmed(reservation.getId());
        return toOfficeHourReservation(requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(reservation.getId())), uid);
    }

    @Transactional
    public OfficeHourFeedbackDTO createOfficeHourFeedback(Long officeHourId, Long reservationId,
                                                           OfficeHourFeedbackCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        OfficeHourRow officeHour = requireOfficeHour(mapper.lockOfficeHour(requireId(officeHourId)));
        expireOfficeHourIfNecessary(officeHour);
        requireVisibleOfficeHour(officeHour);
        OfficeHourReservationRow reservation = requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(requireId(reservationId)));
        requireReservationOfficeHour(reservation, officeHour.getId());
        requireVisibleReservation(reservation);
        if (!"COMPLETED".equals(reservation.getStatus())) {
            throw invalidState();
        }
        Long targetUid;
        if (Objects.equals(uid, officeHour.getHostUid())) {
            targetUid = reservation.getAttendeeUid();
        } else if (Objects.equals(uid, reservation.getAttendeeUid())) {
            targetUid = officeHour.getHostUid();
        } else {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int rating = cmd.getRating() == null ? 0 : cmd.getRating();
        if (rating < 1 || rating > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long id = idGenerator.nextId();
        String feedback = clean(cmd.getFeedback(), 1000);
        if (feedback != null) {
            moderate(uid, "COLLAB_OFFICE_FEEDBACK", id, feedback);
        }
        try {
            mapper.insertOfficeHourFeedback(id, reservation.getId(), officeHour.getId(),
                    uid, targetUid, rating, feedback);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        return mapper.listOfficeHourFeedback(reservation.getId()).stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .map(this::toOfficeHourFeedback)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public List<OfficeHourFeedbackDTO> listOfficeHourFeedback(Long officeHourId, Long reservationId,
                                                               Long uid) {
        requireSchema();
        OfficeHourRow officeHour = requireOfficeHour(mapper.selectOfficeHour(requireId(officeHourId)));
        OfficeHourReservationRow reservation = requireOfficeHourReservation(
                mapper.lockOfficeHourReservation(requireId(reservationId)));
        requireReservationOfficeHour(reservation, officeHour.getId());
        requireVisibleReservation(reservation);
        boolean moderator = canModerate(uid, officeHour.getDomain());
        if (!Objects.equals(uid, officeHour.getHostUid())
                && !Objects.equals(uid, reservation.getAttendeeUid())
                && !moderator) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        List<OfficeHourFeedbackRow> feedback = mapper.listOfficeHourFeedback(reservation.getId());
        boolean revealMutualFeedback = moderator
                || mapper.countOfficeHourFeedback(reservation.getId()) >= 2
                || feedbackRevealDeadlinePassed(reservation);
        return feedback.stream()
                .filter(item -> revealMutualFeedback || Objects.equals(item.getAuthorUid(), uid))
                .map(this::toOfficeHourFeedback)
                .toList();
    }

    public PageResult<DiscussionDTO> listDiscussions(Integer domain, String status, Long postId,
                                                      Long viewerUid, long cursor, int size) {
        requireSchema();
        Integer activeDomain = optionalDomain(domain);
        String activeStatus = optionalStatus(status, "OPEN", "SUMMARIZED", "CLOSED");
        Long activePostId = positiveOrNull(postId);
        int pageSize = pageSize(size);
        List<DiscussionRow> rows = mapper.listDiscussions(activeDomain, activeStatus, activePostId,
                safeCursor(cursor), pageSize + 1);
        List<Long> discussionIds = rows.stream().map(DiscussionRow::getId).toList();
        Map<Long, List<DiscussionOptionRow>> options = discussionIds.isEmpty()
                ? Map.of()
                : mapper.listDiscussionOptionsByDiscussionIds(discussionIds).stream()
                .collect(Collectors.groupingBy(DiscussionOptionRow::getDiscussionId));
        Map<Long, Long> selected = viewerUid == null || discussionIds.isEmpty()
                ? Map.of()
                : mapper.listDiscussionVotes(discussionIds, viewerUid).stream()
                .collect(Collectors.toMap(DiscussionVoteRow::getDiscussionId, DiscussionVoteRow::getOptionId));
        Map<Integer, Boolean> moderation = new HashMap<>();
        return page(rows, pageSize,
                row -> toDiscussion(row, viewerUid, options.getOrDefault(row.getId(), List.of()),
                        selected.get(row.getId()),
                        canManageCached(row.getCreatorUid(), viewerUid, row.getDomain(), moderation)),
                DiscussionRow::getId);
    }

    public DiscussionDTO getDiscussion(Long id, Long viewerUid) {
        requireSchema();
        DiscussionRow discussion = requireDiscussion(mapper.selectDiscussion(requireId(id)));
        requirePublicPost(discussion.getSourcePostId());
        return toDiscussion(discussion, viewerUid);
    }

    @Transactional
    public DiscussionDTO createDiscussion(DiscussionCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        PostRefRow post = requirePublicPost(cmd.getSourcePostId());
        requireRisk(post.getDomain(), cmd.getRiskAcknowledged());
        Long id = idGenerator.nextId();
        String title = required(cmd.getTitle(), 120);
        String prompt = required(cmd.getPrompt(), 2000);
        List<String> options = cmd.getOptions().stream().map(value -> required(value, 300)).distinct().toList();
        if (options.size() < 2) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        moderate(uid, "STRUCTURED_DISCUSSION", id, title, prompt, String.join("\n", options));
        mapper.insertDiscussion(id, uid, post.getId(), post.getDomain(), title, prompt, flag(cmd.getRiskAcknowledged()));
        for (int i = 0; i < options.size(); i++) {
            mapper.insertDiscussionOption(idGenerator.nextId(), id, options.get(i), i);
        }
        return getDiscussion(id, uid);
    }

    @Transactional
    public DiscussionDTO voteDiscussion(Long id, VoteCmd cmd, Long uid) {
        requireSchema();
        DiscussionRow discussion = requireDiscussion(mapper.lockDiscussion(requireId(id)));
        if (!"OPEN".equals(discussion.getStatus())) {
            throw invalidState();
        }
        requireRisk(discussion.getDomain(), cmd.getRiskAcknowledged());
        Long optionId = requireId(cmd.getOptionId());
        if (mapper.optionBelongsToDiscussion(discussion.getId(), optionId) != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long previous = mapper.selectVoteOption(discussion.getId(), uid);
        if (previous == null) {
            mapper.insertDiscussionVote(idGenerator.nextId(), discussion.getId(), optionId, uid);
            mapper.incrementDiscussionOptionVoteCount(optionId, 1);
            mapper.incrementDiscussionVoteCount(discussion.getId(), 1);
        } else if (!previous.equals(optionId)) {
            mapper.updateDiscussionVote(discussion.getId(), optionId, uid);
            mapper.incrementDiscussionOptionVoteCount(previous, -1);
            mapper.incrementDiscussionOptionVoteCount(optionId, 1);
        }
        return getDiscussion(discussion.getId(), uid);
    }

    @Transactional
    public DiscussionDTO summarizeDiscussion(Long id, DiscussionSummaryCmd cmd, Long uid) {
        requireSchema();
        DiscussionRow discussion = requireDiscussion(mapper.lockDiscussion(requireId(id)));
        PostRefRow sourcePost = requirePublicPost(discussion.getSourcePostId());
        boolean sourceAuthor = Objects.equals(sourcePost.getAuthorUid(), uid);
        if (!Objects.equals(discussion.getCreatorUid(), uid) && !sourceAuthor && !canModerate(uid, discussion.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String summary = required(cmd.getSummary(), 4000);
        String consensus = enumValue(cmd.getConsensusState(), "REACHED", "PARTIAL", "NOT_REACHED");
        String followUp = clean(cmd.getAuthorFollowUp(), 2000);
        if (followUp != null && !sourceAuthor && !canModerate(uid, discussion.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        moderate(uid, "DISCUSSION_SUMMARY", discussion.getId(), summary, followUp);
        String status = Boolean.TRUE.equals(cmd.getCloseAfterSummary()) ? "CLOSED" : "SUMMARIZED";
        if (mapper.summarizeDiscussion(discussion.getId(), summary, consensus, followUp, status, uid) != 1) {
            throw invalidState();
        }
        return getDiscussion(discussion.getId(), uid);
    }

    @Transactional
    public GovernanceCaseDTO createGovernanceCase(GovernanceCaseCreateCmd cmd, Long uid) {
        requireSchema();
        requirePublisher(uid);
        String caseType = enumValue(cmd.getCaseType(), "REPORT", "APPEAL");
        String targetType = enumValue(cmd.getTargetType(), "NEED", "SERIES", "ACTIVITY", "CURATION",
                "DISCUSSION", "OFFICE_HOUR", "RESERVATION", "FEEDBACK");
        Long targetId = requireId(cmd.getTargetId());
        Long parentId = positiveOrNull(cmd.getParentCaseId());
        if ("APPEAL".equals(caseType)) {
            GovernanceCaseRow parent = mapper.lockGovernanceCase(requireId(parentId));
            if (parent == null
                    || !"REPORT".equals(parent.getCaseType())
                    || !Objects.equals(parent.getTargetType(), targetType)
                    || !Objects.equals(parent.getTargetId(), targetId)
                    || !"UPHELD".equals(parent.getStatus())
                    || !Objects.equals(governanceTargetOwner(targetType, targetId), uid)) {
                throw new BizException(ErrorCode.INVALID_REQUEST);
            }
        } else if (parentId != null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        } else {
            requireGovernanceTarget(targetType, targetId);
        }
        String reason = enumValue(cmd.getReasonCode(), "ABUSE", "SPAM", "MISLEADING", "COPYRIGHT",
                "PRIVACY", "CONFLICT", "OTHER");
        String detail = required(cmd.getDetail(), 2000);
        int dailyLimit = "REPORT".equals(caseType) ? REPORT_DAILY_LIMIT : APPEAL_DAILY_LIMIT;
        if (mapper.countRecentGovernanceCases(uid, caseType, LocalDateTime.now().minusHours(24)) >= dailyLimit) {
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                    "daily governance submission limit reached");
        }
        Long id = idGenerator.nextId();
        moderate(uid, "COLLAB_GOVERNANCE", id, detail);
        try {
            mapper.insertGovernanceCase(id, caseType, targetType, targetId, uid, parentId, reason, detail);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        return toGovernance(mapper.listGovernanceCases(null, uid, 0, 50).stream()
                .filter(row -> id.equals(row.getId())).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    public PageResult<GovernanceCaseDTO> listMyGovernanceCases(Long uid, String status, long cursor, int size) {
        requireSchema();
        String activeStatus = optionalStatus(status, "PENDING", "UPHELD", "REJECTED", "CLOSED", "OVERTURNED");
        int pageSize = pageSize(size);
        List<GovernanceCaseRow> rows = mapper.listGovernanceCases(activeStatus, uid, safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, this::toGovernance, GovernanceCaseRow::getId);
    }

    public PageResult<GovernanceCaseDTO> listGovernanceReviewQueue(Long uid, String status, long cursor, int size) {
        requireSchema();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        String activeStatus = optionalStatus(status, "PENDING", "UPHELD", "REJECTED", "CLOSED", "OVERTURNED");
        int pageSize = pageSize(size);
        List<GovernanceCaseRow> rows = mapper.listGovernanceCases(activeStatus, null, safeCursor(cursor), pageSize + 1);
        return page(rows, pageSize, this::toGovernance, GovernanceCaseRow::getId);
    }

    @Transactional
    public GovernanceCaseDTO decideGovernanceCase(Long id, ReviewCmd cmd, Long uid) {
        requireSchema();
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        GovernanceCaseRow row = mapper.lockGovernanceCase(requireId(id));
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (Objects.equals(row.getSubmitterUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "governance submitters cannot review their own case");
        }
        if ("APPEAL".equals(row.getCaseType())) {
            GovernanceCaseRow parent = mapper.lockGovernanceCase(requireId(row.getParentCaseId()));
            if (parent == null) {
                throw new BizException(ErrorCode.INVALID_REQUEST);
            }
            if (Objects.equals(parent.getReviewerUid(), uid)) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                        "the original report reviewer cannot review its appeal");
            }
        }
        String decision = enumValue(cmd.getDecision(), "UPHELD", "REJECTED", "CLOSED");
        String note = clean(cmd.getNote(), 1000);
        if (mapper.decideGovernanceCase(row.getId(), decision, uid, note) != 1) {
            throw invalidState();
        }
        if ("UPHELD".equals(decision)) {
            applyGovernanceDecision(row, uid, note);
        }
        return toGovernance(mapper.lockGovernanceCase(row.getId()));
    }

    private NeedDTO toNeed(NeedRow row, Long viewerUid) {
        return toNeed(row, viewerUid,
                Objects.equals(row.getCreatorUid(), viewerUid) || canModerate(viewerUid, row.getDomain()),
                true);
    }

    private NeedDTO toNeed(NeedRow row, Long viewerUid, boolean canManage) {
        return toNeed(row, viewerUid, canManage, false);
    }

    private NeedDTO toNeed(NeedRow row, Long viewerUid, boolean canManage, boolean includeClaimHistory) {
        boolean canViewParticipantDetails = canManage
                || (viewerUid != null && Objects.equals(row.getClaimedByUid(), viewerUid));
        List<NeedClaimCycleDTO> claimCycles = includeClaimHistory && canViewParticipantDetails
                ? toNeedClaimCycles(row.getId(), viewerUid, canManage)
                : List.of();
        NeedClaimCycleDTO currentCycle = claimCycles.stream()
                .filter(cycle -> "ACTIVE".equals(cycle.getStatus()))
                .findFirst()
                .orElse(null);
        NeedRevisionDTO currentRevision = currentCycle == null || currentCycle.getRevisions() == null
                ? null
                : currentCycle.getRevisions().stream()
                .filter(Objects::nonNull)
                .max((left, right) -> Integer.compare(
                        zero(left.getRevisionNo()), zero(right.getRevisionNo())))
                .orElse(null);
        return NeedDTO.builder()
                .id(row.getId()).creatorUid(row.getCreatorUid()).domain(row.getDomain())
                .sourceType(row.getSourceType()).sourceRefId(row.getSourceRefId()).contentFormat(row.getContentFormat())
                .title(row.getTitle()).description(row.getDescription())
                .acceptanceCriteria(row.getAcceptanceCriteria()).status(row.getStatus())
                .claimedByUid(row.getClaimedByUid())
                .claimedAt(canViewParticipantDetails ? row.getClaimedAt() : null)
                .lastProgressAt(canViewParticipantDetails ? effectiveNeedLastProgressAt(row) : null)
                .stalled(canViewParticipantDetails ? isNeedStalled(row) : null)
                .mergedIntoNeedId(row.getMergedIntoNeedId())
                .resolutionType(row.getResolutionType()).resolutionId(row.getResolutionId())
                .resolutionPostId(row.getResolutionPostId())
                .closedReason(canViewParticipantDetails ? row.getClosedReason() : null)
                .submittedByUid(canViewParticipantDetails ? row.getSubmittedByUid() : null)
                .submittedAt(canViewParticipantDetails ? row.getSubmittedAt() : null)
                .submissionResolutionType(canViewParticipantDetails ? row.getSubmissionResolutionType() : null)
                .submissionResolutionId(canViewParticipantDetails ? row.getSubmissionResolutionId() : null)
                .submissionNote(canViewParticipantDetails ? row.getSubmissionNote() : null)
                .rejectReason(canViewParticipantDetails ? row.getRejectReason() : null)
                .currentClaimCycleNo(canViewParticipantDetails && currentCycle != null
                        ? currentCycle.getCycleNo() : null)
                .currentClaimCycleOrigin(canViewParticipantDetails && currentCycle != null
                        ? currentCycle.getCycleOrigin() : null)
                .currentRevisionNo(canViewParticipantDetails && currentRevision != null
                        ? currentRevision.getRevisionNo() : null)
                .currentRevisionOrigin(canViewParticipantDetails && currentRevision != null
                        ? currentRevision.getRevisionOrigin() : null)
                .claimCycles(canViewParticipantDetails ? claimCycles : List.of())
                .followerCount(zero(row.getFollowerCount())).followed(Objects.equals(row.getFollowed(), 1))
                .canManage(canManage)
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private NeedRevisionRow currentSubmittedRevision(NeedClaimCycleRow cycle, NeedRow need) {
        if (cycle == null || need == null || !"SUBMITTED".equals(need.getStatus())) {
            return null;
        }
        return mapper.selectCurrentNeedRevision(need.getId(), cycle.getId());
    }

    private NeedClaimCycleRow ensureCurrentClaimCycle(NeedRow need) {
        if (need == null || need.getClaimedByUid() == null
                || (!"CLAIMED".equals(need.getStatus()) && !"SUBMITTED".equals(need.getStatus()))) {
            return null;
        }
        NeedClaimCycleRow cycle = mapper.selectCurrentNeedClaimCycle(need.getId());
        if (cycle != null) {
            if (!Objects.equals(cycle.getClaimantUid(), need.getClaimedByUid())) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "current collaboration claim cycle does not match the claimant");
            }
            return cycle;
        }
        if (mapper.insertLegacyCurrentNeedClaimCycle(
                idGenerator.nextId(),
                need.getId(),
                need.getClaimedByUid(),
                need.getClaimedAt(),
                effectiveNeedLastProgressAt(need)) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to hand off current collaboration claim cycle");
        }
        cycle = mapper.selectCurrentNeedClaimCycle(need.getId());
        if (cycle == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "current collaboration claim cycle is unavailable");
        }
        return cycle;
    }

    private NeedRevisionRow currentSubmittedRevisionOrHandoff(
            NeedClaimCycleRow cycle, NeedRow need) {
        NeedRevisionRow revision = currentSubmittedRevision(cycle, need);
        if (revision != null || cycle == null || need == null
                || !"SUBMITTED".equals(need.getStatus())
                || need.getSubmissionResolutionType() == null
                || need.getSubmissionResolutionId() == null
                || !Objects.equals(need.getSubmittedByUid(), need.getClaimedByUid())) {
            return revision;
        }
        String note = clean(need.getSubmissionNote(), 1000);
        if (mapper.insertNeedRevision(
                idGenerator.nextId(),
                need.getId(),
                cycle.getId(),
                cycle.getCycleNo(),
                need.getClaimedByUid(),
                need.getSubmissionResolutionType(),
                need.getSubmissionResolutionId(),
                "POST".equals(need.getSubmissionResolutionType())
                        || "QUESTION".equals(need.getSubmissionResolutionType())
                        ? need.getSubmissionResolutionId() : null,
                note,
                EVENT_VISIBILITY_PARTICIPANTS,
                "LEGACY_CURRENT_SUBMISSION",
                need.getSubmittedAt()) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to hand off current collaboration submission");
        }
        revision = mapper.selectCurrentNeedRevision(need.getId(), cycle.getId());
        if (revision == null) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "current collaboration submission is unavailable");
        }
        return revision;
    }

    private void endCurrentClaimCycle(NeedRow need, String status, String reason) {
        NeedClaimCycleRow cycle = mapper.selectCurrentNeedClaimCycle(need.getId());
        if (cycle != null) {
            endClaimCycle(cycle, status, reason);
        }
    }

    private void endClaimCycle(NeedClaimCycleRow cycle, String status, String reason) {
        if (mapper.endNeedClaimCycle(cycle.getId(), status, clean(reason, 500)) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to close collaboration claim cycle");
        }
    }

    private void touchClaimCycle(NeedClaimCycleRow cycle) {
        if (mapper.touchNeedClaimCycle(cycle.getId()) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to update collaboration claim cycle");
        }
    }

    private void decideRevision(NeedRevisionRow revision, String status, Long decidedBy, String note) {
        if (mapper.decideNeedRevision(revision.getId(), status, decidedBy, clean(note, 1000)) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to decide collaboration submission revision");
        }
    }

    private List<NeedClaimCycleDTO> toNeedClaimCycles(Long needId, Long viewerUid, boolean canManage) {
        List<NeedRevisionRow> revisionRows = mapper.listNeedRevisions(
                needId, viewerUid, canManage ? 1 : 0);
        Map<Long, List<NeedRevisionDTO>> revisionsByCycle = (revisionRows == null ? List.<NeedRevisionRow>of() : revisionRows)
                .stream()
                .collect(Collectors.groupingBy(
                        NeedRevisionRow::getCycleId,
                        Collectors.mapping(this::toNeedRevision, Collectors.toList())));
        List<NeedClaimCycleRow> cycleRows = mapper.listNeedClaimCycles(
                needId, viewerUid, canManage ? 1 : 0);
        return (cycleRows == null ? List.<NeedClaimCycleRow>of() : cycleRows)
                .stream()
                .map(row -> NeedClaimCycleDTO.builder()
                        .id(row.getId())
                        .needId(row.getNeedId())
                        .cycleNo(row.getCycleNo())
                        .claimantUid(row.getClaimantUid())
                        .status(row.getStatus())
                        .cycleOrigin(row.getCycleOrigin())
                        .claimedAt(row.getClaimedAt())
                        .lastProgressAt(row.getLastProgressAt())
                        .endedAt(row.getEndedAt())
                        .endReason(row.getEndReason())
                        .revisions(revisionsByCycle.getOrDefault(row.getId(), List.of()))
                        .createTime(row.getCreateTime())
                        .updateTime(row.getUpdateTime())
                        .build())
                .toList();
    }

    private NeedRevisionDTO toNeedRevision(NeedRevisionRow row) {
        return NeedRevisionDTO.builder()
                .id(row.getId())
                .needId(row.getNeedId())
                .cycleId(row.getCycleId())
                .cycleNo(row.getCycleNo())
                .revisionNo(row.getRevisionNo())
                .submitterUid(row.getSubmitterUid())
                .resolutionType(row.getResolutionType())
                .resolutionId(row.getResolutionId())
                .resolutionPostId(row.getResolutionPostId())
                .note(row.getNote())
                .status(row.getStatus())
                .revisionOrigin(row.getRevisionOrigin())
                .submittedAt(row.getSubmittedAt())
                .decidedBy(row.getDecidedBy())
                .decidedAt(row.getDecidedAt())
                .decisionNote(row.getDecisionNote())
                .visibilityScope(row.getVisibilityScope())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private NeedEventDTO toNeedEvent(NeedEventRow row) {
        return NeedEventDTO.builder()
                .id(row.getId())
                .needId(row.getNeedId())
                .eventType(row.getEventType())
                .actorUid(row.getActorUid())
                .fromStatus(row.getFromStatus())
                .toStatus(row.getToStatus())
                .targetType(row.getTargetType())
                .targetId(row.getTargetId())
                .note(row.getNote())
                .visibilityScope(row.getVisibilityScope())
                .createTime(row.getCreateTime())
                .build();
    }

    private static boolean isNeedStalled(NeedRow row) {
        LocalDateTime lastProgressAt = effectiveNeedLastProgressAt(row);
        return "CLAIMED".equals(row.getStatus())
                && lastProgressAt != null
                && lastProgressAt.isBefore(LocalDateTime.now().minusDays(CLAIM_STALE_AFTER_DAYS));
    }

    private static LocalDateTime effectiveNeedLastProgressAt(NeedRow row) {
        if (row.getLastProgressAt() != null) {
            return row.getLastProgressAt();
        }
        if (!"CLAIMED".equals(row.getStatus()) && !"SUBMITTED".equals(row.getStatus())) {
            return null;
        }
        if (row.getClaimedAt() != null) {
            return row.getClaimedAt();
        }
        if (row.getUpdateTime() != null) {
            return row.getUpdateTime();
        }
        return row.getCreateTime();
    }

    private static void requireIndependentNeedReviewer(Long claimantUid, Long reviewerUid) {
        if (Objects.equals(claimantUid, reviewerUid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "claimants cannot review their own submission");
        }
    }

    private SeriesDTO toSeries(SeriesRow row, Long viewerUid) {
        return toSeries(row, viewerUid,
                Objects.equals(row.getOwnerUid(), viewerUid) || canModerate(viewerUid, row.getDomain()));
    }

    private SeriesDTO toSeries(SeriesRow row, Long viewerUid, boolean canManage) {
        return SeriesDTO.builder()
                .id(row.getId()).ownerUid(row.getOwnerUid()).domain(row.getDomain())
                .title(row.getTitle()).description(row.getDescription())
                .submissionInstructions(row.getSubmissionInstructions()).status(row.getStatus())
                .memberCount(zero(row.getMemberCount())).postCount(zero(row.getPostCount()))
                .currentUserRole(row.getCurrentUserRole())
                .canManage(canManage)
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private SeriesMemberDTO toSeriesMember(SeriesMemberRow row) {
        return SeriesMemberDTO.builder()
                .id(row.getId()).uid(row.getUid()).role(row.getRole()).status(row.getStatus())
                .addedBy(row.getAddedBy()).exitedAt(row.getExitedAt()).revokedAt(row.getRevokedAt())
                .revokedBy(row.getRevokedBy()).createTime(row.getCreateTime()).build();
    }

    private SeriesMemberDTO toPublicSeriesMember(SeriesMemberRow row) {
        return SeriesMemberDTO.builder()
                .uid(row.getUid()).role(row.getRole()).status(row.getStatus())
                .createTime(row.getCreateTime()).build();
    }

    private SubmissionDTO toSubmission(SubmissionRow row) {
        return SubmissionDTO.builder()
                .id(row.getId()).parentId(row.getParentId()).postId(row.getPostId())
                .submitterUid(row.getSubmitterUid()).note(row.getNote()).reviewStatus(row.getReviewStatus())
                .reviewerUid(row.getReviewerUid()).reviewNote(row.getReviewNote()).reviewedAt(row.getReviewedAt())
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private SubmissionDTO toPublicSubmission(SubmissionRow row) {
        return SubmissionDTO.builder()
                .id(row.getId()).parentId(row.getParentId()).postId(row.getPostId())
                .submitterUid(row.getSubmitterUid()).reviewStatus(row.getReviewStatus())
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private ActivityDTO toActivity(ActivityRow row, Long viewerUid) {
        return toActivity(row, viewerUid,
                Objects.equals(row.getOwnerUid(), viewerUid) || canModerate(viewerUid, row.getDomain()));
    }

    private ActivityDTO toActivity(ActivityRow row, Long viewerUid, boolean canManage) {
        return ActivityDTO.builder()
                .id(row.getId()).ownerUid(row.getOwnerUid()).domain(row.getDomain()).activityType(row.getActivityType())
                .title(row.getTitle()).description(row.getDescription()).submissionRule(row.getSubmissionRule())
                .status(row.getStatus()).resultSummary(row.getResultSummary())
                .startsAt(row.getStartsAt()).endsAt(row.getEndsAt()).submissionCount(zero(row.getSubmissionCount()))
                .canManage(canManage)
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private CurationSuggestionDTO toCuration(CurationRow row) {
        return CurationSuggestionDTO.builder()
                .id(row.getId()).topicId(row.getTopicId()).topicName(row.getTopicName())
                .postId(row.getPostId()).submitterUid(row.getSubmitterUid()).domain(row.getDomain())
                .suggestionType(row.getSuggestionType()).rationale(row.getRationale())
                .reviewStatus(row.getReviewStatus()).reviewerUid(row.getReviewerUid())
                .reviewNote(row.getReviewNote()).reviewedAt(row.getReviewedAt())
                .resultType(row.getResultType()).resultId(row.getResultId()).resultStatus(row.getResultStatus())
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private OfficeHourDTO toOfficeHour(OfficeHourRow row, boolean canManage) {
        int capacity = zero(row.getCapacity());
        int reserved = Math.min(zero(row.getReservedCount()), capacity);
        boolean acceptingReservations = "OPEN".equals(row.getStatus())
                && row.getEndsAt() != null
                && row.getEndsAt().isAfter(LocalDateTime.now());
        return OfficeHourDTO.builder()
                .id(row.getId()).hostUid(row.getHostUid()).domain(row.getDomain())
                .title(row.getTitle()).description(row.getDescription()).topicGuidance(row.getTopicGuidance())
                .startsAt(row.getStartsAt()).endsAt(row.getEndsAt())
                .capacity(capacity).reservedCount(reserved)
                .availableCount(acceptingReservations ? Math.max(0, capacity - reserved) : 0)
                .status(row.getStatus()).canManage(canManage)
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private OfficeHourReservationDTO toOfficeHourReservation(OfficeHourReservationRow row, Long viewerUid) {
        boolean canManage = Objects.equals(row.getHostUid(), viewerUid)
                || Objects.equals(row.getAttendeeUid(), viewerUid);
        return OfficeHourReservationDTO.builder()
                .id(row.getId()).officeHourId(row.getOfficeHourId()).hostUid(row.getHostUid())
                .attendeeUid(row.getAttendeeUid()).topic(row.getTopic()).contextDetail(row.getContextDetail())
                .status(row.getStatus()).responseNote(row.getResponseNote())
                .decidedBy(row.getDecidedBy()).decidedAt(row.getDecidedAt())
                .hostConfirmedAt(row.getHostConfirmedAt()).attendeeConfirmedAt(row.getAttendeeConfirmedAt())
                .completedAt(row.getCompletedAt()).cancelledBy(row.getCancelledBy())
                .cancelledAt(row.getCancelledAt()).canManage(canManage)
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private OfficeHourFeedbackDTO toOfficeHourFeedback(OfficeHourFeedbackRow row) {
        return OfficeHourFeedbackDTO.builder()
                .id(row.getId()).reservationId(row.getReservationId()).officeHourId(row.getOfficeHourId())
                .authorUid(row.getAuthorUid()).targetUid(row.getTargetUid()).rating(row.getRating())
                .feedback(row.getFeedback()).createTime(row.getCreateTime()).updateTime(row.getUpdateTime())
                .build();
    }

    private DiscussionDTO toDiscussion(DiscussionRow row, Long viewerUid) {
        Long selected = viewerUid == null ? null : mapper.selectVoteOption(row.getId(), viewerUid);
        return toDiscussion(row, viewerUid, mapper.listDiscussionOptions(row.getId()), selected,
                Objects.equals(row.getCreatorUid(), viewerUid) || canModerate(viewerUid, row.getDomain()));
    }

    private DiscussionDTO toDiscussion(DiscussionRow row, Long viewerUid,
                                       List<DiscussionOptionRow> optionRows,
                                       Long selected,
                                       boolean canManage) {
        List<DiscussionOptionDTO> options = optionRows.stream()
                .map(option -> DiscussionOptionDTO.builder()
                        .id(option.getId()).text(option.getText()).sortOrder(option.getSortOrder())
                        .voteCount(zero(option.getVoteCount())).selected(option.getId().equals(selected)).build())
                .toList();
        return DiscussionDTO.builder()
                .id(row.getId()).creatorUid(row.getCreatorUid()).sourcePostId(row.getSourcePostId())
                .domain(row.getDomain()).title(row.getTitle()).prompt(row.getPrompt()).status(row.getStatus())
                .summary(row.getSummary()).consensusState(row.getConsensusState())
                .authorFollowUp(row.getAuthorFollowUp()).followedUpBy(row.getFollowedUpBy())
                .followedUpAt(row.getFollowedUpAt()).summarizedBy(row.getSummarizedBy())
                .summarizedAt(row.getSummarizedAt()).voteCount(zero(row.getVoteCount()))
                .selectedOptionId(selected)
                .canManage(canManage)
                .options(options).createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private GovernanceCaseDTO toGovernance(GovernanceCaseRow row) {
        return GovernanceCaseDTO.builder()
                .id(row.getId()).caseType(row.getCaseType()).targetType(row.getTargetType())
                .targetId(row.getTargetId()).submitterUid(row.getSubmitterUid()).parentCaseId(row.getParentCaseId())
                .reasonCode(row.getReasonCode()).detail(row.getDetail()).status(row.getStatus())
                .reviewerUid(row.getReviewerUid()).reviewNote(row.getReviewNote()).reviewedAt(row.getReviewedAt())
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime()).build();
    }

    private boolean canReviewSeries(Long seriesId, Long uid) {
        if (uid == null) {
            return false;
        }
        String role = mapper.selectSeriesRole(seriesId, uid);
        return "OWNER".equals(role) || "EDITOR".equals(role);
    }

    private void requireSeriesReviewer(Long seriesId, Long uid) {
        if (!canReviewSeries(seriesId, uid)) {
            SeriesRow series = requireSeries(mapper.selectSeries(seriesId, uid));
            if (!canModerate(uid, series.getDomain())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private void requireSeriesOwner(SeriesRow series, Long uid) {
        if (!Objects.equals(series.getOwnerUid(), uid) && !canModerate(uid, series.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private PostRefRow requirePublicPost(Long postId) {
        PostRefRow post = mapper.selectPostRef(requireId(postId));
        if (post == null || Objects.equals(post.getDeleted(), 1)
                || !Objects.equals(post.getStatus(), Post.STATUS_PUBLISHED)
                || !Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)
                || !PostDomain.isValid(post.getDomain())) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private NeedResolution resolveNeedResolution(NeedRow need, NeedCompleteCmd cmd, Long uid) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String type = StringUtils.hasText(cmd.getResolutionType())
                ? enumValue(cmd.getResolutionType(), "POST", "QUESTION", "SERIES")
                : (cmd.getResolutionPostId() == null ? null : "POST");
        if (type == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "resolutionType and resolutionId are required");
        }
        Long id = cmd.getResolutionId() == null ? cmd.getResolutionPostId() : cmd.getResolutionId();
        id = requireId(id);
        if (cmd.getResolutionPostId() != null
                && ("POST".equals(type) || "QUESTION".equals(type))
                && !Objects.equals(id, cmd.getResolutionPostId())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "resolutionId and resolutionPostId must identify the same post");
        }
        if ("SERIES".equals(type)) {
            if (cmd.getResolutionPostId() != null) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                        "resolutionPostId is only compatible with POST or QUESTION");
            }
            SeriesRow series = requireSeries(mapper.selectSeries(id, uid));
            requireMatchingDomain(need.getDomain(), series.getDomain());
            Long contributor = need.getClaimedByUid() == null ? series.getOwnerUid() : need.getClaimedByUid();
            if (mapper.seriesHasContributor(series.getId(), contributor) <= 0) {
                throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                        "the claimed contributor must own or contribute to the resolution series");
            }
            return new NeedResolution(type, series.getId(), null, contributor);
        }
        PostRefRow post = requirePublicPost(id);
        requireMatchingDomain(need.getDomain(), post.getDomain());
        if ("QUESTION".equals(type)
                && !Objects.equals(post.getPostType(), Post.TYPE_COMMUNITY_QUESTION)
                && !Objects.equals(post.getPostType(), Post.TYPE_QA)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "QUESTION resolution must reference a public question post");
        }
        return new NeedResolution(type, post.getId(), post.getId(), post.getAuthorUid());
    }

    private PostRefRow requireOwnedPublicPost(Long postId, Long uid) {
        PostRefRow post = requirePublicPost(postId);
        if (!Objects.equals(post.getAuthorUid(), uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    private boolean isPublicPost(Long postId) {
        try {
            PostRefRow post = mapper.selectPostRef(postId);
            return post != null
                    && Objects.equals(post.getDeleted(), 0)
                    && Objects.equals(post.getStatus(), Post.STATUS_PUBLISHED)
                    && Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)
                    && PostDomain.isValid(post.getDomain());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void requireGovernanceTarget(String type, Long id) {
        boolean exists = switch (type) {
            case "NEED" -> mapper.selectNeed(id, null) != null;
            case "SERIES" -> mapper.selectSeries(id, null) != null;
            case "ACTIVITY" -> mapper.selectActivity(id) != null;
            case "CURATION" -> {
                CurationRow row = mapper.lockCurationSuggestion(id);
                yield row != null && !"REJECTED".equals(row.getReviewStatus());
            }
            case "DISCUSSION" -> mapper.selectDiscussion(id) != null;
            case "OFFICE_HOUR" -> mapper.selectOfficeHour(id) != null;
            case "RESERVATION" -> mapper.lockOfficeHourReservation(id) != null;
            case "FEEDBACK" -> mapper.lockOfficeHourFeedback(id) != null;
            default -> false;
        };
        if (!exists) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private Long governanceTargetOwner(String type, Long id) {
        return switch (type) {
            case "NEED" -> {
                NeedRow row = mapper.lockNeed(id);
                yield row == null ? null : row.getCreatorUid();
            }
            case "SERIES" -> {
                SeriesRow row = mapper.lockSeries(id);
                yield row == null ? null : row.getOwnerUid();
            }
            case "ACTIVITY" -> {
                ActivityRow row = mapper.lockActivity(id);
                yield row == null ? null : row.getOwnerUid();
            }
            case "CURATION" -> {
                CurationRow row = mapper.lockCurationSuggestion(id);
                yield row == null ? null : row.getSubmitterUid();
            }
            case "DISCUSSION" -> {
                DiscussionRow row = mapper.lockDiscussion(id);
                yield row == null ? null : row.getCreatorUid();
            }
            case "OFFICE_HOUR" -> {
                OfficeHourRow row = mapper.lockOfficeHour(id);
                yield row == null ? null : row.getHostUid();
            }
            case "RESERVATION" -> {
                OfficeHourReservationRow row = mapper.lockOfficeHourReservation(id);
                yield row == null ? null : row.getAttendeeUid();
            }
            case "FEEDBACK" -> {
                OfficeHourFeedbackRow row = mapper.lockOfficeHourFeedback(id);
                yield row == null ? null : row.getAuthorUid();
            }
            default -> null;
        };
    }

    private void applyGovernanceDecision(GovernanceCaseRow row, Long reviewerUid, String note) {
        boolean appeal = "APPEAL".equals(row.getCaseType());
        if (appeal) {
            if (row.getParentCaseId() == null || mapper.overturnGovernanceReport(row.getParentCaseId()) != 1) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "appeal parent report is no longer active");
            }
            if (mapper.countActiveUpheldReports(row.getTargetType(), row.getTargetId()) > 0) {
                return;
            }
        }
        int hidden = appeal ? 0 : 1;
        switch (row.getTargetType()) {
            case "NEED" -> mapper.setNeedHidden(row.getTargetId(), hidden);
            case "SERIES" -> mapper.setSeriesHidden(row.getTargetId(), hidden);
            case "ACTIVITY" -> mapper.setActivityHidden(row.getTargetId(), hidden);
            case "DISCUSSION" -> mapper.setDiscussionHidden(row.getTargetId(), hidden);
            case "OFFICE_HOUR" -> mapper.setOfficeHourHidden(row.getTargetId(), hidden);
            case "RESERVATION" -> applyReservationGovernanceDecision(
                    row.getTargetId(), hidden, reviewerUid);
            case "FEEDBACK" -> mapper.setOfficeHourFeedbackHidden(row.getTargetId(), hidden);
            case "CURATION" -> mapper.setCurationGovernanceStatus(
                    row.getTargetId(), appeal ? "PENDING" : "REJECTED", reviewerUid, note);
            default -> throw new BizException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void applyReservationGovernanceDecision(Long reservationId, int hidden, Long reviewerUid) {
        Long officeHourId = mapper.selectReservationOfficeHourId(reservationId);
        if (officeHourId == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        OfficeHourRow officeHour = mapper.lockOfficeHour(officeHourId);
        OfficeHourReservationRow reservation = mapper.lockOfficeHourReservation(reservationId);
        if (officeHour == null || reservation == null
                || !Objects.equals(reservation.getOfficeHourId(), officeHourId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        mapper.setOfficeHourReservationHidden(reservationId, hidden);
        if (hidden == 0 && !Objects.equals(officeHour.getHidden(), 1)) {
            if ("CANCELLED".equals(officeHour.getStatus())) {
                mapper.cancelOfficeHourReservation(reservationId, reviewerUid);
            } else {
                expireOfficeHourIfNecessary(officeHour);
            }
        }
        mapper.refreshOfficeHourReservedCount(officeHourId);
    }

    private void requireManage(Long ownerUid, Integer domain, Long uid) {
        if (!Objects.equals(ownerUid, uid) && !canModerate(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canModerate(Long uid, Integer domain) {
        return uid != null && domain != null && domainModeratorService.canModerateDomain(uid, domain);
    }

    private void requireAnyCommunityRole(Long uid, Integer domain, String... roleCodes) {
        if (canModerate(uid, domain)) {
            return;
        }
        String domainCode = PostDomain.fromCode(domain).name();
        for (String roleCode : roleCodes) {
            if (communityRoleAccessService.hasActiveGrant(uid, roleCode, domainCode)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                "an active community role is required for this collaboration action");
    }

    private void requireCurationRole(Long uid, Integer domain, String suggestionType) {
        switch (suggestionType) {
            case "CONTENT", "TOPIC" -> requireAnyCommunityRole(
                    uid, domain, "TOPIC_CANDIDATE_RECOMMENDER", "CHANNEL_CURATOR");
            case "RESOURCE" -> requireAnyCommunityRole(uid, domain, "CHANNEL_RESOURCE_MAINTAINER");
            case "FRESHNESS", "QUESTION" -> requireAnyCommunityRole(uid, domain, "TOPIC_STEWARD");
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private boolean canManageCached(Long ownerUid, Long viewerUid, Integer domain,
                                    Map<Integer, Boolean> moderation) {
        if (Objects.equals(ownerUid, viewerUid) && viewerUid != null) {
            return true;
        }
        return viewerUid != null && domain != null
                && moderation.computeIfAbsent(domain, value -> canModerate(viewerUid, value));
    }

    private void requirePublisher(Long uid) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        moderationService.requireUserCanPublish(uid);
    }

    private void moderate(Long uid, String sourceType, Long sourceId, String... text) {
        ContentModerationService.ModerationDecision decision =
                moderationService.checkContent(uid, "COLLABORATION", sourceType, sourceId, text);
        if (decision.reviewRequired()) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "Collaboration content requires moderation before it can be published");
        }
    }

    private void requireRisk(Integer domain, Boolean acknowledged) {
        if (domainConfigService.reviewRequiredForPublish(domain) && !Boolean.TRUE.equals(acknowledged)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "High-risk domain collaboration requires explicit risk acknowledgement");
        }
    }

    private void requireCreationRisk(Integer domain, Boolean acknowledged, Long uid) {
        requireRisk(domain, acknowledged);
        if (domainConfigService.reviewRequiredForPublish(domain) && !canModerate(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "High-risk collaboration must be created by an authorized domain moderator");
        }
    }

    private Integer requireDomain(Integer domain) {
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        domainConfigService.requireDomainEnabled(domain);
        return domain;
    }

    private Integer optionalDomain(Integer domain) {
        return domain == null ? null : requireDomain(domain);
    }

    private void requireMatchingDomain(Integer expected, Integer actual) {
        if (!Objects.equals(expected, actual)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "Post domain does not match collaboration domain");
        }
    }

    private static boolean validActivityTransition(String current, String target) {
        return ("DRAFT".equals(current) && "OPEN".equals(target))
                || ("OPEN".equals(current) && ("REVIEWING".equals(target) || "ARCHIVED".equals(target)))
                || ("REVIEWING".equals(current) && "ARCHIVED".equals(target))
                || ("SUMMARIZED".equals(current) && "ARCHIVED".equals(target));
    }

    private static boolean validOfficeHourTransition(String current, String target) {
        return ("DRAFT".equals(current) && ("OPEN".equals(target) || "CANCELLED".equals(target)))
                || ("OPEN".equals(current) && ("CLOSED".equals(target) || "CANCELLED".equals(target)))
                || ("CLOSED".equals(current) && "CANCELLED".equals(target));
    }

    private void expireOfficeHourIfNecessary(OfficeHourRow row) {
        LocalDateTime now = LocalDateTime.now();
        if (!row.getEndsAt().isAfter(now)) {
            mapper.expirePendingOfficeHourReservations(row.getId());
            LocalDateTime confirmationDeadline = row.getEndsAt()
                    .plusHours(OFFICE_HOUR_CONFIRMATION_GRACE_HOURS);
            if (!confirmationDeadline.isAfter(now)) {
                mapper.expireAcceptedOfficeHourReservationsAfterGrace(row.getId(), confirmationDeadline);
            }
            mapper.refreshOfficeHourReservedCount(row.getId());
            if ("OPEN".equals(row.getStatus())
                    && mapper.updateOfficeHourStatus(row.getId(), "OPEN", "CLOSED") == 1) {
                row.setStatus("CLOSED");
            }
        }
    }

    private static void requireVisibleOfficeHour(OfficeHourRow row) {
        if (Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "the office hour is frozen by governance");
        }
    }

    private static void requireVisibleReservation(OfficeHourReservationRow row) {
        if (Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "the reservation is frozen by governance");
        }
    }

    private static boolean feedbackRevealDeadlinePassed(OfficeHourReservationRow reservation) {
        return reservation.getCompletedAt() != null
                && !reservation.getCompletedAt().plusHours(OFFICE_HOUR_FEEDBACK_BLIND_HOURS)
                .isAfter(LocalDateTime.now());
    }

    private void requireTopicDomain(TopicRefRow topic, Integer postDomain) {
        if (topic == null || postDomain == null
                || mapper.topicAllowsDomain(topic.getId(), postDomain) != 1) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "topic does not allow the post domain");
        }
    }

    private static boolean withinActivityWindow(ActivityRow activity) {
        LocalDateTime now = LocalDateTime.now();
        return (activity.getStartsAt() == null || !now.isBefore(activity.getStartsAt()))
                && (activity.getEndsAt() == null || now.isBefore(activity.getEndsAt()));
    }

    private static void requireOpenForParticipation(String status) {
        if (!"OPEN".equals(status) && !"CLAIMED".equals(status)) {
            throw invalidState();
        }
    }

    private static NeedRow requireNeed(NeedRow row) {
        if (row == null || Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static SeriesRow requireSeries(SeriesRow row) {
        if (row == null || Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static void requireSeriesOpen(SeriesRow row) {
        if (!"OPEN".equals(row.getStatus())) {
            throw invalidState();
        }
    }

    private static ActivityRow requireActivity(ActivityRow row) {
        if (row == null || Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static DiscussionRow requireDiscussion(DiscussionRow row) {
        if (row == null || Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static OfficeHourRow requireOfficeHour(OfficeHourRow row) {
        if (row == null || Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static OfficeHourReservationRow requireOfficeHourReservation(OfficeHourReservationRow row) {
        if (row == null || Objects.equals(row.getHidden(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return row;
    }

    private static void requireReservationOfficeHour(OfficeHourReservationRow reservation, Long officeHourId) {
        if (!Objects.equals(reservation.getOfficeHourId(), officeHourId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private static SubmissionRow requireSubmission(SubmissionRow row) {
        if (row == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        return row;
    }

    private void requireSchema() {
        if (schemaReady) {
            return;
        }
        try {
            if (mapper.existingTableCount() == REQUIRED_TABLES + REQUIRED_CLAIM_CYCLE_TABLES
                    && mapper.existingCriticalColumnCount()
                    == REQUIRED_CRITICAL_COLUMNS + REQUIRED_CLAIM_CYCLE_COLUMNS) {
                schemaReady = true;
                return;
            }
        } catch (RuntimeException ignored) {
            // Convert database metadata failures into the same dependency contract.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Collaboration migrations are required: " + MIGRATION + ", " + CLAIM_CYCLE_MIGRATION);
    }

    private static <R, D> PageResult<D> page(List<R> rows, int size,
                                             Function<R, D> converter, Function<R, Long> idGetter) {
        List<R> safe = rows == null ? List.of() : rows;
        boolean hasMore = safe.size() > size;
        List<R> visible = safe.stream().limit(size).toList();
        List<D> items = visible.stream().map(converter).toList();
        String next = hasMore && !visible.isEmpty()
                ? String.valueOf(idGetter.apply(visible.get(visible.size() - 1)))
                : null;
        return PageResult.of(items, next, hasMore);
    }

    private static int pageSize(int size) {
        return Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE));
    }

    private static int followerPageSize(int size) {
        return Math.max(1, Math.min(size <= 0 ? 100 : size, MAX_FOLLOWER_PAGE_SIZE));
    }

    private static long safeCursor(long cursor) {
        return Math.max(cursor, 0);
    }

    private static Long requireId(Long id) {
        if (id == null || id <= 0) throw new BizException(ErrorCode.PARAM_ERROR);
        return id;
    }

    private static Long positiveOrNull(Long id) {
        return id == null ? null : requireId(id);
    }

    private static String required(String value, int max) {
        String result = clean(value, max);
        if (!StringUtils.hasText(result)) throw new BizException(ErrorCode.PARAM_ERROR);
        return result;
    }

    private static String clean(String value, int max) {
        if (!StringUtils.hasText(value)) return null;
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private static String enumValue(String value, String... allowed) {
        String normalized = required(value, 32).toUpperCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) return normalized;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String optionalStatus(String value, String... allowed) {
        return StringUtils.hasText(value) ? enumValue(value, allowed) : null;
    }

    private static String decision(String value) {
        return enumValue(value, "APPROVED", "REJECTED");
    }

    private static int flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static int zero(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private Long appendNeedEvent(Long needId, String eventType, Long actorUid,
                                 Long creatorUid, Long claimantUid, Integer domain,
                                 String fromStatus, String toStatus,
                                 String targetType, Long targetId, String note,
                                 String visibilityScope, boolean publishStateChange) {
        Long eventId = idGenerator.nextId();
        long occurredAt = System.currentTimeMillis();
        LocalDateTime createTime = LocalDateTime.now();
        if (mapper.insertNeedEvent(eventId, needId, eventType, actorUid, claimantUid,
                fromStatus, toStatus, targetType, targetId, clean(note, 1000),
                visibilityScope, createTime) != 1) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "failed to append collaboration need timeline event");
        }
        if (publishStateChange) {
            eventPublisher.publish(CollaborationNeedStateChangedEvent.builder()
                    .eventId(eventId)
                    .needId(needId)
                    .eventType(eventType)
                    .actorUid(actorUid)
                    .creatorUid(creatorUid)
                    .claimantUid(claimantUid)
                    .domain(domain)
                    .targetNeedId("MERGED".equals(eventType) ? targetId : null)
                    .targetType(targetType)
                    .targetId(targetId)
                    .note(clean(note, 1000))
                    .fromStatus(fromStatus)
                    .toStatus(toStatus)
                    .occurredAt(occurredAt)
                    .dedupKey("collaboration_need_event:" + eventId)
                    .build());
        }
        return eventId;
    }

    private void publishContribution(Long contributorUid, Integer domain, String contributionType,
                                     Long sourceId, Long targetPostId, String stableKey) {
        eventPublisher.publish(CollaborationContributionAcceptedEvent.builder()
                .contributorUid(contributorUid)
                .domain(domain)
                .contributionType(contributionType)
                .sourceId(sourceId)
                .targetPostId(targetPostId)
                .stableKey(stableKey)
                .occurredAt(System.currentTimeMillis())
                .build());
    }

    private void publishNeedFulfilled(Long contributorUid, NeedRow need, NeedResolution resolution,
                                      String contributionType, String stableKey) {
        eventPublisher.publish(CollaborationContributionAcceptedEvent.builder()
                .contributorUid(contributorUid)
                .domain(need.getDomain())
                .contributionType(contributionType)
                .sourceId(need.getId())
                .targetPostId(resolution.compatibilityPostId())
                .resolutionType(resolution.type())
                .resolutionId(resolution.id())
                .stableKey(stableKey)
                .occurredAt(System.currentTimeMillis())
                .build());
    }

    private record NeedResolution(String type, Long id, Long compatibilityPostId, Long contributorUid) {
    }

    private static BizException invalidState() {
        return new BizException(ErrorCode.INVALID_STATUS);
    }
}
