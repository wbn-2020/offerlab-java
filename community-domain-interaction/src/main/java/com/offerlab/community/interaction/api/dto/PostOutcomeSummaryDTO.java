package com.offerlab.community.interaction.api.dto;

import com.offerlab.community.interaction.api.enums.PostOutcomeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostOutcomeSummaryDTO {
    private Long postId;
    private Integer minimumSampleSize;
    private Boolean minimumSampleMet;
    private Long publicSampleCount;
    private Map<PostOutcomeType, Long> outcomeCounts;
    private List<PostOutcomeSampleDTO> samples;
}
