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
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    @Mock
    private RecommendFeedNewCreatorSupportRecorder recommendFeedNewCreatorSupportRecorder;

    private FeedFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new FeedFacadeImpl(
                feedRedis,
                feedbackStore,
                postFacade,
                userFacade,
                interactionFacade,
                new ObjectMapper(),
                recommendFeedNewCreatorSupportRecorder);
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

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

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

        PageResult<FeedItemVO> page = facade.getLatestFeed(null, null, 2, null);

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

        PageResult<FeedItemVO> page = facade.getLatestFeed(null, null, 2, null);

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

        PageResult<FeedItemVO> page = facade.getLatestFeed(null, null, 2, null);

        assertEquals(1, page.getItems().size());
        assertEquals(401L, page.getItems().get(0).getPost().getId());
        assertEquals("db-fallback", page.getNextCursor());
        assertEquals(Boolean.FALSE, page.getHasMore());
    }

    @Test
    void recommendFeedExplainsWithCommunitySignalsInsteadOfJobSearchTargets() {
        PostBriefDTO post = PostBriefDTO.builder()
                .id(501L)
                .authorId(51L)
                .title("Redis 缓存击穿治理复盘")
                .summary("支付链路里的降级、限流和压测复盘")
                .extJson("{\"contentType\":\"PROJECT_REVIEW\",\"techStacks\":[\"Redis\",\"Spring Boot\"],\"scenario\":\"支付链路\"}")
                .counter(PostCounterDTO.builder()
                        .postId(501L)
                        .viewCount(12L)
                        .likeCount(1L)
                        .commentCount(1L)
                        .favoriteCount(1L)
                        .build())
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.getLatest(0L, 1)).thenReturn(PageResult.of(List.of(post), null, false));
        when(userFacade.getUserIntent(7L)).thenReturn(UserIntentDTO.builder()
                .targetCompanies(List.of("Redis"))
                .targetPositions(List.of("支付链路"))
                .techStack(List.of("Redis"))
                .build());
        when(feedbackStore.hiddenPostIds(7L)).thenReturn(Set.of());
        when(postFacade.batchGetCounters(List.of(501L))).thenReturn(Map.of(
                501L, PostCounterDTO.builder().postId(501L).viewCount(12L).likeCount(1L).commentCount(1L).favoriteCount(1L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(51L))).thenReturn(Map.of(
                51L, UserBriefDTO.builder().uid(51L).nickname("author").build()));
        when(interactionFacade.hasLiked(7L, 501L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 501L)).thenReturn(false);

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 1, null);

        List<String> reasons = page.getItems().get(0).getRecommendationReasons();
        assertEquals(List.of(
                "覆盖你关注的工程场景：Redis",
                "关联你关注的技术主题：支付链路",
                "包含你关注的技术栈"), reasons);
        String joined = String.join(" ", reasons);
        assertFalse(joined.contains("目标公司"));
        assertFalse(joined.contains("目标岗位"));
    }

    @Test
    void recommendFeedDoesNotAdvanceCursorPastUnshownCandidates() {
        PostBriefDTO first = PostBriefDTO.builder()
                .id(551L)
                .authorId(55L)
                .title("first")
                .summary("first")
                .counter(PostCounterDTO.builder()
                        .postId(551L)
                        .viewCount(3L)
                        .likeCount(0L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(LocalDateTime.now())
                .build();
        PostBriefDTO second = PostBriefDTO.builder()
                .id(552L)
                .authorId(56L)
                .title("second")
                .summary("second")
                .counter(PostCounterDTO.builder()
                        .postId(552L)
                        .viewCount(2L)
                        .likeCount(0L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(LocalDateTime.now().minusMinutes(1))
                .build();
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(first, second), "cursor-after-visible-page", true));
        when(userFacade.getUserIntent(7L)).thenReturn(UserIntentDTO.builder().build());
        when(feedbackStore.hiddenPostIds(7L)).thenReturn(Set.of());
        when(postFacade.batchGetCounters(List.of(551L, 552L))).thenReturn(Map.of(
                551L, PostCounterDTO.builder().postId(551L).viewCount(3L).likeCount(0L).commentCount(0L).favoriteCount(0L).build(),
                552L, PostCounterDTO.builder().postId(552L).viewCount(2L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(55L, 56L))).thenReturn(Map.of(
                55L, UserBriefDTO.builder().uid(55L).nickname("first-author").build(),
                56L, UserBriefDTO.builder().uid(56L).nickname("second-author").build()));
        when(interactionFacade.hasLiked(7L, 551L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 551L)).thenReturn(false);
        when(interactionFacade.hasLiked(7L, 552L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 552L)).thenReturn(false);

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 2, null);

        verify(postFacade).getLatest(0L, 2);
        assertEquals(2, page.getItems().size());
        assertEquals("cursor-after-visible-page", page.getNextCursor());
        assertEquals(Boolean.TRUE, page.getHasMore());
    }

    @Test
    void recommendFeedRanksInterestMatchedPostsFirstAndExplainsInterestReason() {
        PostBriefDTO genericFreshPost = PostBriefDTO.builder()
                .id(601L)
                .authorId(61L)
                .title("本周社区热门讨论合集")
                .summary("整理最近更新的通用讨论")
                .tags(List.of(TagDTO.builder().name("社区动态").build()))
                .counter(PostCounterDTO.builder()
                        .postId(601L)
                        .viewCount(20L)
                        .likeCount(1L)
                        .commentCount(1L)
                        .favoriteCount(0L)
                        .build())
                .createTime(LocalDateTime.now())
                .build();
        PostBriefDTO interestMatchedPost = PostBriefDTO.builder()
                .id(602L)
                .authorId(62L)
                .title("租房生活避坑清单")
                .summary("城市生活里的通勤、合租和预算经验复盘")
                .extJson("{\"contentType\":\"图文笔记\",\"topic\":\"城市生活\"}")
                .tags(List.of(TagDTO.builder().name("租房生活").build()))
                .counter(PostCounterDTO.builder()
                        .postId(602L)
                        .viewCount(0L)
                        .likeCount(0L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(LocalDateTime.now().minusHours(48))
                .build();
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(genericFreshPost, interestMatchedPost), null, false));
        when(userFacade.getUserIntent(7L)).thenReturn(UserIntentDTO.builder()
                .interestTopics(List.of("城市生活"))
                .interestTags(List.of("租房生活"))
                .contentPreferences(List.of("图文笔记"))
                .build());
        when(feedbackStore.hiddenPostIds(7L)).thenReturn(Set.of());
        when(postFacade.batchGetCounters(List.of(602L, 601L))).thenReturn(Map.of(
                601L, PostCounterDTO.builder().postId(601L).viewCount(20L).likeCount(1L).commentCount(1L).favoriteCount(0L).build(),
                602L, PostCounterDTO.builder().postId(602L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(61L, 62L))).thenReturn(Map.of(
                61L, UserBriefDTO.builder().uid(61L).nickname("fresh-author").build(),
                62L, UserBriefDTO.builder().uid(62L).nickname("matched-author").build()));
        when(interactionFacade.hasLiked(7L, 602L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 602L)).thenReturn(false);
        when(interactionFacade.hasLiked(7L, 601L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 601L)).thenReturn(false);

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 2, null);

        assertEquals(2, page.getItems().size());
        assertEquals(602L, page.getItems().get(0).getPost().getId());
        String reasons = String.join(" ", page.getItems().get(0).getRecommendationReasons());
        assertTrue(reasons.contains("兴趣"));
        assertTrue(reasons.contains("租房生活") || reasons.contains("城市生活") || reasons.contains("图文笔记"));
    }

    @Test
    void recommendFeedBoostsNewCreatorWithinFirstThreePublishedPosts() {
        LocalDateTime now = LocalDateTime.now();
        PostBriefDTO establishedCreatorPost = PostBriefDTO.builder()
                .id(701L)
                .authorId(71L)
                .title("established creator")
                .summary("established creator")
                .counter(PostCounterDTO.builder()
                        .postId(701L)
                        .viewCount(10L)
                        .likeCount(1L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(now.minusHours(2))
                .build();
        PostBriefDTO newCreatorPost = PostBriefDTO.builder()
                .id(702L)
                .authorId(72L)
                .title("new creator")
                .summary("new creator")
                .counter(PostCounterDTO.builder()
                        .postId(702L)
                        .viewCount(0L)
                        .likeCount(0L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(now.minusHours(6))
                .build();
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(establishedCreatorPost, newCreatorPost), null, false));
        when(userFacade.getUserIntent(7L)).thenReturn(UserIntentDTO.builder().build());
        when(feedbackStore.hiddenPostIds(7L)).thenReturn(Set.of());
        when(postFacade.batchCountPublicPublishedPostsByAuthors(Set.of(71L, 72L))).thenReturn(Map.of(
                71L, 4L,
                72L, 3L));
        when(postFacade.batchGetCounters(anyCollection())).thenReturn(Map.of(
                701L, PostCounterDTO.builder().postId(701L).viewCount(10L).likeCount(1L).commentCount(0L).favoriteCount(0L).build(),
                702L, PostCounterDTO.builder().postId(702L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(71L, 72L))).thenReturn(Map.of(
                71L, UserBriefDTO.builder().uid(71L).nickname("established").build(),
                72L, UserBriefDTO.builder().uid(72L).nickname("new").build()));
        when(interactionFacade.hasLiked(7L, 701L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 701L)).thenReturn(false);
        when(interactionFacade.hasLiked(7L, 702L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 702L)).thenReturn(false);

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 2, null);

        assertEquals(2, page.getItems().size());
        assertEquals(702L, page.getItems().get(0).getPost().getId());
        assertTrue(page.getItems().get(0).getRecommendationReasons().contains("新作者前 3 篇内容扶持"));
        assertFalse(page.getItems().get(1).getRecommendationReasons().contains("新作者前 3 篇内容扶持"));
        verify(postFacade).batchCountPublicPublishedPostsByAuthors(Set.of(71L, 72L));
    }

    @Test
    void recommendFeedRecordsDeliveredAndSupportHitCountsFromReturnedPage() {
        LocalDateTime now = LocalDateTime.now();
        PostBriefDTO establishedCreatorPost = PostBriefDTO.builder()
                .id(801L)
                .authorId(81L)
                .title("established creator")
                .summary("established creator")
                .counter(PostCounterDTO.builder()
                        .postId(801L)
                        .viewCount(3L)
                        .likeCount(1L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(now.minusHours(1))
                .build();
        PostBriefDTO newCreatorPost = PostBriefDTO.builder()
                .id(802L)
                .authorId(82L)
                .title("new creator")
                .summary("new creator")
                .counter(PostCounterDTO.builder()
                        .postId(802L)
                        .viewCount(0L)
                        .likeCount(0L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(now.minusHours(4))
                .build();
        when(postFacade.getLatest(0L, 2)).thenReturn(PageResult.of(List.of(establishedCreatorPost, newCreatorPost), null, false));
        when(userFacade.getUserIntent(7L)).thenReturn(UserIntentDTO.builder().build());
        when(feedbackStore.hiddenPostIds(7L)).thenReturn(Set.of());
        when(postFacade.batchCountPublicPublishedPostsByAuthors(Set.of(81L, 82L))).thenReturn(Map.of(
                81L, 6L,
                82L, 2L));
        when(postFacade.batchGetCounters(anyCollection())).thenReturn(Map.of(
                801L, PostCounterDTO.builder().postId(801L).viewCount(3L).likeCount(1L).commentCount(0L).favoriteCount(0L).build(),
                802L, PostCounterDTO.builder().postId(802L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(81L, 82L))).thenReturn(Map.of(
                81L, UserBriefDTO.builder().uid(81L).nickname("established").build(),
                82L, UserBriefDTO.builder().uid(82L).nickname("new").build()));
        when(interactionFacade.hasLiked(7L, 801L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 801L)).thenReturn(false);
        when(interactionFacade.hasLiked(7L, 802L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 802L)).thenReturn(false);

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 2, null);

        assertEquals(List.of(802L, 801L), page.getItems().stream().map(item -> item.getPost().getId()).toList());
        assertTrue(page.getItems().get(0).getRecommendationReasons().contains("新作者前 3 篇内容扶持"));
        verify(recommendFeedNewCreatorSupportRecorder).recordRecommendFeedResponse(7L, null, 2, 1);
    }

    @Test
    void recommendFeedStatsFailureDoesNotBlockFeedResponse() {
        LocalDateTime now = LocalDateTime.now();
        PostBriefDTO newCreatorPost = PostBriefDTO.builder()
                .id(901L)
                .authorId(91L)
                .title("new creator")
                .summary("new creator")
                .counter(PostCounterDTO.builder()
                        .postId(901L)
                        .viewCount(0L)
                        .likeCount(0L)
                        .commentCount(0L)
                        .favoriteCount(0L)
                        .build())
                .createTime(now.minusHours(2))
                .build();
        when(postFacade.getLatest(0L, 1)).thenReturn(PageResult.of(List.of(newCreatorPost), null, false));
        when(userFacade.getUserIntent(7L)).thenReturn(UserIntentDTO.builder().build());
        when(feedbackStore.hiddenPostIds(7L)).thenReturn(Set.of());
        when(postFacade.batchCountPublicPublishedPostsByAuthors(Set.of(91L))).thenReturn(Map.of(91L, 1L));
        when(postFacade.batchGetCounters(List.of(901L))).thenReturn(Map.of(
                901L, PostCounterDTO.builder().postId(901L).viewCount(0L).likeCount(0L).commentCount(0L).favoriteCount(0L).build()));
        when(userFacade.batchGetUserBriefs(Set.of(91L))).thenReturn(Map.of(
                91L, UserBriefDTO.builder().uid(91L).nickname("new").build()));
        when(interactionFacade.hasLiked(7L, 901L)).thenReturn(false);
        when(interactionFacade.hasFavorited(7L, 901L)).thenReturn(false);
        doThrow(new RuntimeException("stats write failed"))
                .when(recommendFeedNewCreatorSupportRecorder)
                .recordRecommendFeedResponse(7L, null, 1, 1);

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 1, null);

        assertEquals(1, page.getItems().size());
        assertEquals(901L, page.getItems().get(0).getPost().getId());
        assertTrue(page.getItems().get(0).getRecommendationReasons().contains("新作者前 3 篇内容扶持"));
    }

    @SuppressWarnings("unchecked")
    private static ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
