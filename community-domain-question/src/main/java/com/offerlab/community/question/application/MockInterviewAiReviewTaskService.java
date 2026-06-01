package com.offerlab.community.question.application;

import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewAnswerMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockInterviewAiReviewTaskService {

    private final MockInterviewAnswerMapper answerMapper;
    private final MockInterviewAiReviewService aiReviewService;

    @Async
    public void reviewSession(Long uid, Long sessionId) {
        if (uid == null || sessionId == null) {
            return;
        }
        List<MockInterviewAnswerPO> answers = answerMapper.selectPendingAiReview(uid, sessionId);
        for (MockInterviewAnswerPO answer : answers) {
            reviewOne(uid, sessionId, answer);
        }
    }

    private void reviewOne(Long uid, Long sessionId, MockInterviewAnswerPO answer) {
        try {
            MockInterviewAiReviewService.ReviewResult result = aiReviewService.review(answer);
            if (result == null) {
                answerMapper.updateAiReviewFailed(uid, sessionId, answer.getQuestionId(), "AI review returned empty result");
                return;
            }
            answerMapper.updateAiReview(uid, sessionId, answer.getQuestionId(), result.score(),
                    result.completeness(), result.projectExpression(), result.followUpSuggestion(), result.provider());
        } catch (Exception e) {
            log.warn("mock interview AI review failed: uid={} sessionId={} questionId={}",
                    uid, sessionId, answer.getQuestionId(), e);
            answerMapper.updateAiReviewFailed(uid, sessionId, answer.getQuestionId(), shortMessage(e));
        }
    }

    private String shortMessage(Throwable e) {
        String message = e == null || e.getMessage() == null ? "AI review failed" : e.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
