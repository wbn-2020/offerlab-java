package com.offerlab.community.infra.review;

public interface ReviewQueuePublisher {
    void upsert(ReviewQueueItemCommand command);

    void resolve(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid);
}
