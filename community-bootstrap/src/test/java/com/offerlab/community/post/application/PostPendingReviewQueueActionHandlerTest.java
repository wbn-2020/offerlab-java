package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostPendingReviewQueueActionHandlerTest {

    private final PostApplicationService postService = mock(PostApplicationService.class);
    private final PostPendingReviewQueueActionHandler handler =
            new PostPendingReviewQueueActionHandler(postService);

    @Test
    void approvedPublishesTheReviewDecision() {
        handler.handle(PostApplicationService.PENDING_REVIEW_SOURCE_TYPE, 11L,
                "approved", "approved", "ok", 21L, "{\"version\":3}");

        verify(postService).resolvePendingPostReview(11L, 21L, true, "ok", 3);
        assertTrue(handler.resolveSourceBeforeQueue());
    }

    @Test
    void rejectedPublishesTheReviewDecision() {
        handler.handle(PostApplicationService.PENDING_REVIEW_SOURCE_TYPE, 12L,
                "rejected", "rejected", "no", 22L, "{\"version\":4}");

        verify(postService).resolvePendingPostReview(12L, 22L, false, "no", 4);
    }

    @Test
    void closedOnlyClosesTheQueueItem() {
        handler.handle(PostApplicationService.PENDING_REVIEW_SOURCE_TYPE, 13L,
                "closed", "closed", "duplicate", 23L);

        verifyNoInteractions(postService);
    }

    @Test
    void approvalWithoutAPreviewVersionFailsClosed() {
        assertThrows(BizException.class, () -> handler.handle(
                PostApplicationService.PENDING_REVIEW_SOURCE_TYPE,
                14L,
                "approved",
                "approved",
                "ok",
                24L,
                null));

        verifyNoInteractions(postService);
    }

    @Test
    void malformedPreviewVersionFailsClosed() {
        assertThrows(BizException.class, () -> handler.handle(
                PostApplicationService.PENDING_REVIEW_SOURCE_TYPE,
                15L,
                "rejected",
                "rejected",
                "no",
                25L,
                "{\"version\":\"stale\"}"));

        verifyNoInteractions(postService);
    }
}
