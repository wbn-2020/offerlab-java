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

    @SuppressWarnings("unchecked")
    private static ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
