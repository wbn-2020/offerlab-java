package com.offerlab.community.search.application;

import com.offerlab.community.search.api.dto.SearchContentGapDTO;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchAnalyticsMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchContentGapServiceTest {

    @Test
    void noResultSignalsMustNotBeDoubleCountedForPrivacyThreshold() {
        SearchContentGapDTO belowThreshold = candidate(3);
        SearchContentGapDTO atThreshold = candidate(5);

        assertFalse(belowThreshold.getMinSampleMet(),
                "three no-result searches are still three samples, not six signals");
        assertTrue(atThreshold.getMinSampleMet(),
                "five searches should satisfy the aggregate privacy threshold");
    }

    private SearchContentGapDTO candidate(long searchCount) {
        SearchAnalyticsMapper mapper = mock(SearchAnalyticsMapper.class);
        when(mapper.tableExists()).thenReturn(1);
        when(mapper.topSearchKeywords(anyInt(), anyInt(), anyBoolean())).thenReturn(List.of(
                Map.of(
                        "keyword", "bounded queue",
                        "count", searchCount,
                        "noResultCount", searchCount,
                        "lastResultCount", 0,
                        "lastSearchedAt", "2026-07-11T10:00:00"
                )
        ));
        when(mapper.topNoResultKeywords(anyInt(), anyInt(), anyBoolean())).thenReturn(List.of());

        return new SearchContentGapService(mapper).candidates(30, 10).get(0);
    }
}
