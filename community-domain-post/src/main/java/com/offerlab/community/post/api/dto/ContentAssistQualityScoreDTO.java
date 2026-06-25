package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistQualityScoreDTO {
    private Integer score;
    private String level;
    private Boolean advisoryOnly;
    private String summary;
    private List<String> suggestions;
    private List<DimensionDTO> explanations;
    private String provider;
    private Boolean fallbackUsed;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private String errorCode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionDTO {
        private String dimension;
        private Integer score;
        private String reason;
    }
}
