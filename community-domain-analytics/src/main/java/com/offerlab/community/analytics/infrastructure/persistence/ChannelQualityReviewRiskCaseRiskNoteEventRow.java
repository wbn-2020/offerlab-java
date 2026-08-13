package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

@Data
public class ChannelQualityReviewRiskCaseRiskNoteEventRow {
    private Long id;
    private Long batchId;
    private String eventType;
    private String riskCode;
    private Integer coordinationVersion;
}
