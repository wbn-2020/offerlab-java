package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistEnhancedStatusDTO {
    private String requestStatus;
    private String usageStatus;
    private Boolean quotaConsumed;
    private String requestFingerprint;
    private String errorCode;
    private ContentAssistEnhancedResultDTO result;
}
