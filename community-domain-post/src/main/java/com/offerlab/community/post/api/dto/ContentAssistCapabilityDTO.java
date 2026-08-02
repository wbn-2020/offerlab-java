package com.offerlab.community.post.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistCapabilityDTO {
    private Boolean available;
    private Long remainingQuota;
    private String unavailableReason;
    private String benefitCode;
    private String consumerCode;
    private String targetPath;
}
