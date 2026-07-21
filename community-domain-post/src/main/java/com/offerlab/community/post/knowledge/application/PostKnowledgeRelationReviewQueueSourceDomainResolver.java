package com.offerlab.community.post.knowledge.application;

import com.offerlab.community.infra.review.ReviewQueueSourceDomainResolver;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PostKnowledgeRelationReviewQueueSourceDomainResolver
        implements ReviewQueueSourceDomainResolver {

    private final PostKnowledgeRelationMapper mapper;
    private final PostRepository postRepository;

    @Override
    public boolean supports(String sourceType) {
        return PostKnowledgeRelationService.REVIEW_SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public Integer resolveDomain(String sourceType, Long sourceId) {
        if (!supports(sourceType) || sourceId == null || sourceId <= 0) {
            return null;
        }
        PostKnowledgeRelationRow relation = mapper.findActiveById(sourceId);
        if (relation == null || relation.getSourcePostId() == null) {
            return null;
        }
        return postRepository.findById(relation.getSourcePostId())
                .map(Post::getDomain)
                .orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
