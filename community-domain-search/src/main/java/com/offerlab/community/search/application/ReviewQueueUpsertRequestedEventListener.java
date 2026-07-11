package com.offerlab.community.search.application;

import com.offerlab.community.infra.review.ReviewQueueUpsertRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewQueueUpsertRequestedEventListener {

    private final ReviewQueueService reviewQueueService;

    @EventListener
    public void onReviewQueueUpsertRequested(ReviewQueueUpsertRequestedEvent event) {
        reviewQueueService.upsert(event.command());
    }
}
