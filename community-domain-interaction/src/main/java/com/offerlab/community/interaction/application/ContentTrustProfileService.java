package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.interaction.api.dto.TrustProfileDTO;
import com.offerlab.community.interaction.api.dto.TrustProfileRole;
import com.offerlab.community.interaction.api.dto.TrustProfileUpdateCmd;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.ContentTrustProfileMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContentTrustProfilePO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContentTrustProfileService {

    private final ContentTrustProfileMapper profileMapper;
    private final PostFacade postFacade;

    @Transactional(readOnly = true)
    public TrustProfileDTO getProfile(Long postId, Long viewerUid) {
        PostDTO post = requireReadablePost(postId, viewerUid);
        ContentTrustProfilePO profile = profileMapper.selectByPostId(postId);
        return profile == null ? unavailable(post) : toDto(profile, true);
    }

    @Transactional
    public TrustProfileDTO updateProfile(Long postId, Long actorUid, TrustProfileUpdateCmd cmd) {
        requirePositive(actorUid);
        PostDTO post = requireOwnedPost(postId, actorUid);
        ContentTrustProfilePO profile = toProfile(post, cmd);
        profileMapper.upsert(profile);
        ContentTrustProfilePO saved = profileMapper.selectByPostId(postId);
        if (saved == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        return toDto(saved, true);
    }

    private PostDTO requireReadablePost(Long postId, Long viewerUid) {
        requirePositive(postId);
        PostDTO post = postFacade.getPost(postId, viewerUid);
        if (post == null && viewerUid != null) {
            post = postFacade.getPostForAuthor(postId, viewerUid);
        }
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private PostDTO requireOwnedPost(Long postId, Long actorUid) {
        requirePositive(postId);
        PostDTO post = postFacade.getPostForAuthor(postId, actorUid);
        if (post == null) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private static ContentTrustProfilePO toProfile(PostDTO post, TrustProfileUpdateCmd cmd) {
        if (cmd == null || cmd.getAuthorRole() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (cmd.getExperienceStartAt() != null
                && cmd.getExperienceEndAt() != null
                && cmd.getExperienceEndAt().isBefore(cmd.getExperienceStartAt())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "experience end time cannot be before the start time");
        }
        LocalDateTime now = LocalDateTime.now();
        if ((cmd.getExperienceStartAt() != null && cmd.getExperienceStartAt().isAfter(now))
                || (cmd.getExperienceEndAt() != null && cmd.getExperienceEndAt().isAfter(now))) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "experience time cannot be in the future");
        }
        ContentTrustProfilePO profile = new ContentTrustProfilePO();
        profile.setPostId(post.getId());
        profile.setAuthorUid(post.getAuthorId());
        profile.setAuthorRole(cmd.getAuthorRole().name());
        profile.setExperienceStartAt(cmd.getExperienceStartAt());
        profile.setExperienceEndAt(cmd.getExperienceEndAt());
        profile.setApplicableAudience(normalize(cmd.getApplicableAudience(), 500));
        profile.setApplicableContext(normalize(cmd.getApplicableContext(), 1000));
        profile.setProcessSummary(normalize(cmd.getProcessSummary(), 2000));
        profile.setOutcomeSummary(normalize(cmd.getOutcomeSummary(), 2000));
        profile.setKnownLimitations(normalize(cmd.getKnownLimitations(), 2000));
        profile.setSourceSummary(normalize(cmd.getSourceSummary(), 1000));
        profile.setInterestDisclosure(normalize(cmd.getInterestDisclosure(), 1000));
        profile.setCompletenessScore(completeness(profile));
        return profile;
    }

    private static int completeness(ContentTrustProfilePO profile) {
        int score = 15;
        score += points(profile.getApplicableAudience(), 15);
        score += points(profile.getApplicableContext(), 15);
        score += points(profile.getProcessSummary(), 20);
        score += points(profile.getOutcomeSummary(), 15);
        score += points(profile.getKnownLimitations(), 10);
        score += points(profile.getSourceSummary(), 5);
        score += points(profile.getInterestDisclosure(), 5);
        return Math.min(score, 100);
    }

    private static int points(String value, int points) {
        return value == null || value.isBlank() ? 0 : points;
    }

    private static TrustProfileDTO unavailable(PostDTO post) {
        return TrustProfileDTO.builder()
                .postId(post.getId())
                .authorUid(post.getAuthorId())
                .profileAvailable(false)
                .completenessScore(0)
                .build();
    }

    private static TrustProfileDTO toDto(ContentTrustProfilePO profile, boolean available) {
        return TrustProfileDTO.builder()
                .postId(profile.getPostId())
                .authorUid(profile.getAuthorUid())
                .profileAvailable(available)
                .authorRole(enumValue(profile.getAuthorRole()))
                .experienceStartAt(profile.getExperienceStartAt())
                .experienceEndAt(profile.getExperienceEndAt())
                .applicableAudience(profile.getApplicableAudience())
                .applicableContext(profile.getApplicableContext())
                .processSummary(profile.getProcessSummary())
                .outcomeSummary(profile.getOutcomeSummary())
                .knownLimitations(profile.getKnownLimitations())
                .sourceSummary(profile.getSourceSummary())
                .interestDisclosure(profile.getInterestDisclosure())
                .completenessScore(profile.getCompletenessScore())
                .lastConfirmedAt(profile.getLastConfirmedAt())
                .profileVersion(profile.getProfileVersion())
                .createTime(profile.getCreateTime())
                .updateTime(profile.getUpdateTime())
                .build();
    }

    private static TrustProfileRole enumValue(String raw) {
        try {
            return raw == null ? null : TrustProfileRole.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength || containsControl(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static boolean containsControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static void requirePositive(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }
}
