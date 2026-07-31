package com.offerlab.community.search.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchContentGapDTO {
    private Long id;
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
    private Integer domain;
    private Long reviewedBy;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private Long convertedNeedId;
    private String resolutionType;
    private Long resolutionId;
    private Long resolutionPostId;
    private LocalDateTime fulfilledAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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
        REVIEW_REQUIRED,
        CONVERTED,
        FULFILLED
    }

    public enum CreatedFrom {
        no_result,
        weak_result,
        hot_keyword,
        manual_review
    }
}
