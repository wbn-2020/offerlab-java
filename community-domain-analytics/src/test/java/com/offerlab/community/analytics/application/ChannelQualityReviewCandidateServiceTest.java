package com.offerlab.community.analytics.application;

import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.ContentMaintenanceTaskReadFacade;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionSummary;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelQualityReviewCandidateServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final LocalDateTime WINDOW_START = LocalDateTime.ofInstant(
            NOW.minusSeconds(2L * 24L * 60L * 60L), ZoneOffset.UTC);

    @Mock
    private PostContentRevisionQueryFacade postContentRevisionQuery;
    @Mock
    private CreatorQualitySignalQueryFacade qualitySignalQuery;
    @Mock
    private PostFacade postFacade;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private MigrationCheckService migrationCheckService;
    @Mock
    private ContentMaintenanceTaskReadFacade maintenanceTaskReadFacade;
    @Mock
    private ChannelQualityReviewCandidateDispositionService candidateDispositionService;

    private ChannelQualityReviewCandidateService service;

    @BeforeEach
    void setUp() {
        RevisionAwareQualitySignalCoordinator coordinator = new RevisionAwareQualitySignalCoordinator(
                postContentRevisionQuery,
                qualitySignalQuery,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new ChannelQualityReviewCandidateService(
                coordinator,
                postContentRevisionQuery,
                qualitySignalQuery,
                postFacade,
                adminPermissionService,
                domainModeratorService,
                migrationCheckService,
                maintenanceTaskReadFacade,
                candidateDispositionService);
        when(adminPermissionService.isAdmin(9L)).thenReturn(true);
        when(migrationCheckService.trustedContentReady()).thenReturn(true);
        when(migrationCheckService.trustedDistributionReady()).thenReturn(true);
        when(migrationCheckService.stageTwoToFiveReady()).thenReturn(true);
        when(migrationCheckService.creatorQualityProjectionReady()).thenReturn(true);
        lenient().when(maintenanceTaskReadFacade.findTaskRevisionSummariesBySourceRevision(any(), any()))
                .thenReturn(Map.of());
        lenient().when(candidateDispositionService.findActiveBySourceRevision(any()))
                .thenReturn(Map.of());
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        new RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot(
                                1002L, "r-1002", 0L, 5L))));
    }

    private void stubCandidatePage() {
        when(postContentRevisionQuery.queryPublicRevisionBoundaryPage(any()))
                .thenReturn(PostPublicRevisionBoundaryPageResult.available(
                        List.of(projection(1002L, "r-1002")),
                        null,
                        1002L));
    }

    @Test
    void returnsOnlyMinimumPublicCandidateFieldsForCurrentQualifiedRevision() {
        stubCandidatePage();
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                snapshot(1002L, "r-1002", 7))));
        when(postFacade.batchGetPosts(List.of(1002L), null, false))
                .thenReturn(Map.of(1002L, publicPost(1002L)));

        var page = service.list(1, null, 10, 9L);

        assertTrue(page.getAvailable());
        assertEquals(null, page.getNextCursor());
        assertEquals(1, page.getItems().size());
        var candidate = page.getItems().get(0);
        assertEquals("CHANNEL_HEALTH", candidate.getSourceType());
        assertEquals(1002L, candidate.getSourcePostId());
        assertEquals(7L, candidate.getSourceRefId());
        assertEquals("/post/1002", candidate.getPostHref());
        assertEquals("REVISION_QUALITY_SIGNAL_READY", candidate.getReasonCode());
        assertEquals("MEDIUM", candidate.getPriority());
        assertTrue(candidate.getActionable());
    }

    @Test
    void revisionTokenDriftReturnsStaleCandidateWithoutRevisionReference() {
        stubCandidatePage();
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                snapshot(1002L, "newer-r-1002", 8))));
        when(postFacade.batchGetPosts(List.of(1002L), null, false))
                .thenReturn(Map.of(1002L, publicPost(1002L)));

        var page = service.list(1, null, 10, 9L);

        assertTrue(page.getAvailable());
        assertEquals(1, page.getItems().size());
        var candidate = page.getItems().get(0);
        assertEquals("REVISION_STALE", candidate.getLifecycleState());
        assertEquals(null, candidate.getSourceRefId());
        assertEquals(null, candidate.getMaintenanceStatus());
        assertFalse(candidate.getActionable());
        assertEquals(null, page.getNextCursor());
    }

    @Test
    void missingPublicMetadataIsSuppressedWithoutReturningDetails() {
        stubCandidatePage();
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                snapshot(1002L, "r-1002", 7))));
        when(postFacade.batchGetPosts(List.of(1002L), null, false)).thenReturn(Map.of());

        var page = service.list(1, null, 10, 9L);

        assertTrue(page.getAvailable());
        assertTrue(page.getItems().isEmpty());
        assertEquals(1, page.getSuppressedCount());
        assertEquals(null, page.getNextCursor());
    }

    @Test
    void existingTaskForCurrentRevisionIsNotActionable() {
        stubCandidatePage();
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                snapshot(1002L, "r-1002", 7))));
        when(postFacade.batchGetPosts(List.of(1002L), null, false))
                .thenReturn(Map.of(1002L, publicPost(1002L)));
        when(maintenanceTaskReadFacade.findTaskRevisionSummariesBySourceRevision(any(), any()))
                .thenReturn(Map.of(
                        new ContentMaintenanceTaskRevisionKey(1002L, 7L),
                        new ContentMaintenanceTaskRevisionSummary(
                                "COMPLETED", "VERIFIED_DELIVERY", "VERIFIED_DELIVERY")));

        var page = service.list(1, null, 10, 9L);

        assertTrue(page.getAvailable());
        assertEquals(1, page.getItems().size());
        var candidate = page.getItems().get(0);
        assertEquals("TASK_EXISTS", candidate.getLifecycleState());
        assertEquals(7L, candidate.getSourceRefId());
        assertEquals("COMPLETED", candidate.getMaintenanceStatus());
        assertEquals("VERIFIED_DELIVERY", candidate.getMaintenancePhase());
        assertEquals("VERIFIED_DELIVERY", candidate.getTerminalOutcome());
        assertFalse(candidate.getActionable());
    }

    @Test
    void dispatchResolutionRevalidatesTheCurrentReadyCandidate() {
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                snapshot(1002L, "r-1002", 7))));
        when(postFacade.batchGetPosts(List.of(1002L), null, false))
                .thenReturn(Map.of(1002L, publicPost(1002L)));

        var candidates = service.resolveReadyForDispatch(
                1, List.of(new ContentMaintenanceTaskRevisionKey(1002L, 7L)), 9L);

        assertEquals(1, candidates.size());
        assertEquals(1002L, candidates.get(0).sourcePostId());
        assertEquals(7L, candidates.get(0).sourceRefId());
        assertEquals("公开帖子", candidates.get(0).title());
    }

    private static PostPublicRevisionBoundaryProjection projection(Long postId, String token) {
        return new PostPublicRevisionBoundaryProjection(
                postId,
                1,
                8L,
                true,
                WINDOW_START,
                token,
                PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION);
    }

    private static PostContentRevisionSnapshot snapshot(Long postId, String token, int version) {
        return new PostContentRevisionSnapshot(
                postId,
                PostContentRevisionSnapshot.Status.FOUND,
                WINDOW_START,
                true,
                token,
                version,
                WINDOW_START);
    }

    private static PostBriefDTO publicPost(Long postId) {
        return PostBriefDTO.builder()
                .id(postId)
                .authorId(8L)
                .domain(1)
                .postType(Post.TYPE_COMMUNITY_QUESTION)
                .title("公开帖子")
                .anonymous(false)
                .build();
    }
}
