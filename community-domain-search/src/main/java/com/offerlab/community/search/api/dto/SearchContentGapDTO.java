package com.offerlab.community.search.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchContentGapDTO {
    private String gapId;
    private String keyword;
    private String clusterId;
    private String reasonText;
    private Integer windowDays;
    private Long searchCount;
    private Long noResultCount;
    private Long weakResultCount;
    private Boolean minSampleMet;
    private RiskLevel riskLevel;
    private TargetStage targetStage;
    private GapStatus status;
    private String source;
    private List<String> sourceRefs;
    private CreatedFrom createdFrom;
    private String lastSeenAt;

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum TargetStage {
        workspace,
        editor,
        topic,
        knowledge
    }

    public enum GapStatus {
        CANDIDATE,
        APPROVED,
        IGNORED,
        REVIEW_REQUIRED
    }

    public enum CreatedFrom {
        no_result,
        weak_result,
        hot_keyword,
        manual_review
    }
}
