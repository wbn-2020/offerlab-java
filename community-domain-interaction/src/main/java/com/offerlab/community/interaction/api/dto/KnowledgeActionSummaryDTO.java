package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeActionSummaryDTO {
    private Long total;
    private Map<String, Long> counts;
    private Boolean degraded;
    private Map<String, String> sourceErrors;
    private LocalDateTime generatedAt;
}
