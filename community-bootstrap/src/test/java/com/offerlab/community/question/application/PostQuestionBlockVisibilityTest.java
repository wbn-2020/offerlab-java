package com.offerlab.community.question.application;

import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.question.api.dto.PostQuestionBlockDTO;
import com.offerlab.community.question.infrastructure.persistence.mapper.AiExtractTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.po.AiExtractTaskPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostQuestionBlockVisibilityTest {
    @Mock
    private InterviewQuestionMapper questionMapper;
    @Mock
    private AiExtractTaskMapper taskMapper;
    @Mock
    private PostFacade postFacade;

    private QuestionFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new QuestionFacadeImpl(
                questionMapper,
                null,
                taskMapper,
                null,
                null,
                null,
                null,
                null,
                null,
                postFacade,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void hiddenSourcePostDoesNotExposeQuestionBlock() {
        when(postFacade.getPost(99L, 7L)).thenReturn(null);

        PostQuestionBlockDTO block = facade.getPostQuestionBlock(99L, 7L, false);

        assertEquals("none", block.getTaskStatus());
        assertTrue(block.getQuestions().isEmpty());
        assertFalse(block.getErrorVisible());
        assertFalse(block.getCanRetry());
        verify(postFacade).getPost(99L, 7L);
        verifyNoInteractions(taskMapper, questionMapper);
    }

    @Test
    void succeededExtractionExplainsPendingReviewQuestionsToPublicUser() {
        AiExtractTaskPO task = new AiExtractTaskPO();
        task.setTaskStatus(QuestionConstants.TASK_SUCCEEDED);
        task.setQuestionCount(4);
        when(postFacade.getPost(100L, 7L)).thenReturn(PostDTO.builder().id(100L).build());
        when(taskMapper.findLatest(100L, QuestionConstants.TASK_TYPE_QUESTION_EXTRACT)).thenReturn(task);
        when(questionMapper.selectByPostId(100L, false)).thenReturn(List.of());
        when(questionMapper.countByPostIdAndStatus(100L, QuestionConstants.QUESTION_APPROVED)).thenReturn(0);
        when(questionMapper.countByPostIdAndStatus(100L, QuestionConstants.QUESTION_PENDING)).thenReturn(2);

        PostQuestionBlockDTO block = facade.getPostQuestionBlock(100L, 7L, false);

        assertEquals("succeeded", block.getTaskStatus());
        assertEquals(4, block.getExtractedCount());
        assertEquals(0, block.getVisibleCount());
        assertEquals(2, block.getPendingReviewCount());
        assertTrue(block.getReviewHint().contains("待审核发布"));
        assertTrue(block.getQuestions().isEmpty());
        assertFalse(block.getErrorVisible());
        assertFalse(block.getCanRetry());
    }
}
