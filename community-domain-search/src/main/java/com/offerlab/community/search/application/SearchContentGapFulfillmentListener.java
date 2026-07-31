package com.offerlab.community.search.application;

import com.offerlab.community.post.collaboration.api.CollaborationContributionAcceptedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchContentGapFulfillmentListener {

    private final SearchContentGapService searchContentGapService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContributionAccepted(CollaborationContributionAcceptedEvent event) {
        if (event == null || event.getSourceId() == null || event.getSourceId() <= 0) {
            return;
        }
        String type = event.getContributionType();
        if (!"NEED_FULFILLED".equals(type) && !"NEED_FULFILLED_SYNC".equals(type)) {
            return;
        }
        try {
            searchContentGapService.fulfillFromContentNeed(event.getSourceId(),
                    event.getResolutionType(), event.getResolutionId(), event.getTargetPostId());
        } catch (RuntimeException e) {
            log.warn("search content gap fulfillment event handling failed: needId={}", event.getSourceId(), e);
            throw e;
        }
    }
}
