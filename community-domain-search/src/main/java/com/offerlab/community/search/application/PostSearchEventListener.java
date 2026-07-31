package com.offerlab.community.search.application;

import com.offerlab.community.post.api.event.PostDeletedEvent;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.api.event.PostUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostSearchEventListener {

    private final PostSearchIndexer indexer;
    private final SearchIndexRetryService retryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        try {
            handlePostPublishedSynchronously(event);
        } catch (Exception e) {
            log.warn("post published index sync failed: postId={}", event.getPostId(), e);
            retryService.enqueueIndexRequired(event.getPostId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostUpdated(PostUpdatedEvent event) {
        try {
            handlePostUpdatedSynchronously(event);
        } catch (Exception e) {
            log.warn("post updated index sync failed: postId={}", event.getPostId(), e);
            retryService.enqueueIndexRequired(event.getPostId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostDeleted(PostDeletedEvent event) {
        try {
            handlePostDeletedSynchronously(event);
        } catch (Exception e) {
            log.warn("post deleted index sync failed: postId={}", event.getPostId(), e);
            retryService.enqueueDeleteRequired(event.getPostId(), e);
        }
    }

    public void handlePostPublishedSynchronously(PostPublishedEvent event) {
        indexPost(requirePostId(event == null ? null : event.getPostId()), "published");
    }

    public void handlePostUpdatedSynchronously(PostUpdatedEvent event) {
        indexPost(requirePostId(event == null ? null : event.getPostId()), "updated");
    }

    public void handlePostDeletedSynchronously(PostDeletedEvent event) {
        Long postId = requirePostId(event == null ? null : event.getPostId());
        if (!indexer.deletePost(postId)) {
            throw new IllegalStateException("post search delete returned false: postId=" + postId);
        }
    }

    private void indexPost(Long postId, String operation) {
        if (!indexer.indexPost(postId)) {
            throw new IllegalStateException(
                    "post search index returned false after " + operation + ": postId=" + postId);
        }
    }

    private Long requirePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required for search synchronization");
        }
        return postId;
    }
}
