package com.offerlab.community.search.application;

import com.offerlab.community.common.result.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPublicMetadataGuardTest {

    @Test
    void publicViewKeepsUserFacingFallbackMetadataButDropsInternalDiagnostics() {
        PageResult<String> internal = PageResult.<String>builder()
                .items(List.of("post"))
                .nextCursor("12")
                .hasMore(true)
                .total(1L)
                .source("mysql")
                .degraded(true)
                .fallbackReason("elasticsearch_unavailable")
                .scanLimit(200)
                .diagnostics(Map.of("internalQueryPlan", "private"))
                .build();

        PageResult<String> publicView = internal.publicView();

        assertEquals("mysql", publicView.getSource());
        assertTrue(publicView.getDegraded());
        assertEquals("elasticsearch_unavailable", publicView.getFallbackReason());
        assertEquals(200, publicView.getScanLimit());
        assertNull(publicView.getDiagnostics());
    }
}
