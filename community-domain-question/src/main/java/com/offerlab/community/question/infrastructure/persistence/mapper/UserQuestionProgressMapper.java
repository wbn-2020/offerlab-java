package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.UserQuestionProgressPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface UserQuestionProgressMapper extends BaseMapper<UserQuestionProgressPO> {
    @Select("""
            <script>
            SELECT *
            FROM t_user_question_progress
            WHERE uid = #{uid}
              AND question_id IN
              <foreach collection="questionIds" item="questionId" open="(" separator="," close=")">
                #{questionId}
              </foreach>
            </script>
            """)
    List<UserQuestionProgressPO> selectByUserAndQuestions(@Param("uid") Long uid,
                                                          @Param("questionIds") Collection<Long> questionIds);

    @Select("""
            SELECT *
            FROM t_user_question_progress
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            LIMIT 1
            """)
    UserQuestionProgressPO selectOne(@Param("uid") Long uid, @Param("questionId") Long questionId);

    @Insert("""
            INSERT INTO t_user_question_progress
                (id, uid, question_id, progress_status, favorite, note, mistake_reason, answer_draft, star_story,
                 next_review_at, last_reviewed_at, review_count, review_interval_days)
            VALUES
                (#{id}, #{uid}, #{questionId}, #{status}, #{favorite}, #{note}, #{mistakeReason}, #{answerDraft}, #{starStory},
                 #{nextReviewAt}, #{lastReviewedAt}, #{reviewCount}, #{reviewIntervalDays})
            ON DUPLICATE KEY UPDATE
                progress_status = COALESCE(VALUES(progress_status), progress_status),
                favorite = VALUES(favorite),
                note = COALESCE(VALUES(note), note),
                mistake_reason = COALESCE(VALUES(mistake_reason), mistake_reason),
                answer_draft = COALESCE(VALUES(answer_draft), answer_draft),
                star_story = COALESCE(VALUES(star_story), star_story),
                next_review_at = COALESCE(VALUES(next_review_at), next_review_at),
                last_reviewed_at = COALESCE(VALUES(last_reviewed_at), last_reviewed_at),
                review_count = GREATEST(review_count, VALUES(review_count)),
                review_interval_days = COALESCE(VALUES(review_interval_days), review_interval_days),
                update_time = NOW(3)
            """)
    int upsert(@Param("id") Long id,
               @Param("uid") Long uid,
               @Param("questionId") Long questionId,
               @Param("status") String status,
               @Param("favorite") Integer favorite,
               @Param("note") String note,
               @Param("mistakeReason") String mistakeReason,
               @Param("answerDraft") String answerDraft,
               @Param("starStory") String starStory,
               @Param("nextReviewAt") LocalDateTime nextReviewAt,
               @Param("lastReviewedAt") LocalDateTime lastReviewedAt,
               @Param("reviewCount") Integer reviewCount,
               @Param("reviewIntervalDays") Integer reviewIntervalDays);

    @Update("""
            UPDATE t_user_question_progress
            SET favorite = #{favorite}, update_time = NOW(3)
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            """)
    int updateFavorite(@Param("uid") Long uid, @Param("questionId") Long questionId, @Param("favorite") Integer favorite);

    @Update("""
            UPDATE t_user_question_progress
            SET progress_status = #{status}, update_time = NOW(3)
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            """)
    int updateStatus(@Param("uid") Long uid, @Param("questionId") Long questionId, @Param("status") String status);

    @Update("""
            UPDATE t_user_question_progress
            SET progress_status = #{status},
                next_review_at = #{nextReviewAt},
                last_reviewed_at = COALESCE(#{lastReviewedAt}, last_reviewed_at),
                review_count = review_count + #{reviewCountDelta},
                review_interval_days = #{reviewIntervalDays},
                update_time = NOW(3)
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            """)
    int updateStatusSchedule(@Param("uid") Long uid,
                             @Param("questionId") Long questionId,
                             @Param("status") String status,
                             @Param("nextReviewAt") LocalDateTime nextReviewAt,
                             @Param("lastReviewedAt") LocalDateTime lastReviewedAt,
                             @Param("reviewCountDelta") int reviewCountDelta,
                             @Param("reviewIntervalDays") int reviewIntervalDays);

    @Update("""
            UPDATE t_user_question_progress
            SET note = #{note}, update_time = NOW(3)
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            """)
    int updateNote(@Param("uid") Long uid, @Param("questionId") Long questionId, @Param("note") String note);

    @Update("""
            UPDATE t_user_question_progress
            SET mistake_reason = #{mistakeReason}, update_time = NOW(3)
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            """)
    int updateMistakeReason(@Param("uid") Long uid,
                            @Param("questionId") Long questionId,
                            @Param("mistakeReason") String mistakeReason);

    @Update("""
            UPDATE t_user_question_progress
            SET answer_draft = #{answerDraft}, star_story = #{starStory}, update_time = NOW(3)
            WHERE uid = #{uid}
              AND question_id = #{questionId}
            """)
    int updateAnswerDraft(@Param("uid") Long uid,
                          @Param("questionId") Long questionId,
                          @Param("answerDraft") String answerDraft,
                          @Param("starStory") String starStory);

    @Select("""
            SELECT *
            FROM t_user_question_progress
            WHERE uid = #{uid}
            ORDER BY update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentByUser(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT
              SUM(CASE WHEN up.favorite = 1 THEN 1 ELSE 0 END) AS favoriteCount,
              SUM(CASE WHEN up.progress_status = 'todo' THEN 1 ELSE 0 END) AS todoCount,
              SUM(CASE WHEN up.progress_status = 'learning' THEN 1 ELSE 0 END) AS learningCount,
              SUM(CASE WHEN up.progress_status = 'mastered' THEN 1 ELSE 0 END) AS masteredCount,
              SUM(CASE WHEN up.progress_status = 'review' THEN 1 ELSE 0 END) AS reviewCount,
              SUM(CASE WHEN up.note IS NOT NULL AND up.note <> '' THEN 1 ELSE 0 END) AS noteCount,
              SUM(CASE WHEN (up.answer_draft IS NOT NULL AND up.answer_draft <> '')
                         OR (up.star_story IS NOT NULL AND up.star_story <> '') THEN 1 ELSE 0 END) AS answerDraftCount
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            """)
    java.util.Map<String, Object> countOverview(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT
              SUM(CASE WHEN up.favorite = 1 THEN 1 ELSE 0 END) AS favoriteCount,
              SUM(CASE WHEN up.progress_status = 'learning' THEN 1 ELSE 0 END) AS learningCount,
              SUM(CASE WHEN up.progress_status = 'mastered' THEN 1 ELSE 0 END) AS masteredCount,
              SUM(CASE WHEN up.progress_status = 'review' THEN 1 ELSE 0 END) AS reviewCount
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            <if test="tagName != null and tagName != ''">
              JOIN t_interview_question_tag qt ON qt.question_id = q.id
              JOIN t_tag t ON t.id = qt.tag_id
            </if>
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="company != null and company != ''">
                AND q.company LIKE CONCAT('%', #{company}, '%')
              </if>
              <if test="position != null and position != ''">
                AND q.position LIKE CONCAT('%', #{position}, '%')
              </if>
              <if test="tagName != null and tagName != ''">
                AND t.tag_name = #{tagName}
              </if>
            </script>
            """)
    java.util.Map<String, Object> countByTarget(@Param("uid") Long uid,
                                                @Param("company") String company,
                                                @Param("position") String position,
                                                @Param("tagName") String tagName);

    @Select("""
            SELECT
              SUM(CASE WHEN up.favorite = 1 THEN 1 ELSE 0 END) AS favoriteCount,
              SUM(CASE WHEN up.progress_status = 'learning' THEN 1 ELSE 0 END) AS learningCount,
              SUM(CASE WHEN up.progress_status = 'mastered' THEN 1 ELSE 0 END) AS masteredCount,
              SUM(CASE WHEN up.progress_status = 'review' THEN 1 ELSE 0 END) AS reviewCount
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
            """)
    java.util.Map<String, Object> countByCompany(@Param("uid") Long uid,
                                                 @Param("company") String company);

    @Select("""
            SELECT up.mistake_reason AS name, COUNT(*) AS count
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND up.mistake_reason IS NOT NULL
              AND up.mistake_reason <> ''
            GROUP BY up.mistake_reason
            ORDER BY COUNT(*) DESC, up.mistake_reason ASC
            """)
    List<java.util.Map<String, Object>> countMistakeReasons(@Param("uid") Long uid);

    @Select("""
            SELECT t.tag_name AS name, COUNT(DISTINCT up.question_id) AS count
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            JOIN t_interview_question_tag qt ON qt.question_id = q.id
            JOIN t_tag t ON t.id = qt.tag_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                (up.next_review_at IS NOT NULL AND up.next_review_at <= NOW(3))
                OR (up.next_review_at IS NULL AND up.progress_status = 'review')
                OR (up.next_review_at IS NULL AND up.progress_status = 'learning' AND up.update_time < #{learningBefore})
                OR (up.next_review_at IS NULL AND up.mistake_reason IS NOT NULL AND up.mistake_reason <> '')
              )
            GROUP BY t.id, t.tag_name
            ORDER BY COUNT(DISTINCT up.question_id) DESC, t.tag_name ASC
            LIMIT #{limit}
            """)
    List<java.util.Map<String, Object>> countWeaknessTags(@Param("uid") Long uid,
                                                          @Param("learningBefore") LocalDateTime learningBefore,
                                                          @Param("limit") int limit);

    @Select("""
            SELECT up.*
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND up.favorite = 1
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            ORDER BY up.update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentFavorites(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT up.*
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND up.progress_status = #{status}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            ORDER BY up.update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentByStatus(@Param("uid") Long uid,
                                                      @Param("status") String status,
                                                      @Param("limit") int limit);

    @Select("""
            SELECT up.*
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                (up.answer_draft IS NOT NULL AND up.answer_draft <> '')
                OR (up.star_story IS NOT NULL AND up.star_story <> '')
              )
            ORDER BY up.update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentAnswerDrafts(@Param("uid") Long uid,
                                                          @Param("limit") int limit);

    @Select("""
            SELECT up.*
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                up.progress_status = 'review'
                OR (up.progress_status = 'learning' AND up.update_time < #{learningBefore})
                OR up.mistake_reason IS NOT NULL
              )
            ORDER BY
              CASE
                WHEN up.next_review_at IS NOT NULL AND up.next_review_at <= NOW(3) THEN 0
                WHEN up.progress_status = 'review' THEN 1
                WHEN up.mistake_reason IS NOT NULL THEN 2
                ELSE 3
              END,
              up.next_review_at ASC,
              up.update_time ASC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectReviewCandidates(@Param("uid") Long uid,
                                                        @Param("learningBefore") LocalDateTime learningBefore,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (
                (up.next_review_at IS NOT NULL AND up.next_review_at <= NOW(3))
                OR (up.next_review_at IS NULL AND up.progress_status = 'review')
                OR (up.next_review_at IS NULL AND up.progress_status = 'learning' AND up.update_time < #{learningBefore})
                OR (up.next_review_at IS NULL AND up.mistake_reason IS NOT NULL)
              )
            """)
    int countReviewCandidates(@Param("uid") Long uid,
                              @Param("learningBefore") LocalDateTime learningBefore);

    @Select("""
            SELECT up.*
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND up.update_time >= #{since}
            ORDER BY up.update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectUpdatedSince(@Param("uid") Long uid,
                                                    @Param("since") LocalDateTime since,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND up.update_time >= #{since}
            """)
    int countUpdatedSince(@Param("uid") Long uid,
                          @Param("since") LocalDateTime since);

    @Select("""
            SELECT
              COUNT(*) AS touchedQuestionCount,
              SUM(CASE WHEN up.progress_status = 'mastered' THEN 1 ELSE 0 END) AS masteredQuestionCount,
              SUM(CASE WHEN up.progress_status = 'review' THEN 1 ELSE 0 END) AS reviewQuestionCount,
              SUM(CASE WHEN up.note IS NOT NULL AND up.note <> '' THEN 1 ELSE 0 END) AS noteCount,
              SUM(CASE WHEN (up.answer_draft IS NOT NULL AND up.answer_draft <> '')
                         OR (up.star_story IS NOT NULL AND up.star_story <> '') THEN 1 ELSE 0 END) AS answerDraftCount
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE up.uid = #{uid}
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND up.update_time >= #{since}
            """)
    java.util.Map<String, Object> summarizeWeeklyReport(@Param("uid") Long uid,
                                                        @Param("since") LocalDateTime since);
}
