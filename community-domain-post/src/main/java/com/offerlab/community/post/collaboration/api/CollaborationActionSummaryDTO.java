package com.offerlab.community.post.collaboration.api;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class CollaborationActionSummaryDTO {
    private Integer total;
    private Map<String, Integer> counts;
    private LocalDateTime generatedAt;
    private Boolean degraded;
    private Map<String, String> sourceErrors;
}
