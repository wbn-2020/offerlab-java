package com.offerlab.community.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatorCurationFeedbackDTO {
    private String eventId;
    private Long contentId;
    private String contentTitle;
    private String placementType;
    private Long placementId;
    private String placementLabel;
    private String topicSlug;
    private String sectionKey;
    private String reasonText;
    private String href;
    private LocalDateTime triggeredAt;
    private String status;
    private String source;
    private CreatorCurationMetricsDTO publicMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorCurationMetricsDTO {
        private Long viewCount;
        private Long likeCount;
        private Long favoriteCount;
        private Long commentCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatorCurationFeedbackSummaryDTO {
        private LocalDateTime updatedAt;
        private boolean degraded;
        private String fallbackReason;
        private int total;
        private List<CreatorCurationFeedbackDTO> items;
        private List<CreatorCurationFeedbackDTO> recentItems;
    }
}
