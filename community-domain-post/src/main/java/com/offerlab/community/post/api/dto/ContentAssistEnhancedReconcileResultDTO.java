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
public class ContentAssistEnhancedReconcileResultDTO {
    private Boolean dryRun;
    private Integer scanned;
    private Integer eligible;
    private Integer recovered;
    private Integer skipped;
    private Boolean replayed;
    private List<ContentAssistEnhancedExceptionDTO> issues;
}
