package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityGovernanceTodoRow {
    private Long id;
    private Long caseId;
    private Long retrospectiveId;
    private Integer domain;
    private String taskType;
    private String sourceScope;
    private Integer responsibilityEpoch;
    private Long sourceFactId;
    private Long assigneeUid;
    private String status;
    private LocalDateTime anchorAt;
    private LocalDateTime dueAt;
    private String policySource;
    private String policyKey;
    private String policyVersion;
    private Integer slaMinutes;
    private Long completionFactId;
    private String completionReason;
    private LocalDateTime completedAt;
    private String closeReason;
    private LocalDateTime closedAt;
    private String escalationLevel;
    private Long lastEscalationEventId;
    private LocalDateTime escalatedAt;
    private Integer todoVersion;
    private Integer activeMarker;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
