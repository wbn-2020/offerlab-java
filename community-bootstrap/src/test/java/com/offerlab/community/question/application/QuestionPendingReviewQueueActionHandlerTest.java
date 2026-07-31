package com.offerlab.community.question.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class QuestionPendingReviewQueueActionHandlerTest {

    private final QuestionFacadeImpl questionFacade = mock(QuestionFacadeImpl.class);
    private final QuestionPendingReviewQueueActionHandler handler =
            new QuestionPendingReviewQueueActionHandler(questionFacade);

    @Test
    void approvedDecisionUsesQueuedQuestionVersion() {
        LocalDateTime expectedUpdateTime = LocalDateTime.parse("2026-07-23T10:15:30.123");

        handler.handle(QuestionFacadeImpl.PENDING_REVIEW_SOURCE_TYPE, 11L,
                "approved", "approved", "ok", 21L,
                "{\"expectedUpdateTime\":\"2026-07-23T10:15:30.123\"}");

        assertTrue(handler.resolveSourceBeforeQueue());
        verify(questionFacade).resolvePendingQuestionReview(11L, true, expectedUpdateTime);
    }

    @Test
    void rejectedDecisionUsesQueuedQuestionVersion() {
        LocalDateTime expectedUpdateTime = LocalDateTime.parse("2026-07-23T10:16:30");

        handler.handle(QuestionFacadeImpl.PENDING_REVIEW_SOURCE_TYPE, 12L,
                "rejected", "rejected", "no", 22L,
                "{\"expectedUpdateTime\":\"2026-07-23T10:16:30\"}");

        verify(questionFacade).resolvePendingQuestionReview(12L, false, expectedUpdateTime);
    }

    @Test
    void missingVersionFailsWithoutTouchingQuestionState() {
        BizException ex = assertThrows(
                BizException.class,
                () -> handler.handle(QuestionFacadeImpl.PENDING_REVIEW_SOURCE_TYPE, 13L,
                        "approved", "approved", "ok", 23L, "{\"postId\":99}")
        );

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), ex.getCode());
        verifyNoInteractions(questionFacade);
    }
}
