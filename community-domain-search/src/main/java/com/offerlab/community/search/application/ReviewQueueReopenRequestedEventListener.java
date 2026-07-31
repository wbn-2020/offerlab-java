package com.offerlab.community.search.application;

import com.offerlab.community.infra.review.ReviewQueueReopenRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewQueueReopenRequestedEventListener {

    private final ReviewQueueService reviewQueueService;

    @EventListener
    public void onReviewQueueReopenRequested(ReviewQueueReopenRequestedEvent event) {
        reviewQueueService.reopen(event.command());
    }
}
