package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PostPendingReviewQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PostApplicationService postService;

    public PostPendingReviewQueueActionHandler(@Lazy PostApplicationService postService) {
        this.postService = postService;
    }

    @Override
    public boolean supports(String sourceType) {
        return PostApplicationService.PENDING_REVIEW_SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        handle(sourceType, sourceId, status, result, note, operatorUid, null);
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note,
                       Long operatorUid, String extJson) {
        if (!supports(sourceType) || sourceId == null || "CLOSED".equals(normalize(status))) {
            return;
        }
        String normalizedStatus = normalize(status);
        if (!"APPROVED".equals(normalizedStatus)
                && !"REJECTED".equals(normalizedStatus)) {
            return;
        }
        postService.resolvePendingPostReview(
                sourceId,
                operatorUid,
                "APPROVED".equals(normalizedStatus),
                note,
                expectedVersion(extJson)
        );
    }

    private Integer expectedVersion(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return null;
        }
        try {
            JsonNode version = JSON.readTree(extJson).get("version");
            return version != null && version.canConvertToInt() ? version.asInt() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
