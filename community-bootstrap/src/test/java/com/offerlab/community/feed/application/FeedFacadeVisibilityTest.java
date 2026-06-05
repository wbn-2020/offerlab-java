package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.infrastructure.FeedFeedbackStore;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedFacadeVisibilityTest {

    @Mock
    private FeedInboxRedis feedRedis;
    @Mock
    private FeedFeedbackStore feedbackStore;
    @Mock
    private PostFacade postFacade;
    @Mock
    private UserFacade userFacade;
    @Mock
    private InteractionFacade interactionFacade;

    private FeedFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new FeedFacadeImpl(feedRedis, feedbackStore, postFacade, userFacade, interactionFacade, new ObjectMapper());
    }

    @Test
    void followingFeedFiltersInvisibleRedisEntriesButKeepsPaginationCursor() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(tuple("100", 1000D));
        tuples.add(tuple("101", 900D));
        when(feedRedis.readInboxWithScore(7L, Double.MAX_VALUE, 2)).thenReturn(tuples);

        PostBriefDTO visible = PostBriefDTO.builder()
                .id(101L)
                .authorId(20L)
                .title("visible")
                .summary("visible")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.batchGetPosts(List.of(100L, 101L), 7L)).thenReturn(Map.of(101L, visible));
        when(postFacade.batchGetCounters(List.of(100L, 101L))).thenReturn(Map.of(
                101L, PostCounterDTO.builder().postId(101L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(20L))).thenReturn(Map.of(
                20L, UserBriefDTO.builder().uid(20L).nickname("author").build()));
        when(interactionFacade.hasLiked(7L, 101L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 101L)).thenReturn(false);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(101L, page.getItems().get(0).getPost().getId());
        assertTrue(page.getHasMore());
        assertEquals("900", page.getNextCursor());
        verify(postFacade).batchGetPosts(List.of(100L, 101L), 7L);
    }

    @Test
    void latestFeedUsesDatabaseLatestSoFreshPublishedPostsDoNotDependOnRedisFanout() {
        PostBriefDTO fresh = PostBriefDTO.builder()
                .id(202L)
                .authorId(22L)
                .title("fresh published post")
                .summary("fresh")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(fresh), null, false));
        when(postFacade.batchGetCounters(List.of(202L))).thenReturn(Map.of(
                202L, PostCounterDTO.builder().postId(202L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(22L))).thenReturn(Map.of(
                22L, UserBriefDTO.builder().uid(22L).nickname("author").build()));

        PageResult<FeedItemVO> page = facade.getLatestFeed(null, null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(202L, page.getItems().get(0).getPost().getId());
        assertEquals(Boolean.FALSE, page.getHasMore());
    }

    @Test
    void latestFeedFirstPageMergesRedisGlobalLatestWithDatabaseFallback() {
        PostBriefDTO dbPost = PostBriefDTO.builder()
                .id(301L)
                .authorId(31L)
                .title("database latest")
                .summary("db")
                .createTime(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        PostBriefDTO redisPost = PostBriefDTO.builder()
                .id(302L)
                .authorId(32L)
                .title("redis fanout latest")
                .summary("redis")
                .createTime(LocalDateTime.of(2025, 12, 31, 23, 0))
                .build();
        ZSetOperations.TypedTuple<String> redisTuple = tuple("302", 1_800_000_000_000D);
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(dbPost), "db-cursor", true));
        when(feedRedis.readGlobalLatest(Double.MAX_VALUE, 2)).thenReturn(Set.of(redisTuple));
        when(postFacade.batchGetPosts(List.of(302L), null)).thenReturn(Map.of(302L, redisPost));
        when(postFacade.batchGetCounters(List.of(301L))).thenReturn(Map.of(
                301L, PostCounterDTO.builder().postId(301L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(postFacade.batchGetCounters(List.of(302L))).thenReturn(Map.of(
                302L, PostCounterDTO.builder().postId(302L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(31L))).thenReturn(Map.of(
                31L, UserBriefDTO.builder().uid(31L).nickname("db-author").build()));
        when(userFacade.batchGetUserBriefs(Set.of(32L))).thenReturn(Map.of(
                32L, UserBriefDTO.builder().uid(32L).nickname("redis-author").build()));

        PageResult<FeedItemVO> page = facade.getLatestFeed(null, null, 2);

        assertEquals(2, page.getItems().size());
        assertEquals(302L, page.getItems().get(0).getPost().getId());
        assertEquals(301L, page.getItems().get(1).getPost().getId());
        assertEquals("db-cursor", page.getNextCursor());
        assertEquals(Boolean.TRUE, page.getHasMore());
    }

    @Test
    void latestFeedFirstPageFallsBackToDatabaseWhenRedisLatestFails() {
        PostBriefDTO dbPost = PostBriefDTO.builder()
                .id(401L)
                .authorId(41L)
                .title("database fallback")
                .summary("db")
                .createTime(LocalDateTime.of(2026, 1, 2, 0, 0))
                .build();
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(dbPost), "db-fallback", false));
        doThrow(new RuntimeException("redis down")).when(feedRedis).readGlobalLatest(Double.MAX_VALUE, 2);
        when(postFacade.batchGetCounters(List.of(401L))).thenReturn(Map.of(
                401L, PostCounterDTO.builder().postId(401L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(41L))).thenReturn(Map.of(
                41L, UserBriefDTO.builder().uid(41L).nickname("db-author").build()));

        PageResult<FeedItemVO> page = facade.getLatestFeed(null, null, 2);

        assertEquals(1, page.getItems().size());
        assertEquals(401L, page.getItems().get(0).getPost().getId());
        assertEquals("db-fallback", page.getNextCursor());
        assertEquals(Boolean.FALSE, page.getHasMore());
    }

    @SuppressWarnings("unchecked")
    private static ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
