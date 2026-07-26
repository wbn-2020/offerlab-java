package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.question.api.dto.QuestionAdminUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionTagMapper;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAdminReviewStateTest {

    @Mock
    private InterviewQuestionMapper questionMapper;
    @Mock
    private InterviewQuestionTagMapper questionTagMapper;
    @Mock
    private QuestionSearchIndexer questionSearchIndexer;
    @Mock
    private AfterCommitExecutor afterCommit;
    @Mock
    private ReviewQueuePublisher reviewQueuePublisher;

    private QuestionFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new QuestionFacadeImpl(
                questionMapper,
                questionTagMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                questionSearchIndexer,
                null,
                afterCommit,
                reviewQueuePublisher
        );
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void adminContentEditUsesCasAndReopensPendingReviewWithNewVersion() {
        LocalDateTime expectedVersion = LocalDateTime.parse("2026-07-23T10:00:00");
        LocalDateTime newVersion = LocalDateTime.parse("2026-07-23T10:01:00.123");
        InterviewQuestionPO existing = question(QuestionConstants.QUESTION_APPROVED, expectedVersion);
        InterviewQuestionPO updated = question(QuestionConstants.QUESTION_PENDING, newVersion);
        updated.setAnswerHint("updated answer hint");
        when(questionMapper.selectVisibleByIds(List.of(42L), true))
                .thenReturn(List.of(existing), List.of(updated));
        when(questionMapper.updateAdminIfCurrent(any(InterviewQuestionPO.class), eq(expectedVersion))).thenReturn(1);
        when(questionTagMapper.selectTagsByQuestionIds(List.of(42L))).thenReturn(List.of());
        QuestionAdminUpdateCmd cmd = new QuestionAdminUpdateCmd();
        cmd.setAnswerHint("updated answer hint");
        cmd.setExpectedUpdateTime(expectedVersion);

        QuestionDTO result = facade.updateQuestionAdmin(42L, cmd);

        assertEquals(QuestionConstants.QUESTION_PENDING, result.getStatus());
        ArgumentCaptor<ReviewQueueItemCommand> queueCommand = ArgumentCaptor.forClass(ReviewQueueItemCommand.class);
        verify(reviewQueuePublisher).reopen(queueCommand.capture());
        assertEquals(QuestionFacadeImpl.PENDING_REVIEW_SOURCE_TYPE, queueCommand.getValue().sourceType());
        assertTrue(queueCommand.getValue().extJson().contains(newVersion.toString()));
        verify(afterCommit).execute(any(Runnable.class), eq("question index update:42"));
    }

    @Test
    void staleAdminEditDoesNotReopenQueueOrScheduleIndexing() {
        LocalDateTime expectedVersion = LocalDateTime.parse("2026-07-23T10:00:00");
        when(questionMapper.selectVisibleByIds(List.of(42L), true))
                .thenReturn(List.of(question(QuestionConstants.QUESTION_APPROVED, expectedVersion)));
        when(questionMapper.updateAdminIfCurrent(any(InterviewQuestionPO.class), eq(expectedVersion))).thenReturn(0);
        QuestionAdminUpdateCmd cmd = new QuestionAdminUpdateCmd();
        cmd.setAnswerHint("stale edit");
        cmd.setExpectedUpdateTime(expectedVersion);

        BizException ex = assertThrows(BizException.class, () -> facade.updateQuestionAdmin(42L, cmd));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), ex.getCode());
        verifyNoInteractions(reviewQueuePublisher, afterCommit);
    }

    @Test
    void adminEditCannotSetTerminalReviewStatusDirectly() {
        QuestionAdminUpdateCmd cmd = new QuestionAdminUpdateCmd();
        cmd.setAnswerHint("content edit must not smuggle a review decision");
        cmd.setStatus(QuestionConstants.QUESTION_APPROVED);
        cmd.setExpectedUpdateTime(LocalDateTime.parse("2026-07-23T10:00:00"));

        BizException ex = assertThrows(BizException.class, () -> facade.updateQuestionAdmin(42L, cmd));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        verifyNoInteractions(questionMapper, reviewQueuePublisher, afterCommit);
    }

    @Test
    void directReviewRequiresQueueClosureForTheCurrentOperator() {
        LocalDateTime expectedVersion = LocalDateTime.parse("2026-07-23T10:00:00");
        UserContext.set(7L);
        when(questionMapper.reviewStatusIfPendingAndCurrent(
                42L, QuestionConstants.QUESTION_APPROVED, expectedVersion)).thenReturn(1);

        facade.reviewQuestion(42L, QuestionConstants.QUESTION_APPROVED, expectedVersion);

        verify(reviewQueuePublisher).resolveRequired(
                QuestionFacadeImpl.PENDING_REVIEW_SOURCE_TYPE,
                42L,
                "approved",
                "question approved",
                "question review status=1",
                7L
        );
        verify(afterCommit).execute(any(Runnable.class), eq("question index review:42"));
    }

    private InterviewQuestionPO question(int status, LocalDateTime updateTime) {
        InterviewQuestionPO question = new InterviewQuestionPO();
        question.setId(42L);
        question.setQuestionText("Explain transaction isolation and practical tradeoffs");
        question.setAnswerHint("existing answer");
        question.setSourcePostId(99L);
        question.setSourceAuthorUid(7L);
        question.setStatus(status);
        question.setAppearCount(1);
        question.setQualityScore(80);
        question.setUpdateTime(updateTime);
        return question;
    }
}
