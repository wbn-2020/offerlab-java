package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.QualitySignalWindow;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageResult;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryProjection;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelHealthQualitySignalTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final LocalDateTime REVISION_WINDOW_START = LocalDateTime.ofInstant(
            NOW.minusSeconds(2L * 24L * 60L * 60L), ZoneOffset.UTC);

    @Mock
    private GrowthInsightMapper growthInsightMapper;
    @Mock
    private PostContentRevisionQueryFacade postContentRevisionQuery;
    @Mock
    private CreatorQualitySignalQueryFacade qualitySignalQuery;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private MigrationCheckService migrationCheckService;

    private ChannelHealthService service;

    @BeforeEach
    void setUp() {
        RevisionAwareQualitySignalCoordinator coordinator = new RevisionAwareQualitySignalCoordinator(
                postContentRevisionQuery,
                qualitySignalQuery,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new ChannelHealthService(
                growthInsightMapper,
                coordinator,
                postContentRevisionQuery,
                qualitySignalQuery,
                adminPermissionService,
                domainModeratorService,
                migrationCheckService);
        when(migrationCheckService.trustedContentReady()).thenReturn(true);
        when(migrationCheckService.trustedDistributionReady()).thenReturn(true);
        when(migrationCheckService.stageTwoToFiveReady()).thenReturn(true);
        when(migrationCheckService.creatorQualityProjectionReady()).thenReturn(true);
        when(adminPermissionService.isAdmin(9L)).thenReturn(true);
        when(growthInsightMapper.selectChannelHealth(null, Post.TYPE_COMMUNITY_QUESTION)).thenReturn(List.of(
                Map.of(
                        "domain", 1,
                        "publicPostCount", 12L,
                        "trustProfileCount", 8L,
                        "freshnessAwaitingConfirmation", 0L,
                        "pendingSuggestions", 0L,
                        "unresolvedQuestions", 0L,
                        "openContentNeeds", 0L)));
    }

    @Test
    void channelCountsOnlyCurrentRevisionThresholdQualifiedPosts() {
        stubCurrentCandidate();
        stubCurrentRevisionToken();
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1002L, "r-1002", 5L, 5L))));

        var result = service.list(null, 9L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getQualityReviewPostCount());
        assertTrue(result.get(0).getQualitySignalAvailable());
        assertTrue(result.get(0).getAttentionReasons().contains("存在需要作者复核的匿名质量信号"));
    }

    @Test
    void priorRevisionOnlySignalDoesNotIncreaseChannelCount() {
        stubCurrentCandidate();
        stubCurrentRevisionToken();
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1002L, "r-1002", 5L, 4L))));

        var result = service.list(null, 9L);

        assertTrue(result.get(0).getQualitySignalAvailable());
        assertEquals(0L, result.get(0).getQualityReviewPostCount());
        assertFalse(result.get(0).getAttentionReasons().contains("存在需要作者复核的匿名质量信号"));
    }

    @Test
    void unavailableQualitySourceIsNotReportedAsZeroOrStable() {
        stubCurrentCandidate();
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.unavailable());

        var result = service.list(null, 9L);

        assertFalse(result.get(0).getQualitySignalAvailable());
        assertEquals(null, result.get(0).getQualityReviewPostCount());
        assertEquals("DEGRADED", result.get(0).getHealthStatus());
        assertTrue(result.get(0).getAttentionReasons().contains("匿名质量信号暂不可用"));
    }

    @Test
    void unavailableRevisionBoundarySchemaDegradesChannelQualityWithoutV31Fallback() {
        when(migrationCheckService.creatorQualityProjectionReady()).thenReturn(false);

        var result = service.list(null, 9L);

        assertFalse(result.get(0).getQualitySignalAvailable());
        assertEquals(null, result.get(0).getQualityReviewPostCount());
        assertEquals("DEGRADED", result.get(0).getHealthStatus());
        verifyNoInteractions(postContentRevisionQuery, qualitySignalQuery);
    }

    @Test
    void mismatchedFeedRevisionTokenDegradesInsteadOfCountingProjectionSignals() {
        stubCurrentCandidate();
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1002L, "changed-token", 0L, 5L))));

        var result = service.list(null, 9L);

        assertFalse(result.get(0).getQualitySignalAvailable());
        assertEquals(null, result.get(0).getQualityReviewPostCount());
        assertEquals("DEGRADED", result.get(0).getHealthStatus());
    }

    @Test
    void changedPostRevisionAfterFeedAggregationDegradesInsteadOfCountingStaleSignals() {
        stubCurrentCandidate();
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1002L, "r-1002", 0L, 5L))));
        when(postContentRevisionQuery.query(any()))
                .thenReturn(PostContentRevisionQueryResult.available(List.of(
                        snapshot(1002L, "newer-r-1002"))));

        var result = service.list(null, 9L);

        assertFalse(result.get(0).getQualitySignalAvailable());
        assertEquals(null, result.get(0).getQualityReviewPostCount());
        assertEquals("DEGRADED", result.get(0).getHealthStatus());
    }

    @Test
    void candidateBudgetIsSharedAcrossAllDomainsInOneRequest() {
        when(growthInsightMapper.selectChannelHealth(null, Post.TYPE_COMMUNITY_QUESTION)).thenReturn(List.of(
                channelRow(1),
                channelRow(2)));
        AtomicInteger secondDomainProjectionCalls = new AtomicInteger();
        when(postContentRevisionQuery.queryPublicRevisionBoundaryPage(any()))
                .thenAnswer(invocation -> {
                    var query = invocation.getArgument(0,
                            com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageQuery.class);
                    if (query.domain() == 2) {
                        secondDomainProjectionCalls.incrementAndGet();
                        return PostPublicRevisionBoundaryPageResult.available(
                                List.of(projection(251L, 2)), null, 251L);
                    }
                    long start = query.afterPostId() + 1;
                    long end = Math.min(start + 99, 250);
                    List<PostPublicRevisionBoundaryProjection> items = LongStream.rangeClosed(start, end)
                            .mapToObj(postId -> projection(postId, 1))
                            .toList();
                    return PostPublicRevisionBoundaryPageResult.available(
                            items,
                            end < 250 ? end : null,
                            250L);
                });
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Collection<QualitySignalWindow> windows = invocation.getArgument(0);
                    return RevisionAwareQualitySignalQueryResult.available(windows.stream()
                            .map(window -> signal(window.postId(), window.revisionToken(), 0L, 0L))
                            .toList());
                });
        when(postContentRevisionQuery.query(any())).thenAnswer(invocation -> {
            PostContentRevisionQuery query = invocation.getArgument(0);
            return PostContentRevisionQueryResult.available(query.postIds().stream()
                    .map(postId -> snapshot(postId, "r-" + postId))
                    .toList());
        });

        var result = service.list(null, 9L);

        assertEquals(0, secondDomainProjectionCalls.get());
        assertTrue(result.stream().noneMatch(item -> item.getQualitySignalAvailable()));
        assertTrue(result.stream().allMatch(item -> "DEGRADED".equals(item.getHealthStatus())));
    }

    private static RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal(
            Long postId, String token, long prior, long current) {
        return new RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot(postId, token, prior, current);
    }

    private void stubCurrentCandidate() {
        when(postContentRevisionQuery.queryPublicRevisionBoundaryPage(any()))
                .thenReturn(PostPublicRevisionBoundaryPageResult.available(
                        List.of(new PostPublicRevisionBoundaryProjection(
                                1002L,
                                1,
                                8L,
                                true,
                                REVISION_WINDOW_START,
                                "r-1002",
                                PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION)),
                        null,
                        1002L));
    }

    private void stubCurrentRevisionToken() {
        when(postContentRevisionQuery.query(any()))
                .thenReturn(PostContentRevisionQueryResult.available(List.of(
                        snapshot(1002L, "r-1002"))));
    }

    private static PostContentRevisionSnapshot snapshot(Long postId, String token) {
        return new PostContentRevisionSnapshot(
                postId,
                PostContentRevisionSnapshot.Status.FOUND,
                REVISION_WINDOW_START,
                true,
                token,
                1,
                REVISION_WINDOW_START);
    }

    private static PostPublicRevisionBoundaryProjection projection(Long postId, int domain) {
        return new PostPublicRevisionBoundaryProjection(
                postId,
                domain,
                8L,
                true,
                REVISION_WINDOW_START,
                "r-" + postId,
                PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION);
    }

    private static Map<String, Object> channelRow(int domain) {
        return Map.of(
                "domain", domain,
                "publicPostCount", 12L,
                "trustProfileCount", 8L,
                "freshnessAwaitingConfirmation", 0L,
                "pendingSuggestions", 0L,
                "unresolvedQuestions", 0L,
                "openContentNeeds", 0L);
    }
}
