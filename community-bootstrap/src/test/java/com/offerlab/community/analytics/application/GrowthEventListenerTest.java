package com.offerlab.community.analytics.application;

import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.interaction.api.event.PostFavoritedEvent;
import com.offerlab.community.interaction.api.event.PostLikedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.user.api.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthEventListenerTest {

    @Mock
    private GrowthEventService growthEventService;
    @Mock
    private PostMapper postMapper;

    private GrowthEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new GrowthEventListener(growthEventService, postMapper);
    }

    @Test
    void userRegisteredEventRecordsTrustedRegisterGrowthEvent() {
        listener.onUserRegistered(UserRegisteredEvent.builder()
                .uid(101L)
                .timestamp(123456L)
                .build());

        verify(growthEventService).recordTrustedEvent(
                GrowthEventService.USER_REGISTER,
                101L,
                null,
                null,
                "USER",
                "101",
                "user.register");
    }

    @Test
    void postLikeEventRecordsTrustedInteractionGrowthEvent() {
        listener.onPostLiked(PostLikedEvent.builder()
                .uid(8L)
                .postId(88L)
                .postAuthorId(6L)
                .domain(2)
                .timestamp(123456L)
                .build());

        verify(growthEventService).recordTrustedEvent(
                GrowthEventService.POST_LIKE,
                8L,
                2,
                88L,
                "POST",
                "88",
                "interaction.like");
    }

    @Test
    void postFavoriteEventRecordsTrustedInteractionGrowthEvent() {
        listener.onPostFavorited(PostFavoritedEvent.builder()
                .uid(8L)
                .postId(89L)
                .postAuthorId(6L)
                .domain(3)
                .timestamp(123456L)
                .build());

        verify(growthEventService).recordTrustedEvent(
                GrowthEventService.POST_FAVORITE,
                8L,
                3,
                89L,
                "POST",
                "89",
                "interaction.favorite");
    }

    @Test
    void commentCreatedEventRecordsTrustedInteractionGrowthEvent() {
        listener.onCommentCreated(CommentCreatedEvent.builder()
                .uid(9L)
                .postId(90L)
                .postAuthorId(6L)
                .commentId(7001L)
                .domain(1)
                .timestamp(123456L)
                .build());

        verify(growthEventService).recordTrustedEvent(
                GrowthEventService.POST_COMMENT,
                9L,
                1,
                90L,
                "COMMENT",
                "7001",
                "interaction.comment");
    }

    @Test
    void firstPublishedPostRecordsTrustedCreatorColdStartEvent() {
        when(postMapper.aggregatePublicContributionByAuthor(5L)).thenReturn(Map.of("postCount", 1L));

        listener.onPostPublished(PostPublishedEvent.builder()
                .postId(91L)
                .authorId(5L)
                .domain(4)
                .timestamp(123456L)
                .build());

        verify(growthEventService).recordTrustedEvent(
                GrowthEventService.FIRST_POST_PUBLISHED,
                5L,
                4,
                91L,
                "POST",
                "91",
                "post.publish");
    }

    @Test
    void nonFirstPublishedPostDoesNotRecordCreatorColdStartEvent() {
        when(postMapper.aggregatePublicContributionByAuthor(5L)).thenReturn(Map.of("postCount", 2L));

        listener.onPostPublished(PostPublishedEvent.builder()
                .postId(91L)
                .authorId(5L)
                .domain(4)
                .timestamp(123456L)
                .build());

        verify(growthEventService, never()).recordTrustedEvent(
                GrowthEventService.FIRST_POST_PUBLISHED,
                5L,
                4,
                91L,
                "POST",
                "91",
                "post.publish");
    }
}
