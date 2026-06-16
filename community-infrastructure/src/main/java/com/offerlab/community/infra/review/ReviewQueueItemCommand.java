package com.offerlab.community.infra.review;

public record ReviewQueueItemCommand(
        String sourceType,
        Long sourceId,
        String title,
        String summary,
        String riskLevel,
        Long creatorUid,
        Integer priority,
        String extJson,
        String note
) {
}
