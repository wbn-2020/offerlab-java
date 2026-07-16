package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentMaintenanceTaskRow {
    private Long id;
    private Integer domain;
    private String sourceType;
    private Long sourceRefId;
    private Long sourcePostId;
    private Long createdByUid;
    private Long assigneeUid;
    private String title;
    private String detail;
    private String status;
    private String deliveryType;
    private Long deliveryRefId;
    private Long deliveryPostId;
    private String deliveryNote;
    private String reviewNote;
    private LocalDateTime claimedAt;
    private LocalDateTime submittedAt;
    private Long reviewedByUid;
    private LocalDateTime reviewedAt;
    private Long closedByUid;
    private LocalDateTime closedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
