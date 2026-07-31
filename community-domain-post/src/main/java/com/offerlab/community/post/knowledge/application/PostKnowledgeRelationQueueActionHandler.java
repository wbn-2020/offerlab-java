package com.offerlab.community.post.knowledge.application;

import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PostKnowledgeRelationQueueActionHandler implements ReviewQueueSourceActionHandler {

    private final PostKnowledgeRelationService service;

    public PostKnowledgeRelationQueueActionHandler(@Lazy PostKnowledgeRelationService service) {
        this.service = service;
    }

    @Override
    public boolean supports(String sourceType) {
        return PostKnowledgeRelationService.REVIEW_SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public void handle(String sourceType,
                       Long sourceId,
                       String status,
                       String result,
                       String note,
                       Long operatorUid) {
        if (!supports(sourceType) || sourceId == null) {
            return;
        }
        String normalizedStatus = normalize(status);
        if (!"APPROVED".equals(normalizedStatus)
                && !"REJECTED".equals(normalizedStatus)
                && !"CLOSED".equals(normalizedStatus)) {
            return;
        }
        service.reviewFromQueue(sourceId, operatorUid, "APPROVED".equals(normalizedStatus), note);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
