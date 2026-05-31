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
            if (!indexer.indexPost(event.getPostId())) {
                log.warn("post published index sync skipped or failed: postId={}", event.getPostId());
                retryService.enqueueIndex(event.getPostId(), null);
            }
        } catch (Exception e) {
            log.warn("post published index sync failed: postId={}", event.getPostId(), e);
            retryService.enqueueIndex(event.getPostId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostUpdated(PostUpdatedEvent event) {
        try {
            if (!indexer.indexPost(event.getPostId())) {
                log.warn("post updated index sync skipped or failed: postId={}", event.getPostId());
                retryService.enqueueIndex(event.getPostId(), null);
            }
        } catch (Exception e) {
            log.warn("post updated index sync failed: postId={}", event.getPostId(), e);
            retryService.enqueueIndex(event.getPostId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostDeleted(PostDeletedEvent event) {
        try {
            if (!indexer.deletePost(event.getPostId())) {
                log.warn("post deleted index sync skipped or failed: postId={}", event.getPostId());
                retryService.enqueueDelete(event.getPostId(), null);
            }
        } catch (Exception e) {
            log.warn("post deleted index sync failed: postId={}", event.getPostId(), e);
            retryService.enqueueDelete(event.getPostId(), e);
        }
    }
}
