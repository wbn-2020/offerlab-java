package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistEnhancedResultDTO {
    private String requestStatus;
    private String usageStatus;
    private Boolean quotaConsumed;
    private Boolean replayed;
    private String requestFingerprint;
    private String provider;
    private String fallbackReason;
    private ContentAssistWritingDTO writing;
    private ContentAssistQualityScoreDTO quality;
    private ContentAssistTagTopicSuggestionsDTO tagTopic;
}
