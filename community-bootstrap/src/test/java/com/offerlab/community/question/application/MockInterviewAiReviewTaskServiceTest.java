package com.offerlab.community.question.application;

import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewAnswerMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockInterviewAiReviewTaskServiceTest {
    @Mock
    private MockInterviewAnswerMapper answerMapper;
    @Mock
    private MockInterviewAiReviewService aiReviewService;

    private MockInterviewAiReviewTaskService service;

    @BeforeEach
    void setUp() {
        service = new MockInterviewAiReviewTaskService(answerMapper, aiReviewService);
    }

    @Test
    void reviewSessionPersistsSucceededAndFailedAiStates() {
        MockInterviewAnswerPO success = answer(1L);
        MockInterviewAnswerPO failed = answer(2L);
        String longError = "x".repeat(600);
        when(answerMapper.selectPendingAiReview(42L, 7L)).thenReturn(List.of(success, failed));
        when(aiReviewService.review(success)).thenReturn(new MockInterviewAiReviewService.ReviewResult(
                4, "complete", "project linked", "practice follow-up", "rules"));
        when(aiReviewService.review(failed)).thenThrow(new IllegalStateException(longError));

        service.reviewSession(42L, 7L);

        verify(answerMapper).updateAiReview(42L, 7L, 1L, 4,
                "complete", "project linked", "practice follow-up", "rules");
        verify(answerMapper).updateAiReviewFailed(eq(42L), eq(7L), eq(2L),
                argThat(message -> message.length() == 500));
    }

    @Test
    void emptyReviewResultIsStoredAsFailed() {
        MockInterviewAnswerPO answer = answer(1L);
        when(answerMapper.selectPendingAiReview(42L, 7L)).thenReturn(List.of(answer));
        when(aiReviewService.review(answer)).thenReturn(null);

        service.reviewSession(42L, 7L);

        verify(answerMapper).updateAiReviewFailed(42L, 7L, 1L, "AI review returned empty result");
        verify(answerMapper, never()).updateAiReview(42L, 7L, 1L, 0, null, null, null, null);
    }

    @Test
    void nullSessionInputDoesNotTouchMapper() {
        service.reviewSession(42L, null);

        verify(answerMapper, never()).selectPendingAiReview(42L, null);
    }

    private static MockInterviewAnswerPO answer(Long questionId) {
        MockInterviewAnswerPO answer = new MockInterviewAnswerPO();
        answer.setSessionId(7L);
        answer.setUid(42L);
        answer.setQuestionId(questionId);
        answer.setAnswerText("answer");
        return answer;
    }
}
