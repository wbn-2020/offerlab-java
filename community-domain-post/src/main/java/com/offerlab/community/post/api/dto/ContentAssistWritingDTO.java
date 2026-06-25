package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistWritingDTO {
    private String provider;
    private Boolean fallbackUsed;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long estimatedCostMicros;
    private String errorCode;
    private String suggestedTitle;
    private String summary;
    private List<String> outline;
    private List<String> suggestions;
    private List<String> riskHints;
}
