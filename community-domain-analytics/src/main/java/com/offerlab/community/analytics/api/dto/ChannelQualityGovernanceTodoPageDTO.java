package com.offerlab.community.analytics.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ChannelQualityGovernanceTodoPageDTO {
    private String dependencyStatus;
    private Instant evaluationTime;
    private Instant freshThrough;
    private String nextCursor;
    private List<ChannelQualityGovernanceTodoItemDTO> items;
}
