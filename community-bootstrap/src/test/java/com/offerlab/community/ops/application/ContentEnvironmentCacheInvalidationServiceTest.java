package com.offerlab.community.ops.application;

import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.infra.redis.cache.RequiredCacheEvictor;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.question.application.QuestionFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentEnvironmentCacheInvalidationServiceTest {

    @Mock private PostFacade postFacade;
    @Mock private QuestionFacade questionFacade;
    @Mock private FeedInboxRedis feedInboxRedis;
    @Mock private RequiredCacheEvictor requiredCacheEvictor;

    private ContentEnvironmentCacheInvalidationService service;

    @BeforeEach
    void setUp() {
        service = new ContentEnvironmentCacheInvalidationService(
                postFacade, questionFacade, feedInboxRedis, requiredCacheEvictor);
    }

    @Test
    void invalidatesExactKeysAndRemovesOnlyNonCommunityPostsFromFeed() {
        when(postFacade.getPostMetadata(101L)).thenReturn(post(101L, 11L, Post.CONTENT_ENVIRONMENT_COMMUNITY));
        when(postFacade.getPostMetadata(102L)).thenReturn(post(102L, 12L, Post.CONTENT_ENVIRONMENT_TEST));
        when(questionFacade.resolveCompanyPrepCacheKeysForPosts(List.of(101L, 102L)))
                .thenReturn(List.of("question:company-prep:OfferLab"));
        when(requiredCacheEvictor.evictExact(anyCollection())).thenAnswer(invocation ->
                ((Collection<?>) invocation.getArgument(0)).size());
        when(feedInboxRedis.removePostsRequired(Map.of(12L, List.of(102L))))
                .thenReturn(new FeedInboxRedis.RemovalResult(1L, 1L, 2L, 2));

        ContentEnvironmentCacheInvalidationService.InvalidationResult result =
                service.invalidate("op-1", List.of(101L, 102L));

        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(requiredCacheEvictor).evictExact(keys.capture());
        assertEquals(List.of(
                "post:detail:101",
                "post:detail:raw:101",
                "post:counter:101",
                "post:detail:102",
                "post:detail:raw:102",
                "post:counter:102",
                "question:company-prep:OfferLab"
        ), List.copyOf(keys.getValue()));
        assertEquals(7, result.exactCacheKeysEvicted());
        assertEquals(1, result.questionCompanyCacheKeysEvicted());
        assertEquals(1L, result.globalLatestEntriesRemoved());
        assertEquals(2L, result.followerInboxEntriesRemoved());
        assertEquals(2, result.followerInboxesChecked());
    }

    @Test
    void rejectsMissingExactPostBeforeAnyCacheMutation() {
        when(postFacade.getPostMetadata(999L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.invalidate("op-2", List.of(999L)));

        verify(requiredCacheEvictor, never()).evictExact(anyCollection());
        verify(feedInboxRedis, never()).removePostsRequired(
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void rejectsDuplicateIdsRatherThanSilentlyChangingTheApprovedSet() {
        assertThrows(IllegalArgumentException.class,
                () -> service.invalidate("op-3", List.of(101L, 101L)));

        verify(postFacade, never()).getPostMetadata(org.mockito.ArgumentMatchers.anyLong());
    }

    private static PostDTO post(Long id, Long authorId, String environment) {
        return PostDTO.builder()
                .id(id)
                .authorId(authorId)
                .contentEnvironment(environment)
                .build();
    }
}
