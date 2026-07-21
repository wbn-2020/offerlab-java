package com.offerlab.community.interaction.application;

import com.offerlab.community.infra.review.ReviewQueueSourceDomainResolver;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.PostOutcomeMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.PostOutcomePO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostOutcomeReviewQueueSourceDomainResolver implements ReviewQueueSourceDomainResolver {

    private static final String SOURCE_TYPE = "POST_OUTCOME";

    private final PostOutcomeMapper outcomeMapper;
    private final PostFacade postFacade;

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE.equalsIgnoreCase(sourceType == null ? "" : sourceType.trim());
    }

    @Override
    public Integer resolveDomain(String sourceType, Long sourceId) {
        if (!supports(sourceType) || sourceId == null || sourceId <= 0) {
            return null;
        }
        PostOutcomePO outcome = outcomeMapper.selectActiveById(sourceId);
        if (outcome == null) {
            return null;
        }
        PostDTO post = postFacade.getPostMetadata(outcome.getPostId());
        return post == null ? null : post.getDomain();
    }
}
