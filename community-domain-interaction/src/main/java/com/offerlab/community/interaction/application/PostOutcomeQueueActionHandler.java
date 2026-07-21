package com.offerlab.community.interaction.application;

import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PostOutcomeQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final String SOURCE_TYPE = "POST_OUTCOME";
    private final PostOutcomeService outcomeService;

    public PostOutcomeQueueActionHandler(@Lazy PostOutcomeService outcomeService) {
        this.outcomeService = outcomeService;
    }

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result,
                       String note, Long operatorUid) {
        if (!supports(sourceType) || sourceId == null) {
            return;
        }
        String normalized = normalize(status);
        if ("CLOSED".equals(normalized)) {
            outcomeService.review(sourceId, operatorUid, false, note);
            return;
        }
        outcomeService.review(sourceId, operatorUid, "APPROVED".equals(normalized), note);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
