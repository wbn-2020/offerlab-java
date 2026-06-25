package com.offerlab.community.analytics.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GrowthEventTrackCmd {
    @Size(max = 32)
    private String eventType;
    private Integer domain;
    private Long contentId;
    @Size(max = 32)
    private String targetType;
    @Size(max = 128)
    private String targetValue;
    @Size(max = 128)
    private String sourcePage;
    @Size(max = 2000)
    private String extJson;
}
