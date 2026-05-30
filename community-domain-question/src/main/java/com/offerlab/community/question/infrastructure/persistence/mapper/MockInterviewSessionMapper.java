package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MockInterviewSessionMapper extends BaseMapper<MockInterviewSessionPO> {
    @Select("""
            SELECT *
            FROM t_mock_interview_session
            WHERE uid = #{uid}
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<MockInterviewSessionPO> selectRecentByUser(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_mock_interview_session
            WHERE uid = #{uid}
              AND status = 'completed'
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<MockInterviewSessionPO> selectRecentCompletedByUser(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS session_count,
                   SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed_count,
                   COALESCE(SUM(CASE WHEN status = 'completed' THEN question_count ELSE 0 END), 0) AS total_question_count,
                   COALESCE(SUM(CASE WHEN status = 'completed' THEN answered_count ELSE 0 END), 0) AS answered_question_count,
                   COALESCE(ROUND(AVG(CASE WHEN status = 'completed' AND question_count > 0 THEN total_score * 100 / (question_count * 5) END)), 0) AS average_score_percent,
                   COALESCE(MAX(CASE WHEN status = 'completed' AND question_count > 0 THEN ROUND(total_score * 100 / (question_count * 5)) ELSE 0 END), 0) AS best_score_percent,
                   COALESCE(ROUND(AVG(CASE WHEN status = 'completed' THEN duration_seconds END)), 0) AS average_duration_seconds
            FROM t_mock_interview_session
            WHERE uid = #{uid}
            """)
    Map<String, Object> selectStatsByUser(@Param("uid") Long uid);

    @Select("""
            SELECT COUNT(*) AS mockSessionCount,
                   SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS mockCompletedCount,
                   COALESCE(SUM(CASE WHEN status = 'completed' THEN answered_count ELSE 0 END), 0) AS mockAnsweredQuestionCount,
                   COALESCE(ROUND(AVG(CASE WHEN status = 'completed' AND question_count > 0 THEN total_score * 100 / (question_count * 5) END)), 0) AS mockAverageScorePercent,
                   COALESCE(MAX(CASE WHEN status = 'completed' AND question_count > 0 THEN ROUND(total_score * 100 / (question_count * 5)) ELSE 0 END), 0) AS mockBestScorePercent
            FROM t_mock_interview_session
            WHERE uid = #{uid}
              AND update_time >= #{since}
            """)
    Map<String, Object> summarizeWeeklyReport(@Param("uid") Long uid,
                                              @Param("since") LocalDateTime since);

    @Select("""
            SELECT *
            FROM t_mock_interview_session
            WHERE id = #{id}
              AND uid = #{uid}
            LIMIT 1
            """)
    MockInterviewSessionPO selectByUser(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_mock_interview_session
            SET answered_count = #{answeredCount},
                total_score = #{totalScore},
                duration_seconds = #{durationSeconds},
                update_time = NOW(3)
            WHERE id = #{id}
              AND uid = #{uid}
              AND status = 'started'
            """)
    int updateDraft(@Param("id") Long id,
                    @Param("uid") Long uid,
                    @Param("answeredCount") int answeredCount,
                    @Param("totalScore") int totalScore,
                    @Param("durationSeconds") int durationSeconds);

    @Update("""
            UPDATE t_mock_interview_session
            SET answered_count = #{answeredCount},
                total_score = #{totalScore},
                duration_seconds = #{durationSeconds},
                status = #{status},
                update_time = NOW(3)
            WHERE id = #{id}
              AND uid = #{uid}
              AND status = 'started'
            """)
    int complete(@Param("id") Long id,
                 @Param("uid") Long uid,
                 @Param("answeredCount") int answeredCount,
                 @Param("totalScore") int totalScore,
                 @Param("durationSeconds") int durationSeconds,
                 @Param("status") String status);
}
