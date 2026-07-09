package com.offerlab.community.question.application;

import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewAnswerMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewAiReviewTaskService {

    private static final int REVIEW_BATCH_SIZE = 10;
    private static final int MAX_REVIEWS_PER_TASK = 20;

    private final MockInterviewAnswerMapper answerMapper;
    private final MockInterviewAiReviewService aiReviewService;
    private final MigrationCheckService migrationCheckService;

    @Async("aiReviewAsyncExecutor")
    public void reviewSession(Long uid, Long sessionId) {
        if (uid == null || sessionId == null) {
            return;
        }
        int reviewed = 0;
        boolean ready = mockInterviewAiReviewReady();
        while (reviewed < MAX_REVIEWS_PER_TASK) {
            int limit = Math.min(REVIEW_BATCH_SIZE, MAX_REVIEWS_PER_TASK - reviewed);
            List<MockInterviewAnswerPO> answers = ready
                    ? answerMapper.selectClaimablePendingAiReview(uid, sessionId, limit)
                    : answerMapper.selectPendingAiReview(uid, sessionId, limit);
            if (answers == null || answers.isEmpty()) {
                break;
            }
            for (MockInterviewAnswerPO answer : answers) {
                if (answer == null || answer.getQuestionId() == null) {
                    continue;
                }
                if (reviewOne(uid, sessionId, answer)) {
                    reviewed++;
                    if (reviewed >= MAX_REVIEWS_PER_TASK) {
                        break;
                    }
                }
            }
            if (answers.size() < limit) {
                break;
            }
        }
        if (reviewed >= MAX_REVIEWS_PER_TASK) {
            log.warn("mock interview AI review task reached per-run limit: uid={} sessionId={} maxReviews={}",
                    uid, sessionId, MAX_REVIEWS_PER_TASK);
        }
    }

    private boolean reviewOne(Long uid, Long sessionId, MockInterviewAnswerPO answer) {
        String taskId = "mock-review-" + UUID.randomUUID();
        if (!claimPendingAiReview(uid, sessionId, answer.getQuestionId(), taskId)) {
            log.info("skip stale mock interview AI review: uid={} sessionId={} questionId={}",
                    uid, sessionId, answer.getQuestionId());
            return false;
        }
        long startedNanos = System.nanoTime();
        try {
            MockInterviewAiReviewService.ReviewResult result = aiReviewService.review(answer);
            long durationMs = durationMs(startedNanos);
            if (result == null) {
                updateAiReviewFailed(uid, sessionId, answer.getQuestionId(),
                        "AI review returned empty result", taskId, durationMs, 0, 0, 0L,
                        "EMPTY_REVIEW_RESULT", "none");
                return true;
            }
            updateAiReview(uid, sessionId, answer.getQuestionId(), result.score(),
                    result.completeness(), result.projectExpression(), result.followUpSuggestion(), result.provider(),
                    taskId, result.fallbackUsed() ? 1 : 0, durationMs, result.promptTokens(),
                    result.completionTokens(), result.estimatedCostMicros(), result.errorCode());
        } catch (Exception e) {
            long durationMs = durationMs(startedNanos);
            log.warn("mock interview AI review failed: uid={} sessionId={} questionId={}",
                    uid, sessionId, answer.getQuestionId(), e);
            updateAiReviewFailed(uid, sessionId, answer.getQuestionId(), shortMessage(e),
                    taskId, durationMs, 0, 0, 0L, "REVIEW_TASK_EXCEPTION", "none");
        }
        return true;
    }

    private long durationMs(long startedNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    private String shortMessage(Throwable e) {
        String message = e == null || e.getMessage() == null ? "AI review failed" : e.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private boolean claimPendingAiReview(Long uid, Long sessionId, Long questionId, String taskId) {
        if (!mockInterviewAiReviewReady()) {
            return true;
        }
        return answerMapper.claimPendingAiReview(uid, sessionId, questionId, taskId) > 0;
    }

    private int updateAiReview(Long uid, Long sessionId, Long questionId, int aiScore,
                               String aiCompleteness, String aiProjectExpression, String aiFollowUpSuggestion,
                               String aiReviewProvider, String aiReviewTaskId, int aiReviewFallbackUsed,
                               long aiReviewDurationMs, int aiReviewPromptTokens, int aiReviewCompletionTokens,
                               long aiReviewEstimatedCostMicros, String aiReviewErrorCode) {
        return mockInterviewAiReviewReady()
                ? answerMapper.updateAiReview(uid, sessionId, questionId, aiScore,
                aiCompleteness, aiProjectExpression, aiFollowUpSuggestion, aiReviewProvider,
                aiReviewTaskId, aiReviewFallbackUsed, aiReviewDurationMs, aiReviewPromptTokens,
                aiReviewCompletionTokens, aiReviewEstimatedCostMicros, aiReviewErrorCode)
                : answerMapper.updateAiReviewCompat(uid, sessionId, questionId, aiScore,
                aiCompleteness, aiProjectExpression, aiFollowUpSuggestion, aiReviewProvider);
    }

    private int updateAiReviewFailed(Long uid, Long sessionId, Long questionId, String aiReviewError,
                                     String aiReviewTaskId, long aiReviewDurationMs,
                                     Integer aiReviewPromptTokens, Integer aiReviewCompletionTokens,
                                     Long aiReviewEstimatedCostMicros, String aiReviewErrorCode,
                                     String aiReviewProvider) {
        return mockInterviewAiReviewReady()
                ? answerMapper.updateAiReviewFailed(uid, sessionId, questionId, aiReviewError,
                aiReviewTaskId, aiReviewDurationMs, aiReviewPromptTokens, aiReviewCompletionTokens,
                aiReviewEstimatedCostMicros, aiReviewErrorCode, aiReviewProvider)
                : answerMapper.updateAiReviewFailedCompat(uid, sessionId, questionId, aiReviewError, aiReviewProvider);
    }

    private boolean mockInterviewAiReviewReady() {
        return migrationCheckService.mockInterviewAiReviewReady();
    }
}
