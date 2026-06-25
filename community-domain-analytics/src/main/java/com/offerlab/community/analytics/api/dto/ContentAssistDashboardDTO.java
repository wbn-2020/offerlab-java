package com.offerlab.community.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistDashboardDTO {
    private Integer days;
    private Long totalRequests;
    private Long aiSuccessRequests;
    private Long fallbackRequests;
    private Long ruleOnlyRequests;
    private Long uniqueUsers;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long estimatedCostMicros;
    private List<BucketDTO> sceneStats;
    private List<BucketDTO> statusStats;
    private List<BucketDTO> providerStats;
    private List<BucketDTO> errorStats;
    private List<ErrorSampleDTO> recentErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BucketDTO {
        private String name;
        private Long count;
        private Long estimatedCostMicros;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorSampleDTO {
        private String scene;
        private String provider;
        private String errorCode;
        private LocalDateTime createTime;
    }
}
