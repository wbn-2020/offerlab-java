package com.offerlab.community.feed.application;

import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.infra.mq.idempotent.IdempotentChecker;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.user.api.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedFanoutIdempotencyTest {

    @Mock
    private UserFacade userFacade;
    @Mock
    private FeedInboxRedis feedRedis;
    @Mock
    private IdempotentChecker idempotentChecker;

    private FeedFanoutService service;

    @BeforeEach
    void setUp() {
        service = new FeedFanoutService(userFacade, feedRedis, idempotentChecker);
    }

    @Test
    void duplicatePostPublishedEventIsSkippedWithoutFanoutWork() {
        when(idempotentChecker.tryConsume("post.published:88", "feed-fanout")).thenReturn(false);

        boolean processed = service.fanoutPostPublished(event(88L, 7L, Post.VIS_PUBLIC, Post.STATUS_PUBLISHED, 123456L), "kafka");

        assertFalse(processed);
        verifyNoInteractions(userFacade, feedRedis);
        verify(idempotentChecker, never()).release(anyString(), anyString());
    }

    @Test
    void fanoutFailureReleasesIdempotentKeyBeforeRethrow() {
        when(idempotentChecker.tryConsume("post.published:88", "feed-fanout")).thenReturn(true);
        doThrow(new IllegalStateException("redis down"))
                .when(feedRedis).addToAuthorTimeline(7L, 88L, 123456L);

        assertThrows(IllegalStateException.class,
                () -> service.fanoutPostPublished(event(88L, 7L, Post.VIS_PUBLIC, Post.STATUS_PUBLISHED, 123456L), "kafka"));

        verify(idempotentChecker).release("post.published:88", "feed-fanout");
        verify(feedRedis, never()).addToGlobalLatest(anyLong(), anyLong());
        verifyNoInteractions(userFacade);
    }

    private static PostPublishedEvent event(Long postId, Long authorId, Integer visibility, Integer postStatus, Long timestamp) {
        return PostPublishedEvent.builder()
                .postId(postId)
                .authorId(authorId)
                .visibility(visibility)
                .postStatus(postStatus)
                .timestamp(timestamp)
                .title("post")
                .content("content")
                .build();
    }
}
