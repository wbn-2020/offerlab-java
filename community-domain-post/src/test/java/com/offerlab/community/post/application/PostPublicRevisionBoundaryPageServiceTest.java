package com.offerlab.community.post.application;

import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryPageQuery;
import com.offerlab.community.post.api.quality.PostPublicRevisionBoundaryProjection;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostVersionHistoryMapper;
import com.offerlab.community.post.infrastructure.persistence.projection.PostContentRevisionQueryRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostPublicRevisionBoundaryPageServiceTest {

    @Mock
    private PostVersionHistoryMapper versionMapper;

    @Test
    void firstPagePinsUpperBoundAndNeverReturnsMoreThanOneHundredRows() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        when(versionMapper.selectMaxPublicContentRevisionBoundaryPostId(3)).thenReturn(250L);
        List<PostContentRevisionQueryRow> rows = LongStream.rangeClosed(1, 100)
                .mapToObj(postId -> row(postId, 7L, 3, "revision-" + postId,
                        windowStart.plusMinutes(postId)))
                .toList();
        when(versionMapper.selectPublicContentRevisionBoundaryRows(3, 0L, 250L, 100))
                .thenReturn(rows);
        when(versionMapper.existsPublicContentRevisionBoundaryAfter(3, 100L, 250L)).thenReturn(true);

        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);
        var result = service.queryPublicRevisionBoundaryPage(query(
                windowStart, 0L, null, PostPublicRevisionBoundaryPageQuery.MAX_PAGE_SIZE));

        assertTrue(result.available());
        assertEquals(100, result.items().size());
        assertEquals(100L, result.nextCursor());
        assertEquals(250L, result.snapshotUpperBoundPostId());
        assertTrue(result.items().stream().allMatch(item ->
                item.publicEligible()
                        && item.domain().equals(3)
                        && item.authorId().equals(7L)
                        && item.status() == PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION));
        assertEquals(windowStart.plusMinutes(1), result.items().get(0).windowStart());
        assertEquals(windowStart.plusMinutes(100), result.items().get(99).windowStart());
        verify(versionMapper).selectPublicContentRevisionBoundaryRows(3, 0L, 250L, 100);
        verify(versionMapper).existsPublicContentRevisionBoundaryAfter(3, 100L, 250L);
    }

    @Test
    void continuationRetainsWatermarkAndSeparatesNoRevisionFromUnavailable() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        when(versionMapper.selectPublicContentRevisionBoundaryRows(3, 100L, 250L, 2))
                .thenReturn(List.of(
                        row(101L, 9L, 3, "revision-before-window", windowStart.minusMinutes(1)),
                        row(102L, 9L, 3, "revision-102", windowStart.plusMinutes(1))));
        when(versionMapper.existsPublicContentRevisionBoundaryAfter(3, 102L, 250L)).thenReturn(false);
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.queryPublicRevisionBoundaryPage(query(windowStart, 100L, 250L, 2));

        assertTrue(result.available());
        assertNull(result.nextCursor());
        assertEquals(250L, result.snapshotUpperBoundPostId());
        assertEquals(PostPublicRevisionBoundaryProjection.Status.NO_EFFECTIVE_REVISION,
                result.items().get(0).status());
        assertEquals("revision-before-window", result.items().get(0).revisionToken());
        assertEquals(windowStart, result.items().get(0).windowStart());
        assertEquals(PostPublicRevisionBoundaryProjection.Status.EFFECTIVE_REVISION,
                result.items().get(1).status());
        assertEquals(windowStart.plusMinutes(1), result.items().get(1).windowStart());
        verify(versionMapper, never()).selectMaxPublicContentRevisionBoundaryPostId(anyInt());
    }

    @Test
    void unavailableSchemaDoesNotPretendTheChannelHasNoEffectiveRevision() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(5);
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.queryPublicRevisionBoundaryPage(query(windowStart, 0L, null, 10));

        assertFalse(result.available());
        assertTrue(result.items().isEmpty());
        assertNull(result.nextCursor());
        assertNull(result.snapshotUpperBoundPostId());
        verify(versionMapper, never()).selectMaxPublicContentRevisionBoundaryPostId(anyInt());
        verify(versionMapper, never()).selectPublicContentRevisionBoundaryRows(
                anyInt(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void outOfOrderRowsFailClosedWithoutAdvancingTheCursor() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        when(versionMapper.selectPublicContentRevisionBoundaryRows(3, 100L, 250L, 2))
                .thenReturn(List.of(
                        row(102L, 9L, 3, "revision-102", windowStart.plusMinutes(2)),
                        row(101L, 9L, 3, "revision-101", windowStart.plusMinutes(1))));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.queryPublicRevisionBoundaryPage(query(windowStart, 100L, 250L, 2));

        assertFalse(result.available());
        assertTrue(result.items().isEmpty());
        assertNull(result.nextCursor());
        assertNull(result.snapshotUpperBoundPostId());
        verify(versionMapper, never()).existsPublicContentRevisionBoundaryAfter(
                anyInt(), anyLong(), anyLong());
    }

    @Test
    void nonQualifyingRowsFailClosedWithoutSkippingTheirKeysetPosition() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        PostContentRevisionQueryRow invalid = row(101L, 9L, 3, "revision-101", windowStart.plusMinutes(1));
        invalid.setAuthorId(null);
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        when(versionMapper.selectPublicContentRevisionBoundaryRows(3, 100L, 250L, 2))
                .thenReturn(List.of(
                        invalid,
                        row(102L, 9L, 3, "revision-102", windowStart.plusMinutes(2))));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.queryPublicRevisionBoundaryPage(query(windowStart, 100L, 250L, 2));

        assertFalse(result.available());
        assertTrue(result.items().isEmpty());
        assertNull(result.nextCursor());
        verify(versionMapper, never()).existsPublicContentRevisionBoundaryAfter(
                anyInt(), anyLong(), anyLong());
    }

    @Test
    void anonymousCareerRowsFailClosedEvenIfTheMapperContractIsViolated() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        PostContentRevisionQueryRow anonymousCareer = row(101L, 9L, 2, "revision-101", windowStart.plusMinutes(1));
        anonymousCareer.setAnonymous(true);
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        when(versionMapper.selectPublicContentRevisionBoundaryRows(2, 100L, 250L, 2))
                .thenReturn(List.of(anonymousCareer));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.queryPublicRevisionBoundaryPage(PostPublicRevisionBoundaryPageQuery.authorizedChannel(
                99L, 2, List.of(2), windowStart, 100L, 250L, 2));

        assertFalse(result.available());
        assertTrue(result.items().isEmpty());
        verify(versionMapper, never()).existsPublicContentRevisionBoundaryAfter(
                anyInt(), anyLong(), anyLong());
    }

    @Test
    void nonCommunityRowsFailClosedEvenIfTheMapperContractIsViolated() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        PostContentRevisionQueryRow internal = row(101L, 9L, 3, "revision-101", windowStart.plusMinutes(1));
        internal.setContentEnvironment("INTERNAL");
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        when(versionMapper.selectPublicContentRevisionBoundaryRows(3, 100L, 250L, 2))
                .thenReturn(List.of(internal));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.queryPublicRevisionBoundaryPage(query(windowStart, 100L, 250L, 2));

        assertFalse(result.available());
        assertTrue(result.items().isEmpty());
        verify(versionMapper, never()).existsPublicContentRevisionBoundaryAfter(
                anyInt(), anyLong(), anyLong());
    }

    @Test
    void unauthorizedChannelIsAnEmptyProjectionAndNeverTouchesPersistence() {
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 4, 0, 0);
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);
        var query = PostPublicRevisionBoundaryPageQuery.authorizedChannel(
                99L, 3, List.of(4), windowStart, 0L, null, 10);

        var result = service.queryPublicRevisionBoundaryPage(query);

        assertTrue(result.available());
        assertTrue(result.items().isEmpty());
        verify(versionMapper, never()).qualitySignalSchemaColumnCount();
        verify(versionMapper, never()).selectPublicContentRevisionBoundaryRows(
                anyInt(), anyLong(), anyLong(), anyInt());
    }

    private static PostPublicRevisionBoundaryPageQuery query(LocalDateTime windowStart,
                                                              Long afterPostId,
                                                              Long snapshotUpperBoundPostId,
                                                              int pageSize) {
        return PostPublicRevisionBoundaryPageQuery.authorizedChannel(
                99L, 3, List.of(3, 4), windowStart, afterPostId, snapshotUpperBoundPostId, pageSize);
    }

    private static PostContentRevisionQueryRow row(Long postId, Long authorId, Integer domain,
                                                    String revisionToken, LocalDateTime effectiveAt) {
        PostContentRevisionQueryRow row = new PostContentRevisionQueryRow();
        row.setPostId(postId);
        row.setAuthorId(authorId);
        row.setDomain(domain);
        row.setIsDeleted(0);
        row.setVisibility(1);
        row.setPostStatus(1);
        row.setContentEnvironment("COMMUNITY");
        row.setLatestEffectiveContentRevisionToken(revisionToken);
        row.setLatestEffectiveContentRevisionAt(effectiveAt);
        row.setQualitySignalRevisionToken(revisionToken);
        row.setQualitySignalRevisionState("EFFECTIVE");
        row.setQualitySignalEffectiveAt(effectiveAt);
        row.setEffectivePublishedPostVersion(1);
        return row;
    }
}
