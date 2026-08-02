package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorChallengeAdminActionCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeAdminCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeCompleteCmd;
import com.offerlab.community.analytics.api.dto.CreatorChallengeCompletionResultDTO;
import com.offerlab.community.analytics.api.dto.CreatorChallengeWorkspaceDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.CreatorGrowthChallengeMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgeAwardPO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgeAwardRow;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgePO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengePO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengeParticipationPO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengeWorkspaceRow;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CreatorChallengeService {
    private static final int WORKSPACE_POST_LIMIT = 6;
    private static final int ADMIN_CHALLENGE_LIMIT = 100;
    private static final String AUDIT_RESOURCE = "CREATOR_GROWTH_CHALLENGE";
    private static final String BADGE_BOUNDARY =
            "该标识只说明已完成公开创作挑战，不代表专业认证、平台背书、排名、收益或额外权益。";

    private final CreatorGrowthChallengeMapper challengeMapper;
    private final GrowthInsightMapper growthInsightMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    public CreatorChallengeWorkspaceDTO workspace(Long uid) {
        requireUser(uid);
        List<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> challenges = challengeMapper.selectWorkspace(uid).stream()
                .map(row -> toWorkspaceChallenge(uid, row))
                .toList();
        return CreatorChallengeWorkspaceDTO.builder()
                .challenges(challenges)
                .badges(awardedBadges(uid))
                .boundaryCopy("挑战需要自愿加入并绑定本人公开内容完成；不提供积分、日更打卡、排名、收益或认证背书。")
                .build();
    }

    @Transactional
    public CreatorChallengeWorkspaceDTO.CreatorChallengeDTO join(Long uid, Long challengeId) {
        requireUser(uid);
        CreatorGrowthChallengePO challenge = requireChallengeForJoin(challengeId);
        requireActiveChallenge(challenge, LocalDateTime.now());
        CreatorGrowthChallengeParticipationPO participation = challengeMapper.lockParticipation(challengeId, uid);
        if (participation == null) {
            participation = new CreatorGrowthChallengeParticipationPO();
            participation.setId(idGenerator.nextId());
            participation.setChallengeId(challengeId);
            participation.setUid(uid);
            challengeMapper.insertParticipation(participation);
        } else if (!"JOINED".equals(participation.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_NOT_JOINABLE");
        }
        return workspaceChallenge(uid, challengeId);
    }

    @Transactional
    public CreatorChallengeWorkspaceDTO.CreatorChallengeDTO withdraw(Long uid, Long challengeId) {
        requireUser(uid);
        CreatorGrowthChallengeParticipationPO participation = challengeMapper.lockParticipation(challengeId, uid);
        if (participation == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("WITHDRAWN".equals(participation.getStatus())) {
            return workspaceChallenge(uid, challengeId);
        }
        if (!"JOINED".equals(participation.getStatus())
                || challengeMapper.withdrawParticipation(participation.getId(), uid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_NOT_WITHDRAWABLE");
        }
        return workspaceChallenge(uid, challengeId);
    }

    @Transactional
    public CreatorChallengeCompletionResultDTO complete(Long uid, Long challengeId, CreatorChallengeCompleteCmd cmd) {
        requireUser(uid);
        if (cmd == null || cmd.getPostId() == null || cmd.getPostId() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CreatorGrowthChallengePO challenge = challengeMapper.lockChallengeById(challengeId);
        if (challenge == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        requireActiveChallenge(challenge, LocalDateTime.now());
        CreatorGrowthChallengeParticipationPO participation = challengeMapper.lockParticipation(challengeId, uid);
        if (participation == null) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_JOIN_REQUIRED");
        }
        if ("COMPLETED".equals(participation.getStatus())) {
            return CreatorChallengeCompletionResultDTO.builder()
                    .challenge(workspaceChallenge(uid, challengeId))
                    .newlyAwardedBadges(List.of())
                    .replayed(true)
                    .build();
        }
        if (!"JOINED".equals(participation.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_NOT_COMPLETABLE");
        }
        requireEligibleCompletionPost(uid, challenge, cmd.getPostId());
        if (challengeMapper.completeParticipation(participation.getId(), uid, cmd.getPostId()) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_COMPLETION_UNAVAILABLE");
        }
        List<CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO> newBadges =
                awardEligibleBadges(uid, challengeId);
        return CreatorChallengeCompletionResultDTO.builder()
                .challenge(workspaceChallenge(uid, challengeId))
                .newlyAwardedBadges(newBadges)
                .replayed(false)
                .build();
    }

    public List<CreatorChallengeWorkspaceDTO.CreatorChallengeDTO> adminChallenges(Long operatorUid) {
        requireOperations(operatorUid);
        return challengeMapper.selectAdminChallenges(ADMIN_CHALLENGE_LIMIT).stream()
                .map(this::toAdminChallenge)
                .toList();
    }

    @Transactional
    public CreatorChallengeWorkspaceDTO.CreatorChallengeDTO upsert(
            Long operatorUid,
            CreatorChallengeAdminCmd cmd) {
        requireOperations(operatorUid);
        CreatorGrowthChallengePO candidate = commandToChallenge(cmd, operatorUid);
        adminAuditService.requireWritable("CREATOR_CHALLENGE_UPSERT", AUDIT_RESOURCE,
                candidate.getId() == null ? candidate.getChallengeCode() : candidate.getId());
        if (candidate.getId() == null) {
            if (challengeMapper.selectChallengeByCode(candidate.getChallengeCode()) != null) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(), "CREATOR_CHALLENGE_CODE_EXISTS");
            }
            candidate.setId(idGenerator.nextId());
            challengeMapper.insertChallenge(candidate);
            adminAuditService.recordRequired(operatorUid, "CREATOR_CHALLENGE_CREATE", AUDIT_RESOURCE,
                    candidate.getId(), null, auditSummary(candidate), cleanReason(cmd.getReason()));
            return toAdminChallenge(candidate);
        }

        CreatorGrowthChallengePO current = challengeMapper.lockChallengeById(candidate.getId());
        if (current == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Objects.equals(current.getChallengeCode(), candidate.getChallengeCode())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "CREATOR_CHALLENGE_CODE_IMMUTABLE");
        }
        if (!"DRAFT".equals(current.getStatus())
                || challengeMapper.updateDraftChallenge(candidate) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_DRAFT_REQUIRED");
        }
        CreatorGrowthChallengePO updated = challengeMapper.selectChallengeById(candidate.getId());
        adminAuditService.recordRequired(operatorUid, "CREATOR_CHALLENGE_UPDATE", AUDIT_RESOURCE,
                candidate.getId(), auditSummary(current), auditSummary(updated), cleanReason(cmd.getReason()));
        return toAdminChallenge(updated);
    }

    @Transactional
    public CreatorChallengeWorkspaceDTO.CreatorChallengeDTO publish(
            Long operatorUid,
            Long challengeId,
            CreatorChallengeAdminActionCmd cmd) {
        requireOperations(operatorUid);
        String reason = cleanReason(cmd == null ? null : cmd.getReason());
        CreatorGrowthChallengePO current = requireChallengeForAdmin(challengeId, operatorUid,
                "CREATOR_CHALLENGE_PUBLISH", reason);
        if (!"DRAFT".equals(current.getStatus()) || !current.getEndsAt().isAfter(LocalDateTime.now())
                || challengeMapper.publishChallenge(challengeId, operatorUid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_NOT_PUBLISHABLE");
        }
        CreatorGrowthChallengePO updated = challengeMapper.selectChallengeById(challengeId);
        adminAuditService.recordRequired(operatorUid, "CREATOR_CHALLENGE_PUBLISH", AUDIT_RESOURCE,
                challengeId, auditSummary(current), auditSummary(updated), reason);
        return toAdminChallenge(updated);
    }

    @Transactional
    public CreatorChallengeWorkspaceDTO.CreatorChallengeDTO offline(
            Long operatorUid,
            Long challengeId,
            CreatorChallengeAdminActionCmd cmd) {
        requireOperations(operatorUid);
        String reason = cleanReason(cmd == null ? null : cmd.getReason());
        CreatorGrowthChallengePO current = requireChallengeForAdmin(challengeId, operatorUid,
                "CREATOR_CHALLENGE_OFFLINE", reason);
        if (!"PUBLISHED".equals(current.getStatus())
                || challengeMapper.offlineChallenge(challengeId, operatorUid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_NOT_OFFLINEABLE");
        }
        CreatorGrowthChallengePO updated = challengeMapper.selectChallengeById(challengeId);
        adminAuditService.recordRequired(operatorUid, "CREATOR_CHALLENGE_OFFLINE", AUDIT_RESOURCE,
                challengeId, auditSummary(current), auditSummary(updated), reason);
        return toAdminChallenge(updated);
    }

    private CreatorGrowthChallengePO requireChallengeForAdmin(
            Long challengeId,
            Long operatorUid,
            String action,
            String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CreatorGrowthChallengePO current = challengeMapper.lockChallengeById(challengeId);
        if (current == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        adminAuditService.requireWritable(action, AUDIT_RESOURCE, challengeId);
        return current;
    }

    private CreatorGrowthChallengePO commandToChallenge(CreatorChallengeAdminCmd cmd, Long operatorUid) {
        if (cmd == null || !StringUtils.hasText(cmd.getChallengeCode())
                || !StringUtils.hasText(cmd.getTitle())
                || !StringUtils.hasText(cmd.getDescription())
                || cmd.getStartsAt() == null
                || cmd.getEndsAt() == null
                || !cmd.getEndsAt().isAfter(cmd.getStartsAt())
                || !StringUtils.hasText(cleanReason(cmd.getReason()))) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CreatorGrowthChallengePO challenge = new CreatorGrowthChallengePO();
        challenge.setId(cmd.getId() != null && cmd.getId() > 0 ? cmd.getId() : null);
        challenge.setChallengeCode(cmd.getChallengeCode().trim().toUpperCase());
        challenge.setTitle(cmd.getTitle().trim());
        challenge.setDescription(cmd.getDescription().trim());
        challenge.setDomain(cmd.getDomain());
        challenge.setPostType(cmd.getPostType());
        challenge.setAssistTemplateCode(clean(cmd.getAssistTemplateCode()));
        challenge.setStartsAt(cmd.getStartsAt());
        challenge.setEndsAt(cmd.getEndsAt());
        challenge.setOperatorUid(operatorUid);
        return challenge;
    }

    private CreatorChallengeWorkspaceDTO.CreatorChallengeDTO workspaceChallenge(Long uid, Long challengeId) {
        return challengeMapper.selectWorkspace(uid).stream()
                .filter(item -> Objects.equals(item.getId(), challengeId))
                .findFirst()
                .map(row -> toWorkspaceChallenge(uid, row))
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private CreatorChallengeWorkspaceDTO.CreatorChallengeDTO toWorkspaceChallenge(
            Long uid,
            CreatorGrowthChallengeWorkspaceRow row) {
        List<CreatorChallengeWorkspaceDTO.EligiblePostDTO> eligiblePosts =
                "JOINED".equals(row.getParticipationStatus()) && isActive(row.getStatus(), row.getStartsAt(), row.getEndsAt())
                        ? eligiblePosts(uid, row.getStartsAt(), row.getEndsAt(), row.getDomain(), row.getPostType())
                        : List.of();
        return CreatorChallengeWorkspaceDTO.CreatorChallengeDTO.builder()
                .id(row.getId())
                .challengeCode(row.getChallengeCode())
                .title(row.getTitle())
                .description(row.getDescription())
                .domain(row.getDomain())
                .postType(row.getPostType())
                .assistTemplateCode(row.getAssistTemplateCode())
                .status(row.getStatus())
                .startsAt(row.getStartsAt())
                .endsAt(row.getEndsAt())
                .participationStatus(row.getParticipationStatus())
                .joinedAt(row.getJoinedAt())
                .completedAt(row.getCompletedAt())
                .completedPostId(row.getCompletedPostId())
                .eligiblePosts(eligiblePosts)
                .build();
    }

    private CreatorChallengeWorkspaceDTO.CreatorChallengeDTO toAdminChallenge(CreatorGrowthChallengePO challenge) {
        return CreatorChallengeWorkspaceDTO.CreatorChallengeDTO.builder()
                .id(challenge.getId())
                .challengeCode(challenge.getChallengeCode())
                .title(challenge.getTitle())
                .description(challenge.getDescription())
                .domain(challenge.getDomain())
                .postType(challenge.getPostType())
                .assistTemplateCode(challenge.getAssistTemplateCode())
                .status(challenge.getStatus())
                .startsAt(challenge.getStartsAt())
                .endsAt(challenge.getEndsAt())
                .build();
    }

    private List<CreatorChallengeWorkspaceDTO.EligiblePostDTO> eligiblePosts(
            Long uid,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Integer domain,
            Integer postType) {
        return growthInsightMapper.selectEligibleCreatorChallengePosts(
                        uid, startsAt, endsAt, domain, postType, WORKSPACE_POST_LIMIT)
                .stream()
                .map(this::toEligiblePost)
                .filter(item -> item.getPostId() != null)
                .toList();
    }

    private void requireEligibleCompletionPost(Long uid, CreatorGrowthChallengePO challenge, Long postId) {
        Map<String, Object> post = growthInsightMapper.selectCreatorChallengeCompletionPost(uid, postId);
        if (post == null || !Objects.equals(longValue(post.get("postId")), postId)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "CREATOR_CHALLENGE_POST_INELIGIBLE");
        }
        LocalDateTime publishedAt = dateTime(post.get("publishedAt"));
        if (publishedAt == null || publishedAt.isBefore(challenge.getStartsAt())
                || publishedAt.isAfter(challenge.getEndsAt())
                || (challenge.getDomain() != null
                && !Objects.equals(challenge.getDomain(), integerValue(post.get("domain"))))
                || (challenge.getPostType() != null
                && !Objects.equals(challenge.getPostType(), integerValue(post.get("postType"))))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "CREATOR_CHALLENGE_POST_INELIGIBLE");
        }
    }

    private List<CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO> awardEligibleBadges(
            Long uid,
            Long sourceChallengeId) {
        long completedCount = challengeMapper.countCompletedChallenges(uid);
        List<CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO> newBadges = new ArrayList<>();
        for (CreatorGrowthBadgePO badge : challengeMapper.selectEligibleBadges(completedCount)) {
            CreatorGrowthBadgeAwardPO award = new CreatorGrowthBadgeAwardPO();
            award.setId(idGenerator.nextId());
            award.setUid(uid);
            award.setBadgeId(badge.getId());
            award.setSourceChallengeId(sourceChallengeId);
            if (challengeMapper.insertBadgeAward(award) == 1) {
                newBadges.add(toBadge(badge, LocalDateTime.now()));
            }
        }
        return List.copyOf(newBadges);
    }

    private List<CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO> awardedBadges(Long uid) {
        return challengeMapper.selectAwardedBadges(uid).stream()
                .map(this::toBadge)
                .toList();
    }

    private CreatorChallengeWorkspaceDTO.EligiblePostDTO toEligiblePost(Map<String, Object> row) {
        return CreatorChallengeWorkspaceDTO.EligiblePostDTO.builder()
                .postId(longValue(row.get("postId")))
                .title(text(row.get("title")))
                .domain(integerValue(row.get("domain")))
                .postType(integerValue(row.get("postType")))
                .publishedAt(dateTime(row.get("publishedAt")))
                .build();
    }

    private CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO toBadge(CreatorGrowthBadgePO badge,
                                                                            LocalDateTime awardedAt) {
        return CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO.builder()
                .badgeCode(badge.getBadgeCode())
                .title(badge.getTitle())
                .description(badge.getDescription())
                .requiredCompletedChallengeCount(badge.getRequiredCompletedChallengeCount())
                .awardedAt(awardedAt)
                .boundaryCopy(BADGE_BOUNDARY)
                .build();
    }

    private CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO toBadge(CreatorGrowthBadgeAwardRow row) {
        return CreatorChallengeWorkspaceDTO.CreatorChallengeBadgeDTO.builder()
                .badgeCode(row.getBadgeCode())
                .title(row.getTitle())
                .description(row.getDescription())
                .requiredCompletedChallengeCount(row.getRequiredCompletedChallengeCount())
                .awardedAt(row.getAwardedAt())
                .boundaryCopy(BADGE_BOUNDARY)
                .build();
    }

    private CreatorGrowthChallengePO requireChallengeForJoin(Long challengeId) {
        if (challengeId == null || challengeId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CreatorGrowthChallengePO challenge = challengeMapper.lockChallengeById(challengeId);
        if (challenge == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return challenge;
    }

    private static void requireActiveChallenge(CreatorGrowthChallengePO challenge, LocalDateTime now) {
        if (!isActive(challenge.getStatus(), challenge.getStartsAt(), challenge.getEndsAt(), now)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(), "CREATOR_CHALLENGE_NOT_ACTIVE");
        }
    }

    private static boolean isActive(String status, LocalDateTime startsAt, LocalDateTime endsAt) {
        return isActive(status, startsAt, endsAt, LocalDateTime.now());
    }

    private static boolean isActive(String status, LocalDateTime startsAt, LocalDateTime endsAt, LocalDateTime now) {
        return "PUBLISHED".equals(status)
                && startsAt != null
                && endsAt != null
                && !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }

    private void requireOperations(Long operatorUid) {
        adminPermissionService.requireScope(operatorUid, AdminPermissionService.ROLE_OPS);
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Map<String, Object> auditSummary(CreatorGrowthChallengePO challenge) {
        if (challenge == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", challenge.getId());
        result.put("challengeCode", challenge.getChallengeCode());
        result.put("status", challenge.getStatus());
        result.put("domain", challenge.getDomain());
        result.put("postType", challenge.getPostType());
        result.put("startsAt", challenge.getStartsAt());
        result.put("endsAt", challenge.getEndsAt());
        return result;
    }

    private static String cleanReason(String value) {
        String result = clean(value);
        return result != null && result.length() >= 2 && result.length() <= 500 ? result : null;
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        try {
            return value == null ? null : LocalDateTime.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
