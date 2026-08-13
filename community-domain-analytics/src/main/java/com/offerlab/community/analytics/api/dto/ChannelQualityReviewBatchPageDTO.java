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
public class ChannelQualityReviewBatchPageDTO {
    private Boolean available;
    private Long nextCursor;
    private List<ChannelQualityReviewBatchDTO> items;

    public static ChannelQualityReviewBatchPageDTO unavailable() {
        return ChannelQualityReviewBatchPageDTO.builder()
                .available(false)
                .nextCursor(null)
                .items(List.of())
                .build();
    }

    public static ChannelQualityReviewBatchPageDTO available(
            Long nextCursor, List<ChannelQualityReviewBatchDTO> items) {
        return ChannelQualityReviewBatchPageDTO.builder()
                .available(true)
                .nextCursor(nextCursor)
                .items(items == null ? List.of() : List.copyOf(items))
                .build();
    }
}
