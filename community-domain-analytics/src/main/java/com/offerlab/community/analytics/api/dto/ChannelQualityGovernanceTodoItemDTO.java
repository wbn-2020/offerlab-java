package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ChannelQualityGovernanceTodoItemDTO {
    private Long todoId;
    private Long caseId;
    private Long retrospectiveId;
    private Integer domain;
    private String taskType;
    private String status;
    private Instant anchorAt;
    private Instant dueAt;
    private String dueState;
    private Boolean isOverdue;
    private String slaOutcome;
    private String completionReason;
    private String closeReason;
    private String escalationLevel;
    private String actionability;
    private Boolean canOpenSource;
    private String actionPath;
    private Integer todoVersion;
}
