package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceReminderAttemptRow {
    private Long id;
    private Long reminderId;
    private Integer attemptNo;
    private String requestEventKey;
    private String outcome;
    private String policyDecision;
    private LocalDateTime retryAfter;
    private Long notificationId;
    private String errorCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
