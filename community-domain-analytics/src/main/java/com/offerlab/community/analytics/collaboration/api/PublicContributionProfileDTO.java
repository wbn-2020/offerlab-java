package com.offerlab.community.analytics.collaboration.api;

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
public class PublicContributionProfileDTO {

    private Long uid;
    private Integer factCount;
    private Boolean truncated;
    private LocalDateTime generatedAt;
    private List<PublicContributionFactDTO> facts;
}
