package com.offerlab.community.post.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAssistEnhancedReconcileCmd {
    private boolean dryRun;
    @Min(1)
    @Max(100)
    private Integer limit;
    @NotBlank
    @Size(min = 16, max = 96)
    private String idempotencyKey;
    @NotBlank
    @Size(max = 500)
    private String reason;
}
