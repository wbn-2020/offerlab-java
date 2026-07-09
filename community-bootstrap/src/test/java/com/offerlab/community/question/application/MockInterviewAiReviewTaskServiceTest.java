package com.offerlab.community.question.application;

import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewAnswerMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MockInterviewAiReviewTaskServiceTest {
    @Mock
    private MockInterviewAnswerMapper answerMapper;
    @Mock
    private MockInterviewAiReviewService aiReviewService;
    @Mock
    private MigrationCheckService migrationCheckService;

    private MockInterviewAiReviewTaskService service;

    @BeforeEach
    void setUp() {
        service = new MockInterviewAiReviewTaskService(answerMapper, aiReviewService, migrationCheckService);
        lenient().when(migrationCheckService.mockInterviewAiReviewReady()).thenReturn(true);
    }

    @Test
    void reviewSessionPersistsSucceededAndFailedAiStates() {
        MockInterviewAnswerPO success = answer(1L);
        MockInterviewAnswerPO failed = answer(2L);
        String longError = "x".repeat(600);
        when(answerMapper.selectClaimablePendingAiReview(42L, 7L, 10)).thenReturn(List.of(success, failed));
        when(answerMapper.claimPendingAiReview(eq(42L), eq(7L), eq(1L), anyString())).thenReturn(1);
        when(answerMapper.claimPendingAiReview(eq(42L), eq(7L), eq(2L), anyString())).thenReturn(1);
        when(aiReviewService.review(success)).thenReturn(new MockInterviewAiReviewService.ReviewResult(
                4, "complete", "project linked", "practice follow-up", "rules",
                true, 12, 8, 99L, "DEEPSEEK_REVIEW_FAILED"));
        when(aiReviewService.review(failed)).thenThrow(new IllegalStateException(longError));

        service.reviewSession(42L, 7L);

        verify(answerMapper).updateAiReview(eq(42L), eq(7L), eq(1L), eq(4),
                eq("complete"), eq("project linked"), eq("practice follow-up"), eq("rules"),
                argThat(taskId -> taskId != null && taskId.startsWith("mock-review-")),
                eq(1),
                longThat(duration -> duration >= 0L),
                eq(12),
                eq(8),
                eq(99L),
                eq("DEEPSEEK_REVIEW_FAILED"));
        verify(answerMapper).updateAiReviewFailed(eq(42L), eq(7L), eq(2L),
                argThat(message -> message.length() == 500),
                argThat(taskId -> taskId != null && taskId.startsWith("mock-review-")),
                longThat(duration -> duration >= 0L),
                eq(0),
                eq(0),
                eq(0L),
                eq("REVIEW_TASK_EXCEPTION"),
                eq("none"));
    }

    @Test
    void emptyReviewResultIsStoredAsFailed() {
        MockInterviewAnswerPO answer = answer(1L);
        when(answerMapper.selectClaimablePendingAiReview(42L, 7L, 10)).thenReturn(List.of(answer));
        when(answerMapper.claimPendingAiReview(eq(42L), eq(7L), eq(1L), anyString())).thenReturn(1);
        when(aiReviewService.review(answer)).thenReturn(null);

        service.reviewSession(42L, 7L);

        verify(answerMapper).updateAiReviewFailed(eq(42L), eq(7L), eq(1L), eq("AI review returned empty result"),
                argThat(taskId -> taskId != null && taskId.startsWith("mock-review-")),
                longThat(duration -> duration >= 0L),
                eq(0),
                eq(0),
                eq(0L),
                eq("EMPTY_REVIEW_RESULT"),
                eq("none"));
        verify(answerMapper, never()).updateAiReview(eq(42L), eq(7L), eq(1L), intThat(value -> value == 0),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq(0L), eq(0), eq(0), eq(0L), eq(null));
    }

    @Test
    void stalePendingAnswerIsSkippedWhenAnotherWorkerAlreadyClaimedIt() {
        MockInterviewAnswerPO answer = answer(1L);
        when(answerMapper.selectClaimablePendingAiReview(42L, 7L, 10)).thenReturn(List.of(answer));
        when(answerMapper.claimPendingAiReview(eq(42L), eq(7L), eq(1L), anyString())).thenReturn(0);

        service.reviewSession(42L, 7L);

        verify(aiReviewService, never()).review(answer);
        verify(answerMapper, never()).updateAiReview(eq(42L), eq(7L), eq(1L), intThat(value -> value == 0),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(0), eq(0L), eq(0), eq(0), eq(0L), eq(null));
        verify(answerMapper, never()).updateAiReviewFailed(eq(42L), eq(7L), eq(1L),
                eq(null), eq(null), eq(0L), eq(0), eq(0), eq(0L), eq(null), eq(null));
    }

    @Test
    void reviewSessionUsesCompatWritesWhenTransparencyColumnsAreMissing() {
        when(migrationCheckService.mockInterviewAiReviewReady()).thenReturn(false);
        MockInterviewAnswerPO success = answer(1L);
        MockInterviewAnswerPO failed = answer(2L);
        when(answerMapper.selectPendingAiReview(42L, 7L, 10)).thenReturn(List.of(success, failed));
        when(aiReviewService.review(success)).thenReturn(new MockInterviewAiReviewService.ReviewResult(
                4, "complete", "project linked", "practice follow-up", "rules",
                true, 12, 8, 99L, "DEEPSEEK_REVIEW_FAILED"));
        when(aiReviewService.review(failed)).thenThrow(new IllegalStateException("provider down"));

        service.reviewSession(42L, 7L);

        verify(answerMapper).updateAiReviewCompat(eq(42L), eq(7L), eq(1L), eq(4),
                eq("complete"), eq("project linked"), eq("practice follow-up"), eq("rules"));
        verify(answerMapper, never()).updateAiReview(eq(42L), eq(7L), eq(1L), intThat(value -> value == 4),
                eq("complete"), eq("project linked"), eq("practice follow-up"), eq("rules"),
                argThat(taskId -> taskId != null && taskId.startsWith("mock-review-")),
                eq(1), longThat(duration -> duration >= 0L), eq(12), eq(8), eq(99L), eq("DEEPSEEK_REVIEW_FAILED"));
        verify(answerMapper).updateAiReviewFailedCompat(eq(42L), eq(7L), eq(2L),
                eq("provider down"), eq("none"));
        verify(answerMapper, never()).updateAiReviewFailed(eq(42L), eq(7L), eq(2L),
                eq("provider down"),
                argThat(taskId -> taskId != null && taskId.startsWith("mock-review-")),
                longThat(duration -> duration >= 0L),
                eq(0),
                eq(0),
                eq(0L),
                eq("REVIEW_TASK_EXCEPTION"),
                eq("none"));
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
