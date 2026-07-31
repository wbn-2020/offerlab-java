package com.offerlab.community.interaction.application;

import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.moderation.ModerationKeywordHit;
import com.offerlab.community.infra.review.ReviewQueueSourceDomainResolver;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentReportMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.ContactRequestMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentReportPO;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContactRequestPO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class InteractionReviewQueueSourceDomainResolver implements ReviewQueueSourceDomainResolver {

    private static final String SOURCE_COMMENT_REPORT = "COMMENT_REPORT";
    private static final String SOURCE_CONTACT_REQUEST_REPORT = "CONTACT_REQUEST_REPORT";
    private static final String SOURCE_MODERATION_HIT = "MODERATION_HIT";

    private final CommentReportMapper commentReportMapper;
    private final CommentMapper commentMapper;
    private final ContactRequestMapper contactRequestMapper;
    private final PostRepository postRepository;
    private final ContentModerationService moderationService;

    @Override
    public boolean supports(String sourceType) {
        String normalized = normalize(sourceType);
        return SOURCE_COMMENT_REPORT.equals(normalized)
                || SOURCE_CONTACT_REQUEST_REPORT.equals(normalized)
                || SOURCE_MODERATION_HIT.equals(normalized);
    }

    @Override
    public Integer resolveDomain(String sourceType, Long sourceId) {
        if (sourceId == null || sourceId <= 0) {
            return null;
        }
        return switch (normalize(sourceType)) {
            case SOURCE_COMMENT_REPORT -> resolveCommentReportDomain(sourceId);
            case SOURCE_CONTACT_REQUEST_REPORT -> resolveContactRequestReportDomain(sourceId);
            case SOURCE_MODERATION_HIT -> resolveCommentModerationHitDomain(sourceId);
            default -> null;
        };
    }

    private Integer resolveCommentReportDomain(Long reportId) {
        CommentReportPO report = commentReportMapper.selectById(reportId);
        return report == null ? null : resolvePostDomain(report.getPostId());
    }

    private Integer resolveContactRequestReportDomain(Long requestId) {
        ContactRequestPO request = contactRequestMapper.selectActiveById(requestId);
        if (request == null || request.getSourceType() == null) {
            return null;
        }
        return switch (request.getSourceType()) {
            case "post" -> resolvePostDomain(request.getSourceId());
            case "comment" -> resolveCommentSourceDomain(request.getSourceId());
            default -> null;
        };
    }

    private Integer resolveCommentModerationHitDomain(Long hitId) {
        ModerationKeywordHit hit = moderationService.findKeywordHit(hitId);
        if (hit == null
                || !ContentModerationService.SCOPE_COMMENT.equalsIgnoreCase(hit.getScope())
                || !ContentModerationService.SOURCE_COMMENT.equalsIgnoreCase(hit.getSourceType())
                || hit.getSourceId() == null) {
            return null;
        }
        CommentPO comment = commentMapper.selectById(hit.getSourceId());
        return comment == null ? null : resolvePostDomain(comment.getPostId());
    }

    private Integer resolveCommentSourceDomain(Long commentId) {
        if (commentId == null || commentId <= 0) {
            return null;
        }
        CommentPO comment = commentMapper.selectById(commentId);
        return comment == null ? null : resolvePostDomain(comment.getPostId());
    }

    private Integer resolvePostDomain(Long postId) {
        if (postId == null || postId <= 0) {
            return null;
        }
        return postRepository.findById(postId).map(Post::getDomain).orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
