package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MockInterviewAnswerMapper extends BaseMapper<MockInterviewAnswerPO> {
    @Select("""
            SELECT *
            FROM t_mock_interview_answer
            WHERE session_id = #{sessionId}
              AND uid = #{uid}
            ORDER BY sequence_no ASC, id ASC
            """)
    List<MockInterviewAnswerPO> selectBySession(@Param("sessionId") Long sessionId, @Param("uid") Long uid);

    @Select("""
            <script>
            SELECT *
            FROM t_mock_interview_answer
            WHERE uid = #{uid}
            <choose>
              <when test="sessionIds != null and sessionIds.size > 0">
                AND session_id IN
                <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
                  #{sessionId}
                </foreach>
              </when>
              <otherwise>
                AND 1 = 0
              </otherwise>
            </choose>
            ORDER BY session_id ASC, sequence_no ASC, id ASC
            </script>
            """)
    List<MockInterviewAnswerPO> selectBySessions(@Param("uid") Long uid, @Param("sessionIds") Collection<Long> sessionIds);

    @Update("""
            UPDATE t_mock_interview_answer
            SET answer_text = #{answerText},
                self_review = #{selfReview},
                score = #{score},
                ai_reviewed = 0,
                ai_review_status = 'NOT_REQUESTED',
                ai_review_error = NULL,
                ai_score = NULL,
                ai_completeness = NULL,
                ai_project_expression = NULL,
                ai_follow_up_suggestion = NULL,
                ai_review_provider = NULL,
                ai_review_task_id = NULL,
                ai_review_fallback_used = 0,
                ai_review_duration_ms = 0,
                ai_review_prompt_tokens = 0,
                ai_review_completion_tokens = 0,
                ai_review_estimated_cost_micros = 0,
                ai_review_error_code = NULL
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND EXISTS (
                  SELECT 1
                  FROM t_mock_interview_session s
                  WHERE s.id = #{sessionId}
                    AND s.uid = #{uid}
                    AND s.status = 'started'
              )
            """)
    int updateDraft(@Param("uid") Long uid,
                    @Param("sessionId") Long sessionId,
                    @Param("questionId") Long questionId,
                    @Param("answerText") String answerText,
                    @Param("selfReview") String selfReview,
                    @Param("score") int score);

    @Update("""
            UPDATE t_mock_interview_answer
            SET answer_text = #{answerText},
                self_review = #{selfReview},
                score = #{score},
                ai_reviewed = 0,
                ai_review_status = 'NOT_REQUESTED',
                ai_review_error = NULL,
                ai_score = NULL,
                ai_completeness = NULL,
                ai_project_expression = NULL,
                ai_follow_up_suggestion = NULL,
                ai_review_provider = NULL
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND EXISTS (
                  SELECT 1
                  FROM t_mock_interview_session s
                  WHERE s.id = #{sessionId}
                    AND s.uid = #{uid}
                    AND s.status = 'started'
              )
            """)
    int updateDraftCompat(@Param("uid") Long uid,
                          @Param("sessionId") Long sessionId,
                          @Param("questionId") Long questionId,
                          @Param("answerText") String answerText,
                          @Param("selfReview") String selfReview,
                          @Param("score") int score);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 0,
                ai_review_status = 'PENDING',
                ai_review_error = NULL,
                ai_review_task_id = NULL,
                ai_review_fallback_used = 0,
                ai_review_duration_ms = 0,
                ai_review_prompt_tokens = 0,
                ai_review_completion_tokens = 0,
                ai_review_estimated_cost_micros = 0,
                ai_review_error_code = NULL
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND TRIM(COALESCE(answer_text, '')) <> ''
            """)
    int markPendingForSession(@Param("uid") Long uid, @Param("sessionId") Long sessionId);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 0,
                ai_review_status = 'PENDING',
                ai_review_error = NULL
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND TRIM(COALESCE(answer_text, '')) <> ''
            """)
    int markPendingForSessionCompat(@Param("uid") Long uid, @Param("sessionId") Long sessionId);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 0,
                ai_review_status = 'PENDING',
                ai_review_error = NULL,
                ai_review_task_id = NULL,
                ai_review_fallback_used = 0,
                ai_review_duration_ms = 0,
                ai_review_prompt_tokens = 0,
                ai_review_completion_tokens = 0,
                ai_review_estimated_cost_micros = 0,
                ai_review_error_code = NULL
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND ai_review_status IN ('FAILED', 'NOT_REQUESTED')
              AND TRIM(COALESCE(answer_text, '')) <> ''
            """)
    int markRetryPendingForSession(@Param("uid") Long uid, @Param("sessionId") Long sessionId);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 0,
                ai_review_status = 'PENDING',
                ai_review_error = NULL
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND ai_review_status IN ('FAILED', 'NOT_REQUESTED')
              AND TRIM(COALESCE(answer_text, '')) <> ''
            """)
    int markRetryPendingForSessionCompat(@Param("uid") Long uid, @Param("sessionId") Long sessionId);

    @Select("""
            SELECT *
            FROM t_mock_interview_answer
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND ai_review_status = 'PENDING'
            ORDER BY sequence_no ASC, id ASC
            LIMIT #{limit}
            """)
    default List<MockInterviewAnswerPO> selectPendingAiReview(Long uid, Long sessionId) {
        return selectPendingAiReview(uid, sessionId, Integer.MAX_VALUE);
    }

    List<MockInterviewAnswerPO> selectPendingAiReview(@Param("uid") Long uid,
                                                      @Param("sessionId") Long sessionId,
                                                      @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_mock_interview_answer
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND ai_review_status = 'PENDING'
              AND (ai_review_task_id IS NULL OR ai_review_task_id = '')
            ORDER BY sequence_no ASC, id ASC
            LIMIT #{limit}
            """)
    List<MockInterviewAnswerPO> selectClaimablePendingAiReview(@Param("uid") Long uid,
                                                               @Param("sessionId") Long sessionId,
                                                               @Param("limit") int limit);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_review_task_id = #{aiReviewTaskId},
                ai_review_error = NULL,
                ai_review_error_code = NULL,
                ai_review_duration_ms = 0
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND ai_review_status = 'PENDING'
              AND (ai_review_task_id IS NULL OR ai_review_task_id = '')
            """)
    int claimPendingAiReview(@Param("uid") Long uid,
                             @Param("sessionId") Long sessionId,
                             @Param("questionId") Long questionId,
                             @Param("aiReviewTaskId") String aiReviewTaskId);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 1,
                ai_review_status = 'SUCCEEDED',
                ai_review_error = NULL,
                ai_score = #{aiScore},
                ai_completeness = #{aiCompleteness},
                ai_project_expression = #{aiProjectExpression},
                ai_follow_up_suggestion = #{aiFollowUpSuggestion},
                ai_review_provider = #{aiReviewProvider},
                ai_review_task_id = #{aiReviewTaskId},
                ai_review_fallback_used = #{aiReviewFallbackUsed},
                ai_review_duration_ms = #{aiReviewDurationMs},
                ai_review_prompt_tokens = #{aiReviewPromptTokens},
                ai_review_completion_tokens = #{aiReviewCompletionTokens},
                ai_review_estimated_cost_micros = #{aiReviewEstimatedCostMicros},
                ai_review_error_code = #{aiReviewErrorCode}
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND ai_review_status = 'PENDING'
              AND ai_review_task_id = #{aiReviewTaskId}
            """)
    int updateAiReview(@Param("uid") Long uid,
                       @Param("sessionId") Long sessionId,
                       @Param("questionId") Long questionId,
                       @Param("aiScore") int aiScore,
                       @Param("aiCompleteness") String aiCompleteness,
                       @Param("aiProjectExpression") String aiProjectExpression,
                       @Param("aiFollowUpSuggestion") String aiFollowUpSuggestion,
                       @Param("aiReviewProvider") String aiReviewProvider,
                       @Param("aiReviewTaskId") String aiReviewTaskId,
                       @Param("aiReviewFallbackUsed") int aiReviewFallbackUsed,
                       @Param("aiReviewDurationMs") long aiReviewDurationMs,
                       @Param("aiReviewPromptTokens") int aiReviewPromptTokens,
                       @Param("aiReviewCompletionTokens") int aiReviewCompletionTokens,
                       @Param("aiReviewEstimatedCostMicros") long aiReviewEstimatedCostMicros,
                       @Param("aiReviewErrorCode") String aiReviewErrorCode);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 1,
                ai_review_status = 'SUCCEEDED',
                ai_review_error = NULL,
                ai_score = #{aiScore},
                ai_completeness = #{aiCompleteness},
                ai_project_expression = #{aiProjectExpression},
                ai_follow_up_suggestion = #{aiFollowUpSuggestion},
                ai_review_provider = #{aiReviewProvider}
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND ai_review_status = 'PENDING'
            """)
    int updateAiReviewCompat(@Param("uid") Long uid,
                             @Param("sessionId") Long sessionId,
                             @Param("questionId") Long questionId,
                             @Param("aiScore") int aiScore,
                             @Param("aiCompleteness") String aiCompleteness,
                             @Param("aiProjectExpression") String aiProjectExpression,
                             @Param("aiFollowUpSuggestion") String aiFollowUpSuggestion,
                             @Param("aiReviewProvider") String aiReviewProvider);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 0,
                ai_review_status = 'FAILED',
                ai_review_error = #{aiReviewError},
                ai_review_task_id = #{aiReviewTaskId},
                ai_review_fallback_used = 0,
                ai_review_duration_ms = #{aiReviewDurationMs},
                ai_review_prompt_tokens = #{aiReviewPromptTokens},
                ai_review_completion_tokens = #{aiReviewCompletionTokens},
                ai_review_estimated_cost_micros = #{aiReviewEstimatedCostMicros},
                ai_review_error_code = #{aiReviewErrorCode},
                ai_review_provider = #{aiReviewProvider}
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND ai_review_status = 'PENDING'
              AND ai_review_task_id = #{aiReviewTaskId}
            """)
    int updateAiReviewFailed(@Param("uid") Long uid,
                             @Param("sessionId") Long sessionId,
                             @Param("questionId") Long questionId,
                             @Param("aiReviewError") String aiReviewError,
                             @Param("aiReviewTaskId") String aiReviewTaskId,
                             @Param("aiReviewDurationMs") long aiReviewDurationMs,
                             @Param("aiReviewPromptTokens") Integer aiReviewPromptTokens,
                             @Param("aiReviewCompletionTokens") Integer aiReviewCompletionTokens,
                             @Param("aiReviewEstimatedCostMicros") Long aiReviewEstimatedCostMicros,
                             @Param("aiReviewErrorCode") String aiReviewErrorCode,
                             @Param("aiReviewProvider") String aiReviewProvider);

    @Update("""
            UPDATE t_mock_interview_answer
            SET ai_reviewed = 0,
                ai_review_status = 'FAILED',
                ai_review_error = #{aiReviewError},
                ai_review_provider = #{aiReviewProvider}
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
              AND ai_review_status = 'PENDING'
            """)
    int updateAiReviewFailedCompat(@Param("uid") Long uid,
                                   @Param("sessionId") Long sessionId,
                                   @Param("questionId") Long questionId,
                                   @Param("aiReviewError") String aiReviewError,
                                   @Param("aiReviewProvider") String aiReviewProvider);

}
