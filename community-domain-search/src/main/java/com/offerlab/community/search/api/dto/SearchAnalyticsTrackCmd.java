package com.offerlab.community.search.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchAnalyticsTrackCmd {
    @Size(max = 32)
    private String eventType;

    @Size(max = 100)
    private String keyword;

    @Size(max = 128)
    private String company;
}
