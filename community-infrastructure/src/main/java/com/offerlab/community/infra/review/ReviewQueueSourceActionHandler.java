package com.offerlab.community.infra.review;

public interface ReviewQueueSourceActionHandler {
    boolean supports(String sourceType);

    void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid);

    default void handle(String sourceType, Long sourceId, String status, String result, String note,
                        Long operatorUid, String extJson) {
        handle(sourceType, sourceId, status, result, note, operatorUid);
    }
}
