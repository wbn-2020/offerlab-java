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
public class ChannelQualityReviewCandidatePageDTO {
    private Boolean available;
    private String nextCursor;
    private Integer suppressedCount;
    private List<ChannelQualityReviewCandidateDTO> items;

    public static ChannelQualityReviewCandidatePageDTO unavailable() {
        return ChannelQualityReviewCandidatePageDTO.builder()
                .available(false)
                .nextCursor(null)
                .suppressedCount(0)
                .items(List.of())
                .build();
    }

    public static ChannelQualityReviewCandidatePageDTO available(
            String nextCursor, List<ChannelQualityReviewCandidateDTO> items) {
        return available(nextCursor, 0, items);
    }

    public static ChannelQualityReviewCandidatePageDTO available(
            String nextCursor, int suppressedCount, List<ChannelQualityReviewCandidateDTO> items) {
        return ChannelQualityReviewCandidatePageDTO.builder()
                .available(true)
                .nextCursor(nextCursor)
                .suppressedCount(Math.max(0, suppressedCount))
                .items(items == null ? List.of() : List.copyOf(items))
                .build();
    }
}
