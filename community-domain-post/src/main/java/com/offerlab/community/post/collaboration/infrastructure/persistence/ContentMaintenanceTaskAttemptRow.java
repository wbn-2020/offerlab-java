package com.offerlab.community.post.collaboration.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContentMaintenanceTaskAttemptRow {
    private Long id;
    private Long taskId;
    private Integer attemptNo;
    private String deliveryType;
    private Long deliveryRefId;
    private Long deliveryPostId;
    private String note;
    private Long submittedByUid;
    private LocalDateTime submittedAt;
    private String decision;
    private String reasonCode;
    private Long reviewedByUid;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
