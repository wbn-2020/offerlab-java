package com.offerlab.community.search.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchAnalyticsItemDTO {
    private String keyword;
    private String company;
    private Long count;
    private Long noResultCount;
    private Long lastResultCount;
    private String lastSearchedAt;
}
