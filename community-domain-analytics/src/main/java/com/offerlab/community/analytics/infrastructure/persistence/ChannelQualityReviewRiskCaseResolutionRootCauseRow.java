package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelQualityReviewRiskCaseResolutionRootCauseRow {
    private Long id;
    private Long caseId;
    private Long resolutionRevisionId;
    private String causeRole;
    private String category;
    private Integer sequenceNo;
    private String note;
    private LocalDateTime createTime;
}
