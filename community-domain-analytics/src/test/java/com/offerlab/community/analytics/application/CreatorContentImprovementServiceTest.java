package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorContentImprovementSignalsDTO;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.post.api.ContentMaintenanceTaskReadFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorContentImprovementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final LocalDateTime BASE_WINDOW_START = LocalDateTime.ofInstant(
            NOW.minusSeconds(30L * 24L * 60L * 60L), ZoneOffset.UTC);
    private static final LocalDateTime REVISION_WINDOW_START = LocalDateTime.ofInstant(
            NOW.minusSeconds(2L * 24L * 60L * 60L), ZoneOffset.UTC);

    @Mock
    private PostFacade postFacade;
    @Mock
    private PostContentRevisionQueryFacade postContentRevisionQuery;
    @Mock
    private CreatorQualitySignalQueryFacade qualitySignalQuery;
    @Mock
    private ContentMaintenanceTaskReadFacade maintenanceTaskReadFacade;
    @Mock
    private MigrationCheckService migrationCheckService;

    private CreatorContentImprovementService service;

    @BeforeEach
    void setUp() {
        RevisionAwareQualitySignalCoordinator coordinator = new RevisionAwareQualitySignalCoordinator(
                postContentRevisionQuery,
                qualitySignalQuery,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service = new CreatorContentImprovementService(
                postFacade, coordinator, maintenanceTaskReadFacade, migrationCheckService);
        when(migrationCheckService.creatorContentRevisionBoundaryReady()).thenReturn(true);
    }

    @Test
    void onlyCurrentRevisionThresholdQualifiedPublicOwnPostsAreReturned() {
        PostBriefDTO belowThreshold = post(1001L, 8L, false, "Below threshold");
        PostBriefDTO qualified = post(1002L, 8L, false, "Qualified post");
        PostBriefDTO anonymous = post(1003L, 8L, true, "Anonymous post");
        PostBriefDTO foreign = post(1004L, 9L, false, "Foreign post");
        when(postFacade.getPostsByAuthor(8L, 0L, 5)).thenReturn(PageResult.of(
                List.of(belowThreshold, qualified, anonymous, foreign), null, false));
        List<PostContentRevisionSnapshot> snapshots = List.of(
                revision(1001L, true, "r-1001"),
                revision(1002L, true, "r-1002"),
                notEligible(1003L),
                notEligible(1004L));
        when(postContentRevisionQuery.query(any())).thenAnswer(invocation -> {
            PostContentRevisionQuery query = invocation.getArgument(0);
            return PostContentRevisionQueryResult.available(snapshots.stream()
                    .filter(snapshot -> query.postIds().contains(snapshot.postId()))
                    .toList());
        });
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1001L, "r-1001", 0L, 4L),
                        signal(1002L, "r-1002", 0L, 5L))));

        CreatorContentImprovementSignalsDTO result = service.list(8L, null, 5);

        assertFalse(result.isDegraded());
        assertEquals(30, result.getPeriodDays());
        assertEquals(1, result.getItems().size());
        assertEquals(1002L, result.getItems().get(0).getPostId());
        assertEquals("REVIEW_RECOMMENDED", result.getItems().get(0).getState());
        assertEquals("/editor/1002?source=creator_workbench", result.getItems().get(0).getEditHref());
    }

    @Test
    void currentRevisionQualifiedPostWithActiveMaintenanceKeepsMaintenanceAsHighestPriority() {
        PostBriefDTO qualified = post(1002L, 8L, false, "Qualified post");
        when(postFacade.getPostsByAuthor(8L, 0L, 5)).thenReturn(PageResult.of(
                List.of(qualified), null, false));
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                revision(1002L, true, "r-1002"))));
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1002L, "r-1002", 0L, 5L))));
        when(maintenanceTaskReadFacade.findActivePublicSourcePostIds(8L, List.of(1002L)))
                .thenReturn(Set.of(1002L));

        CreatorContentImprovementSignalsDTO result = service.list(8L, null, 5);

        assertEquals(1, result.getItems().size());
        CreatorContentImprovementSignalsDTO.Item item = result.getItems().get(0);
        assertEquals("MAINTENANCE_EXISTS", item.getState());
        assertEquals("/me/maintenance", item.getWorkspaceHref());
        assertEquals("/post/1002", item.getPostHref());
        assertEquals("/editor/1002?source=creator_workbench", item.getEditHref());
        verify(maintenanceTaskReadFacade).findActivePublicSourcePostIds(8L, List.of(1002L));
    }

    @Test
    void priorRevisionThresholdAfterAnEffectiveRevisionBecomesNeutralAwaitingFeedbackState() {
        PostBriefDTO updated = post(1002L, 8L, false, "Updated post");
        when(postFacade.getPostsByAuthor(8L, 0L, 5)).thenReturn(PageResult.of(
                List.of(updated), null, false));
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                revision(1002L, true, "r-1002"))));
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1002L, "r-1002", 5L, 4L))));

        CreatorContentImprovementSignalsDTO result = service.list(8L, null, 5);

        assertEquals(1, result.getItems().size());
        CreatorContentImprovementSignalsDTO.Item item = result.getItems().get(0);
        assertEquals("UPDATED_AWAITING_ANONYMOUS_FEEDBACK", item.getState());
        assertEquals("内容已更新，等待新的匿名反馈", item.getHeadline());
        assertEquals("/editor/1002?source=creator_workbench", item.getEditHref());
    }

    @Test
    void unavailableRevisionOrQualitySourceStaysDegradedInsteadOfClaimingNoIssues() {
        when(postFacade.getPostsByAuthor(8L, 0L, 5)).thenReturn(PageResult.of(
                List.of(post(1001L, 8L, false, "Candidate")), null, false));
        when(postContentRevisionQuery.query(any()))
                .thenReturn(PostContentRevisionQueryResult.unavailable(List.of()));

        CreatorContentImprovementSignalsDTO result = service.list(8L, null, 5);

        assertTrue(result.isDegraded());
        assertEquals("QUALITY_SIGNAL_SOURCE_UNAVAILABLE", result.getFallbackReason());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void unavailableRevisionBoundarySchemaDegradesBeforeAnyV32CrossDomainRead() {
        when(migrationCheckService.creatorContentRevisionBoundaryReady()).thenReturn(false);

        CreatorContentImprovementSignalsDTO result = service.list(8L, null, 5);

        assertTrue(result.isDegraded());
        assertEquals("QUALITY_SIGNAL_SCHEMA_UNAVAILABLE", result.getFallbackReason());
        verifyNoInteractions(postFacade, postContentRevisionQuery, qualitySignalQuery, maintenanceTaskReadFacade);
    }

    private static PostContentRevisionSnapshot revision(Long postId, boolean hasEffectiveRevision, String token) {
        LocalDateTime windowStart = hasEffectiveRevision ? REVISION_WINDOW_START : BASE_WINDOW_START;
        return new PostContentRevisionSnapshot(
                postId,
                hasEffectiveRevision
                        ? PostContentRevisionSnapshot.Status.FOUND
                        : PostContentRevisionSnapshot.Status.NO_EFFECTIVE_REVISION,
                windowStart,
                hasEffectiveRevision,
                token,
                2,
                windowStart);
    }

    private static PostContentRevisionSnapshot notEligible(Long postId) {
        return new PostContentRevisionSnapshot(
                postId,
                PostContentRevisionSnapshot.Status.NOT_ELIGIBLE,
                BASE_WINDOW_START,
                false,
                null,
                null,
                null);
    }

    private static RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal(
            Long postId, String token, long prior, long current) {
        return new RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot(postId, token, prior, current);
    }

    private static PostBriefDTO post(Long id, Long authorId, boolean anonymous, String title) {
        return PostBriefDTO.builder()
                .id(id)
                .authorId(authorId)
                .title(title)
                .summary("A public review")
                .domain(1)
                .anonymous(anonymous)
                .build();
    }
}
