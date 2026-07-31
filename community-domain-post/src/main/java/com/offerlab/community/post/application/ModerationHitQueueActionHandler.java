package com.offerlab.community.post.application;

import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ModerationHitQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final String SOURCE_TYPE = "MODERATION_HIT";

    private final PostApplicationService postService;

    public ModerationHitQueueActionHandler(@Lazy PostApplicationService postService) {
        this.postService = postService;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        if (!supports(sourceType) || sourceId == null || "CLOSED".equals(normalize(status))) {
            return;
        }
        postService.resolveModerationHit(sourceId, operatorUid, "APPROVED".equals(normalize(status)), note);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
