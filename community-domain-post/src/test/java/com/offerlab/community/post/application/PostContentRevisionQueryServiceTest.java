package com.offerlab.community.post.application;

import com.offerlab.community.post.api.quality.PostContentRevisionQuery;
import com.offerlab.community.post.api.quality.PostContentRevisionSnapshot;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostVersionHistoryMapper;
import com.offerlab.community.post.infrastructure.persistence.projection.PostContentRevisionQueryRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostContentRevisionQueryServiceTest {

    @Mock
    private PostVersionHistoryMapper versionMapper;

    @Test
    void missingSchemaIsUnavailableForEveryRequestedPost() {
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(5);
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 1, 0, 0);

        var result = service.query(PostContentRevisionQuery.authorOwned(7L, List.of(101L, 102L), windowStart));

        assertFalse(result.available());
        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(item ->
                item.status() == PostContentRevisionSnapshot.Status.UNAVAILABLE
                        && !item.hasEffectiveRevision()
                        && item.windowStart().equals(windowStart)));
        verify(versionMapper, never()).selectContentRevisionQueryRows(anyList());
    }

    @Test
    void ownerScopeReturnsOnlyVerifiedEffectiveBoundaryInsideWindow() {
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(versionMapper.selectContentRevisionQueryRows(List.of(101L, 102L, 103L, 104L)))
                .thenReturn(List.of(
                        row(101L, 7L, 1, "token-101", 9, windowStart.plusHours(1), 1),
                        row(102L, 7L, 1, null, null, null, 1),
                        row(103L, 8L, 1, "token-103", 8, windowStart.plusHours(1), 1)
                ));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.query(PostContentRevisionQuery.authorOwned(
                7L, List.of(101L, 102L, 103L, 104L), windowStart));

        assertTrue(result.available());
        assertEquals(PostContentRevisionSnapshot.Status.FOUND, result.items().get(0).status());
        assertTrue(result.items().get(0).hasEffectiveRevision());
        assertEquals("token-101", result.items().get(0).revisionToken());
        assertEquals(9, result.items().get(0).effectivePublishedPostVersion());
        assertEquals(PostContentRevisionSnapshot.Status.NO_EFFECTIVE_REVISION, result.items().get(1).status());
        assertEquals("baseline-102", result.items().get(1).revisionToken());
        assertEquals(PostContentRevisionSnapshot.Status.NOT_ELIGIBLE, result.items().get(2).status());
        assertEquals(PostContentRevisionSnapshot.Status.NOT_ELIGIBLE, result.items().get(3).status());
    }

    @Test
    void authorizedChannelScopeRequiresAnAllowedChannel() {
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(versionMapper.selectContentRevisionQueryRows(List.of(201L)))
                .thenReturn(List.of(row(201L, 11L, 3, "token-201", 6, windowStart.plusMinutes(1), 1)));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.query(PostContentRevisionQuery.authorizedChannel(
                99L, List.of(201L), windowStart, List.of(3)));

        assertEquals(PostContentRevisionSnapshot.Status.FOUND, result.items().get(0).status());
    }

    @Test
    void noEffectiveRevisionRetainsTheLatestOpaqueTokenWhenItPredatesTheWindow() {
        when(versionMapper.qualitySignalSchemaColumnCount()).thenReturn(6);
        LocalDateTime windowStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(versionMapper.selectContentRevisionQueryRows(List.of(301L)))
                .thenReturn(List.of(row(301L, 7L, 1, "token-before-window", 3,
                        windowStart.minusMinutes(1), 1)));
        PostContentRevisionQueryService service = new PostContentRevisionQueryService(versionMapper);

        var result = service.query(PostContentRevisionQuery.authorOwned(7L, List.of(301L), windowStart));

        assertEquals(PostContentRevisionSnapshot.Status.NO_EFFECTIVE_REVISION, result.items().get(0).status());
        assertEquals("token-before-window", result.items().get(0).revisionToken());
        assertFalse(result.items().get(0).hasEffectiveRevision());
    }

    private static PostContentRevisionQueryRow row(Long postId, Long authorId, Integer domain, String token,
                                                    Integer resultVersion, LocalDateTime effectiveAt,
                                                    Integer postStatus) {
        PostContentRevisionQueryRow row = new PostContentRevisionQueryRow();
        row.setPostId(postId);
        row.setAuthorId(authorId);
        row.setDomain(domain);
        row.setIsDeleted(0);
        row.setVisibility(1);
        row.setPostStatus(postStatus);
        row.setLatestEffectiveContentRevisionToken(token);
        row.setLatestEffectiveContentRevisionAt(effectiveAt);
        row.setQualitySignalRevisionToken(token);
        row.setQualitySignalRevisionState(token == null ? null : "EFFECTIVE");
        row.setQualitySignalEffectiveAt(effectiveAt);
        row.setEffectivePublishedPostVersion(resultVersion);
        return row;
    }
}
