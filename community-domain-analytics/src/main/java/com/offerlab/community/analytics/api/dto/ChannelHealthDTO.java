package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelHealthDTO {
    private Integer domain;
    private String domainName;
    private Long publicPostCount;
    private Long trustProfileCount;
    private Integer trustProfileCoveragePercent;
    private Long freshnessAwaitingConfirmation;
    private Long pendingSuggestions;
    private Long unresolvedQuestions;
    private Long openContentNeeds;
    private String healthStatus;
    private List<String> attentionReasons;
}
