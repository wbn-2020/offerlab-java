package com.offerlab.community.question.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskMetricsDTO {
    private Integer totalTasks;
    private Integer successCount;
    private Integer failedCount;
    private Integer runningCount;
    private Integer fallbackCount;
    private Double fallbackRate;
    private Long avgDurationMs;
    private Long p95DurationMs;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private Long estimatedCostMicros;
    private List<BucketDTO> providerStats;
    private List<BucketDTO> errorStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BucketDTO {
        private String name;
        private Integer count;
        private Integer fallbackCount;
        private Long avgDurationMs;
        private Long totalTokens;
        private Long estimatedCostMicros;
    }
}
