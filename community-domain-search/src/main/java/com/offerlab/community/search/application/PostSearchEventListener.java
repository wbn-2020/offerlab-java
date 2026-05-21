package com.offerlab.community.search.application;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostPublished(PostPublishedEvent event) {
        if (!indexer.indexPost(event.getPostId())) {
            log.debug("post published index sync skipped or failed: postId={}", event.getPostId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostUpdated(PostUpdatedEvent event) {
        if (!indexer.indexPost(event.getPostId())) {
            log.debug("post updated index sync skipped or failed: postId={}", event.getPostId());
        }
    }
}
