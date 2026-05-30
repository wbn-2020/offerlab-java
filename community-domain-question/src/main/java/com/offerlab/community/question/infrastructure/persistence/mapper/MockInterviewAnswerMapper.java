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
                score = #{score}
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
            SET ai_reviewed = 1,
                ai_score = #{aiScore},
                ai_completeness = #{aiCompleteness},
                ai_project_expression = #{aiProjectExpression},
                ai_follow_up_suggestion = #{aiFollowUpSuggestion},
                ai_review_provider = #{aiReviewProvider}
            WHERE uid = #{uid}
              AND session_id = #{sessionId}
              AND question_id = #{questionId}
            """)
    int updateAiReview(@Param("uid") Long uid,
                       @Param("sessionId") Long sessionId,
                       @Param("questionId") Long questionId,
                       @Param("aiScore") int aiScore,
                       @Param("aiCompleteness") String aiCompleteness,
                       @Param("aiProjectExpression") String aiProjectExpression,
                       @Param("aiFollowUpSuggestion") String aiFollowUpSuggestion,
                       @Param("aiReviewProvider") String aiReviewProvider);

}
