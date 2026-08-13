package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChannelQualityGovernanceAnalyticsRebuildCmd {
    @NotBlank
    private String from;

    @NotBlank
    private String to;

    @Size(max = 5)
    private List<Integer> domains;

    @Size(max = 7)
    private List<String> metricFamilies;

    private Boolean dryRun;

    private Integer limit;

    @Size(max = 160)
    private String reason;

    @NotBlank
    @Size(max = 96)
    private String idempotencyKey;
}
