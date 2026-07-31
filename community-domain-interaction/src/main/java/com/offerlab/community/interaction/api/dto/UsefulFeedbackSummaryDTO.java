package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.UsefulFeedbackReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsefulFeedbackSummaryDTO {
    private Long total;
    private Map<UsefulFeedbackReason, Long> reasonCounts;
    private UsefulFeedbackReason myReason;
}
