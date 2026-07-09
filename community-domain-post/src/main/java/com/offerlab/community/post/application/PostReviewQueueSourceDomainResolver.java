package com.offerlab.community.post.application;

import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.moderation.ModerationKeywordHit;
import com.offerlab.community.infra.review.ReviewQueueSourceDomainResolver;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostReportMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostReportPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PostReviewQueueSourceDomainResolver implements ReviewQueueSourceDomainResolver {

    private static final String SOURCE_POST_REPORT = "POST_REPORT";
    private static final String SOURCE_MODERATION_HIT = "MODERATION_HIT";

    private final PostReportMapper postReportMapper;
    private final PostRepository postRepository;
    private final ContentModerationService moderationService;

    @Override
    public boolean supports(String sourceType) {
        String normalized = normalize(sourceType);
        return SOURCE_POST_REPORT.equals(normalized) || SOURCE_MODERATION_HIT.equals(normalized);
    }

    @Override
    public Integer resolveDomain(String sourceType, Long sourceId) {
        if (sourceId == null || sourceId <= 0) {
            return null;
        }
        return switch (normalize(sourceType)) {
            case SOURCE_POST_REPORT -> resolvePostReportDomain(sourceId);
            case SOURCE_MODERATION_HIT -> resolvePostModerationHitDomain(sourceId);
            default -> null;
        };
    }

    private Integer resolvePostReportDomain(Long reportId) {
        PostReportPO report = postReportMapper.selectById(reportId);
        return report == null ? null : resolvePostDomain(report.getPostId());
    }

    private Integer resolvePostModerationHitDomain(Long hitId) {
        ModerationKeywordHit hit = moderationService.findKeywordHit(hitId);
        if (hit == null
                || !ContentModerationService.SCOPE_POST.equalsIgnoreCase(hit.getScope())
                || !ContentModerationService.SOURCE_POST.equalsIgnoreCase(hit.getSourceType())) {
            return null;
        }
        return resolvePostDomain(hit.getSourceId());
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
