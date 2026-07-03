package com.offerlab.community.interaction.application;

import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CommentReportQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final String SOURCE_TYPE = "COMMENT_REPORT";

    private final CommentReportService reportService;

    @Override
    public boolean supports(String sourceType) {
        return SOURCE_TYPE.equals(normalize(sourceType));
    }

    @Override
    public void handle(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        if (!supports(sourceType) || sourceId == null) {
            return;
        }
        reportService.reviewReport(sourceId, operatorUid, isApproved(status), note);
    }

    private static boolean isApproved(String status) {
        return "APPROVED".equals(normalize(status));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
