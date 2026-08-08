package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseRecurrenceLinkRow {
    private Long id;
    private Long currentCaseId;
    private Long previousCaseId;
    private Integer domain;
    private String relationType;
    private String rootCauseCategory;
    private String note;
    private Long linkedByUid;
    private String commandId;
    private String commandFingerprint;
    private LocalDateTime createTime;
}
