package com.offerlab.community.analytics.application;

import com.offerlab.community.feed.api.quality.CreatorQualitySignalQueryFacade;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.post.api.quality.PostContentRevisionQueryFacade;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevisionAwareQualitySignalCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final LocalDateTime REVISION_WINDOW_START = LocalDateTime.ofInstant(
            NOW.minusSeconds(2L * 24L * 60L * 60L), ZoneOffset.UTC);

    @Mock
    private PostContentRevisionQueryFacade postContentRevisionQuery;
    @Mock
    private CreatorQualitySignalQueryFacade qualitySignalQuery;

    private RevisionAwareQualitySignalCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new RevisionAwareQualitySignalCoordinator(
                postContentRevisionQuery,
                qualitySignalQuery,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void concurrentTokenChangeSkipsOnlyThatPostWithoutDowngradingTheWholeBlock() {
        when(postContentRevisionQuery.query(any()))
                .thenReturn(PostContentRevisionQueryResult.available(List.of(revision(1001L, "r-one"))))
                .thenReturn(PostContentRevisionQueryResult.available(List.of(revision(1001L, "r-two"))));
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1001L, "r-one", 0L, 5L))));

        RevisionAwareQualitySignalCoordinator.Resolution result = coordinator.resolveAuthorOwned(
                coordinator.captureWindow(), 8L, List.of(1001L));

        assertTrue(result.available());
        assertTrue(result.assessments().isEmpty());
    }

    @Test
    void mismatchedFeedTokenMakesTheQualityBlockUnavailable() {
        when(postContentRevisionQuery.query(any())).thenReturn(PostContentRevisionQueryResult.available(List.of(
                revision(1001L, "r-one"))));
        when(qualitySignalQuery.findRevisionAwareActiveQualitySignals(any(), any(), any()))
                .thenReturn(RevisionAwareQualitySignalQueryResult.available(List.of(
                        signal(1001L, "r-other", 0L, 5L))));

        RevisionAwareQualitySignalCoordinator.Resolution result = coordinator.resolveAuthorOwned(
                coordinator.captureWindow(), 8L, List.of(1001L));

        assertFalse(result.available());
    }

    private static PostContentRevisionSnapshot revision(Long postId, String token) {
        return new PostContentRevisionSnapshot(
                postId,
                PostContentRevisionSnapshot.Status.FOUND,
                REVISION_WINDOW_START,
                true,
                token,
                2,
                REVISION_WINDOW_START);
    }

    private static RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot signal(
            Long postId, String token, long prior, long current) {
        return new RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot(postId, token, prior, current);
    }
}
