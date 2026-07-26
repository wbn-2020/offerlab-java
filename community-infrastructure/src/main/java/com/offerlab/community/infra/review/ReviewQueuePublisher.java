package com.offerlab.community.infra.review;

public interface ReviewQueuePublisher {
    void upsert(ReviewQueueItemCommand command);

    default void reopen(ReviewQueueItemCommand command) {
        upsert(command);
    }

    void resolve(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid);

    default void resolveRequired(String sourceType, Long sourceId, String status, String result,
                                 String note, Long operatorUid) {
        resolve(sourceType, sourceId, status, result, note, operatorUid);
    }
}
