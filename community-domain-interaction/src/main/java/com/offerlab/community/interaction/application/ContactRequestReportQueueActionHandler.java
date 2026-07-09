package com.offerlab.community.interaction.application;

import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ContactRequestReportQueueActionHandler implements ReviewQueueSourceActionHandler {

    private static final String SOURCE_TYPE = "CONTACT_REQUEST_REPORT";

    private final ContactRequestService contactRequestService;

    public ContactRequestReportQueueActionHandler(@Lazy ContactRequestService contactRequestService) {
        this.contactRequestService = contactRequestService;
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
        contactRequestService.resolveReportFromQueue(sourceId, operatorUid, normalize(status), note);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
