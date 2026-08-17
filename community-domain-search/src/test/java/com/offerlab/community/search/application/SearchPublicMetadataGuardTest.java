package com.offerlab.community.search.application;

import com.offerlab.community.common.result.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

class SearchPublicMetadataGuardTest {

    @Test
    void publicViewDropsAllInfrastructureMetadata() {
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

        assertNull(publicView.getSource());
        assertNull(publicView.getDegraded());
        assertNull(publicView.getFallbackReason());
        assertNull(publicView.getScanLimit());
        assertNull(publicView.getDiagnostics());
    }
}
