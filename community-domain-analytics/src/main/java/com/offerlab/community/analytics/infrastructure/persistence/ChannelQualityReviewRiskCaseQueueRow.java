package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseQueueRow {
    private String queueSource;
    private Integer queuePriority;
    private LocalDateTime queueUpdateTime;
    private Long queueRowId;
    private Long caseId;
    private Long batchId;
    private Integer domain;
    private String batchName;
    private String priority;
    private LocalDateTime dueAt;
    private LocalDateTime effectiveDueAt;
    private Integer coordinationVersion;
    private Integer candidateCount;
    private Integer activeTaskCount;
    private String triggerType;
    private Long riskEventId;
    private String riskCode;
    private String dueState;
    private String status;
    private Long ownerUid;
    private Integer caseVersion;
    private Integer openedCoordinationVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
