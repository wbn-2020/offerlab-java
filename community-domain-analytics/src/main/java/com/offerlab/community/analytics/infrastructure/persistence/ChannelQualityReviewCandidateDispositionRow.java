package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewCandidateDispositionRow {
    private Long id;
    private Integer domain;
    private String sourceType;
    private Long sourcePostId;
    private Long sourceRefId;
    private String state;
    private String reasonCode;
    private LocalDateTime snoozedUntil;
    private Long updatedByUid;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
