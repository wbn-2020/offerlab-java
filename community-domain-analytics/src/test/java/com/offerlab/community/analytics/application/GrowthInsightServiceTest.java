package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.GrowthProfileDTO;
import com.offerlab.community.analytics.api.dto.GrowthReportDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthEventMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesPostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthInsightServiceTest {

    @Mock
    private GrowthInsightMapper growthInsightMapper;
    @Mock
    private GrowthEventMapper growthEventMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private ContentSeriesMapper contentSeriesMapper;
    @Mock
    private ContentSeriesPostMapper contentSeriesPostMapper;

    private GrowthInsightService growthInsightService;

    @BeforeEach
    void setUp() {
        growthInsightService = new GrowthInsightService(
                growthInsightMapper,
                growthEventMapper,
                tagMapper,
                contentSeriesMapper,
                contentSeriesPostMapper);
    }

    @Test
    void profileMarksDegradedWhenGrowthEventsAndSeriesSchemasAreUnavailable() {
        when(growthInsightMapper.selectAuthorDomainStats(eq(8L), any(LocalDateTime.class))).thenReturn(List.of(
                Map.of(
                        "domain", 1,
                        "postCount", 3L,
                        "featuredCount", 1L,
                        "likeCount", 6L,
                        "favoriteCount", 2L,
                        "commentCount", 4L,
                        "viewCount", 80L,
                        "activeDays", 2L,
                        "avgContentLength", 950L)));
        when(growthInsightMapper.selectRepresentativePosts(eq(8L), any(LocalDateTime.class), eq(4))).thenReturn(List.of(
                Map.of(
                        "postId", 100L,
                        "title", "JVM GC tuning review",
                        "domain", 1,
                        "featured", 1,
                        "interactionCount", 9L)));
        when(growthEventMapper.tableExists()).thenReturn(0);
        when(contentSeriesMapper.tableExists()).thenReturn(0);

        GrowthProfileDTO profile = growthInsightService.profile(8L, 30);

        assertTrue(profile.isDegraded());
        assertTrue(profile.getDegradationReasons().stream().anyMatch(item -> item.contains("growth event")));
        assertTrue(profile.getDegradationReasons().stream().anyMatch(item -> item.contains("content series")));
        assertTrue(profile.getDegradationReasons().stream().anyMatch(item -> item.contains("cross-domain consume")));
        assertEquals(1, profile.getDomains().size());
        assertEquals(PostDomain.fromCode(1).getDisplayName(), profile.getDomains().get(0).getDomainName());
        assertFalse(profile.getDomains().get(0).getDimensions().isEmpty());
    }

    @Test
    void profileStillMarksCrossDomainFallbackWhenSchemasAreReady() {
        when(growthInsightMapper.selectAuthorDomainStats(eq(9L), any(LocalDateTime.class))).thenReturn(List.of(
                Map.of(
                        "domain", 2,
                        "postCount", 2L,
                        "featuredCount", 0L,
                        "likeCount", 4L,
                        "favoriteCount", 1L,
                        "commentCount", 2L,
                        "viewCount", 36L,
                        "activeDays", 2L,
                        "avgContentLength", 840L)));
        when(growthInsightMapper.selectRepresentativePosts(eq(9L), any(LocalDateTime.class), eq(4))).thenReturn(List.of(
                Map.of(
                        "postId", 220L,
                        "title", "Career transition notes",
                        "domain", 2,
                        "featured", 0,
                        "interactionCount", 6L)));
        when(growthEventMapper.tableExists()).thenReturn(1);
        when(contentSeriesMapper.tableExists()).thenReturn(1);
        when(contentSeriesPostMapper.tableExists()).thenReturn(1);
        when(contentSeriesMapper.selectMine(9L)).thenReturn(List.of());

        GrowthProfileDTO profile = growthInsightService.profile(9L, 14);

        assertTrue(profile.isDegraded());
        assertTrue(profile.getDegradationReasons().stream().anyMatch(item -> item.contains("cross-domain consume")));
        assertFalse(profile.getDegradationReasons().stream().anyMatch(item -> item.contains("content series")));
        assertEquals(1, profile.getDomains().size());
    }

    @Test
    void reportBuildsDomainTrendAndNextActionsFromCurrentAndPreviousWindows() {
        when(growthInsightMapper.selectAuthorDomainStats(eq(12L), any(LocalDateTime.class))).thenReturn(List.of(
                Map.of(
                        "domain", 1,
                        "postCount", 2L,
                        "featuredCount", 1L,
                        "likeCount", 10L,
                        "favoriteCount", 3L,
                        "commentCount", 5L,
                        "viewCount", 120L,
                        "activeDays", 3L,
                        "avgContentLength", 1200L),
                Map.of(
                        "domain", 2,
                        "postCount", 1L,
                        "featuredCount", 0L,
                        "likeCount", 2L,
                        "favoriteCount", 1L,
                        "commentCount", 1L,
                        "viewCount", 30L,
                        "activeDays", 1L,
                        "avgContentLength", 600L)));
        when(growthInsightMapper.selectPreviousAuthorDomainStats(eq(12L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
                Map.of("domain", 1, "postCount", 1L),
                Map.of("domain", 2, "postCount", 0L)));
        when(growthInsightMapper.selectRepresentativePosts(eq(12L), any(LocalDateTime.class), eq(3))).thenReturn(List.of(
                Map.of(
                        "postId", 300L,
                        "title", "Spring Batch troubleshooting",
                        "domain", 1,
                        "featured", 1,
                        "interactionCount", 15L)));
        when(growthEventMapper.tableExists()).thenReturn(1);
        when(contentSeriesMapper.tableExists()).thenReturn(1);
        when(contentSeriesPostMapper.tableExists()).thenReturn(1);
        when(contentSeriesMapper.selectMine(12L)).thenReturn(List.of());

        GrowthReportDTO report = growthInsightService.report(12L, "weekly");

        assertEquals("weekly", report.getPeriod());
        assertEquals(7, report.getDays());
        assertEquals(2, report.getDomainChanges().size());
        assertEquals("up", report.getDomainChanges().get(0).getTrend());
        assertFalse(report.getNextActions().isEmpty());
        assertEquals(1, report.getHighlightPosts().size());
    }
}
