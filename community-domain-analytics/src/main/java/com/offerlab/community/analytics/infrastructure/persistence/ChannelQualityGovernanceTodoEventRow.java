package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceTodoEventRow {
    private Long id;
    private Long todoId;
    private Long caseId;
    private String eventType;
    private String previousStatus;
    private String status;
    private Long assigneeUid;
    private Integer responsibilityEpoch;
    private Long sourceFactId;
    private String reason;
    private String previousEscalationLevel;
    private String escalationLevel;
    private Integer todoVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
