package com.offerlab.community.analytics.infrastructure.persistence;

import lombok.Data;

@Data
public class ChannelQualityRiskGovernanceSnapshotRootCauseRow {
    private Long id;
    private Long caseId;
    private Long resolutionRevisionId;
    private String causeRole;
    private String category;
    private Integer sequenceNo;
}
