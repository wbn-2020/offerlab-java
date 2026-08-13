package com.offerlab.community.analytics.application;

import java.time.Instant;

public record ChannelQualityReviewRiskCaseV40LifecycleEvent(
        Long eventId,
        String eventType,
        String status,
        Long assignedOwnerUid,
        Integer caseVersion,
        Instant occurredAt) {
}
