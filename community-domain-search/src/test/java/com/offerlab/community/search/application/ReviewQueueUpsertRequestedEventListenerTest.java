package com.offerlab.community.search.application;

import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueueUpsertRequestedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewQueueUpsertRequestedEventListenerTest {

    @Test
    void delegatesTheSharedEventToTheReviewQueueService() {
        ReviewQueueService service = mock(ReviewQueueService.class);
        ReviewQueueUpsertRequestedEventListener listener = new ReviewQueueUpsertRequestedEventListener(service);
        ReviewQueueItemCommand command = new ReviewQueueItemCommand(
                "MODERATION_HIT",
                42L,
                "title",
                "summary",
                "medium",
                7L,
                60,
                "{}",
                "test"
        );

        listener.onReviewQueueUpsertRequested(new ReviewQueueUpsertRequestedEvent(command));

        verify(service).upsert(command);
    }
}
