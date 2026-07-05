package com.offerlab.community.post.application;

import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class PostReportQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final String SOURCE_TYPE = "POST_REPORT";

    private final PostReportService reportService;

    public PostReportQueueActionHandler(@Lazy PostReportService reportService) {
        this.reportService = reportService;
    }

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
