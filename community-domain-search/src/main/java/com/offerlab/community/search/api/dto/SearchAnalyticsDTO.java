package com.offerlab.community.search.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchAnalyticsDTO {
    private List<SearchAnalyticsItemDTO> hotKeywords;
    private List<SearchAnalyticsItemDTO> noResultKeywords;
    private List<SearchAnalyticsItemDTO> prepClicks;
}
