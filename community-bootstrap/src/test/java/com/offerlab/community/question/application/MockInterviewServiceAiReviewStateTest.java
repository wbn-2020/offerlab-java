package com.offerlab.community.question.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.question.api.dto.MockInterviewAnswerDTO;
import com.offerlab.community.question.api.dto.MockInterviewSessionDTO;
import com.offerlab.community.question.api.dto.MockInterviewSubmitCmd;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionTagMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewAnswerMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewSessionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.UserPrepTargetMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.UserQuestionProgressMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewSessionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockInterviewServiceAiReviewStateTest {
    @Mock
    private MockInterviewSessionMapper sessionMapper;
    @Mock
    private MockInterviewAnswerMapper answerMapper;
    @Mock
    private InterviewQuestionMapper questionMapper;
    @Mock
    private InterviewQuestionTagMapper questionTagMapper;
    @Mock
    private UserQuestionProgressMapper progressMapper;
    @Mock
    private UserPrepTargetMapper prepTargetMapper;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private MockInterviewAiReviewTaskService aiReviewTaskService;

    private MockInterviewService service;

    @BeforeEach
    void setUp() {
        service = new MockInterviewService(
                sessionMapper,
                answerMapper,
                questionMapper,
                questionTagMapper,
                progressMapper,
                prepTargetMapper,
                idGen,
                new AfterCommitExecutor(),
                aiReviewTaskService);
    }

    @Test
    void submitReturnsCompletedSessionEvenWhenAsyncAiReviewDispatchFails() {
        MockInterviewAnswerPO answer = answer(1L, "NOT_REQUESTED");
        when(sessionMapper.selectByUser(7L, 42L)).thenReturn(session("started"), session("completed"));
        when(answerMapper.selectBySession(7L, 42L)).thenReturn(List.of(answer), List.of(answer));
        when(sessionMapper.complete(7L, 42L, 1, 5, 120, "completed")).thenReturn(1);
        when(questionMapper.selectVisibleByIds(any(), eq(false))).thenReturn(List.of());
        when(questionTagMapper.selectTagsByQuestionIds(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("ai down")).when(aiReviewTaskService).reviewSession(42L, 7L);

        MockInterviewSessionDTO dto = service.submit(42L, 7L, submitCmd(true));

        assertEquals("completed", dto.getStatus());
        assertEquals(1, dto.getAnsweredCount());
        assertEquals(5, dto.getTotalScore());
        verify(answerMapper).markPendingForSession(42L, 7L);
        verify(aiReviewTaskService).reviewSession(42L, 7L);
    }

    @Test
    void repeatedSubmitOnCompletedSessionIsIdempotent() {
        MockInterviewAnswerPO answer = answer(1L, "SUCCEEDED");
        when(sessionMapper.selectByUser(7L, 42L)).thenReturn(session("completed"), session("completed"));
        when(answerMapper.selectBySession(7L, 42L)).thenReturn(List.of(answer));
        when(questionMapper.selectVisibleByIds(any(), eq(false))).thenReturn(List.of());
        when(questionTagMapper.selectTagsByQuestionIds(any())).thenReturn(List.of());

        MockInterviewSessionDTO dto = service.submit(42L, 7L, submitCmd(true));

        assertEquals("completed", dto.getStatus());
        verify(answerMapper, never()).updateDraft(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyInt());
        verify(sessionMapper, never()).complete(anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyString());
        verify(answerMapper, never()).markPendingForSession(42L, 7L);
        verifyNoInteractions(aiReviewTaskService);
    }

    @Test
    void retryAiReviewRequiresCompletedSessionAndMarksFailedAnswersPending() {
        MockInterviewAnswerPO answer = answer(1L, "FAILED");
        when(sessionMapper.selectByUser(7L, 42L)).thenReturn(session("completed"), session("completed"));
        when(answerMapper.selectBySession(7L, 42L)).thenReturn(List.of(answer));
        when(questionMapper.selectVisibleByIds(any(), eq(false))).thenReturn(List.of());
        when(questionTagMapper.selectTagsByQuestionIds(any())).thenReturn(List.of());

        MockInterviewSessionDTO dto = service.retryAiReview(42L, 7L);

        assertEquals("completed", dto.getStatus());
        verify(answerMapper).markRetryPendingForSession(42L, 7L);
        verify(aiReviewTaskService).reviewSession(42L, 7L);
    }

    @Test
    void retryAiReviewRejectsStartedSessions() {
        when(sessionMapper.selectByUser(7L, 42L)).thenReturn(session("started"));

        BizException error = assertThrows(BizException.class, () -> service.retryAiReview(42L, 7L));

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), error.getCode());
        verify(answerMapper, never()).markRetryPendingForSession(42L, 7L);
        verifyNoInteractions(aiReviewTaskService);
    }

    @Test
    void getPreservesPendingFailedAndSucceededAiReviewStates() {
        MockInterviewAnswerPO pending = answer(1L, "PENDING");
        MockInterviewAnswerPO failed = answer(2L, "FAILED");
        failed.setAiReviewError("provider timeout");
        MockInterviewAnswerPO succeeded = answer(3L, "SUCCEEDED");
        succeeded.setAiReviewed(1);
        when(sessionMapper.selectByUser(7L, 42L)).thenReturn(session("completed"));
        when(answerMapper.selectBySession(7L, 42L)).thenReturn(List.of(pending, failed, succeeded));
        when(questionMapper.selectVisibleByIds(any(), eq(false))).thenReturn(List.of());
        when(questionTagMapper.selectTagsByQuestionIds(any())).thenReturn(List.of());

        MockInterviewSessionDTO dto = service.get(42L, 7L);
        List<String> statuses = dto.getAnswers().stream()
                .map(MockInterviewAnswerDTO::getAiReviewStatus)
                .toList();

        assertEquals(List.of("PENDING", "FAILED", "SUCCEEDED"), statuses);
        assertEquals("provider timeout", dto.getAnswers().get(1).getAiReviewError());
    }

    private static MockInterviewSubmitCmd submitCmd(boolean aiReviewEnabled) {
        MockInterviewSubmitCmd.AnswerCmd answer = new MockInterviewSubmitCmd.AnswerCmd();
        answer.setQuestionId(1L);
        answer.setAnswerText("answer");
        answer.setSelfReview("review");
        answer.setScore(5);
        MockInterviewSubmitCmd cmd = new MockInterviewSubmitCmd();
        cmd.setDurationSeconds(120);
        cmd.setAiReviewEnabled(aiReviewEnabled);
        cmd.setAnswers(List.of(answer));
        return cmd;
    }

    private static MockInterviewSessionPO session(String status) {
        MockInterviewSessionPO session = new MockInterviewSessionPO();
        session.setId(7L);
        session.setUid(42L);
        session.setQuestionCount(1);
        session.setAnsweredCount("completed".equals(status) ? 1 : 0);
        session.setTotalScore("completed".equals(status) ? 5 : 0);
        session.setDurationSeconds("completed".equals(status) ? 120 : 0);
        session.setStatus(status);
        return session;
    }

    private static MockInterviewAnswerPO answer(Long questionId, String aiReviewStatus) {
        MockInterviewAnswerPO answer = new MockInterviewAnswerPO();
        answer.setId(questionId);
        answer.setSessionId(7L);
        answer.setUid(42L);
        answer.setQuestionId(questionId);
        answer.setSequenceNo(questionId.intValue());
        answer.setQuestionTextSnapshot("question " + questionId);
        answer.setAnswerText("answer");
        answer.setSelfReview("review");
        answer.setScore(5);
        answer.setAiReviewed("SUCCEEDED".equals(aiReviewStatus) ? 1 : 0);
        answer.setAiReviewStatus(aiReviewStatus);
        return answer;
    }
}
