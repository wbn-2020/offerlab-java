package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestionPO> {

    @Select("""
            <script>
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="keyword != null and keyword != ''">
                AND q.question_text LIKE CONCAT('%', #{keyword}, '%')
              </if>
              <if test="company != null and company != ''">
                AND q.company LIKE CONCAT('%', #{company}, '%')
              </if>
              <if test="position != null and position != ''">
                AND q.position LIKE CONCAT('%', #{position}, '%')
              </if>
              <if test="difficulty != null and difficulty != ''">
                AND q.difficulty = #{difficulty}
              </if>
              <if test="round != null and round != ''">
                AND q.interview_round = #{round}
              </if>
              <if test="startTime != null">
                AND q.create_time &gt;= #{startTime}
              </if>
              <if test="endTime != null">
                AND q.create_time &lt;= #{endTime}
              </if>
              <if test="tagIds != null and tagIds.size() > 0">
                AND q.id IN (
                    SELECT question_id FROM t_interview_question_tag
                    WHERE tag_id IN
                    <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">
                      #{tagId}
                    </foreach>
                )
              </if>
            ORDER BY
              <choose>
                <when test="sort == 'appear'">q.appear_count DESC,</when>
                <when test="sort == 'hot'">(q.appear_count * 5 + q.quality_score) DESC,</when>
                <otherwise>q.create_time DESC,</otherwise>
              </choose>
              q.create_time DESC, q.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<InterviewQuestionPO> searchPublic(@Param("keyword") String keyword,
                                           @Param("company") String company,
                                           @Param("position") String position,
                                           @Param("difficulty") String difficulty,
                                           @Param("round") String round,
                                           @Param("tagIds") Collection<Long> tagIds,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("sort") String sort,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT q.*
            FROM t_interview_question q
            WHERE q.source_post_id = #{postId}
              <if test="admin == false">
                AND q.status = 1
              </if>
            ORDER BY q.create_time ASC, q.id ASC
            </script>
            """)
    List<InterviewQuestionPO> selectByPostId(@Param("postId") Long postId, @Param("admin") boolean admin);

    @Select("""
            <script>
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="admin == false">
                AND q.status = 1
              </if>
            </script>
            """)
    List<InterviewQuestionPO> selectVisibleByIds(@Param("ids") Collection<Long> ids, @Param("admin") boolean admin);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.id <> #{questionId}
              AND (
                (#{canonicalId} IS NOT NULL AND q.canonical_id = #{canonicalId})
                OR (#{company} IS NOT NULL AND #{company} <> '' AND q.company = #{company})
                OR (#{position} IS NOT NULL AND #{position} <> '' AND q.position = #{position})
              )
            ORDER BY q.appear_count DESC, q.create_time DESC
            LIMIT #{limit}
            """)
    List<InterviewQuestionPO> selectRelated(@Param("questionId") Long questionId,
                                            @Param("canonicalId") Long canonicalId,
                                            @Param("company") String company,
                                            @Param("position") String position,
                                            @Param("limit") int limit);

    @Update("""
            UPDATE t_interview_question
            SET status = 2, update_time = NOW(3)
            WHERE source_post_id = #{postId}
            """)
    int hideByPostId(@Param("postId") Long postId);

    @Select("""
            SELECT company AS name, COUNT(*) AS count
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company IS NOT NULL
              AND q.company <> ''
            GROUP BY company
            ORDER BY COUNT(*) DESC, company ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> suggestCompanies(@Param("limit") int limit);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
            ORDER BY q.appear_count DESC, q.create_time DESC
            LIMIT #{limit}
            """)
    List<InterviewQuestionPO> selectTopByCompany(@Param("company") String company, @Param("limit") int limit);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            ORDER BY (q.appear_count * 5 + q.quality_score) DESC, q.create_time DESC
            LIMIT #{limit}
            """)
    List<InterviewQuestionPO> selectRecommended(@Param("limit") int limit);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            ORDER BY q.update_time DESC, q.id DESC
            LIMIT #{limit}
            """)
    List<InterviewQuestionPO> selectAllIndexable(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="status != null">
                AND q.status = #{status}
              </if>
            ORDER BY q.update_time DESC, q.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<InterviewQuestionPO> selectAdminRecent(@Param("status") Integer status, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="status != null">
                AND q.status = #{status}
              </if>
            </script>
            """)
    long countAdminRecent(@Param("status") Integer status);

    @Select("""
            SELECT q.status AS name, COUNT(*) AS count
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            GROUP BY q.status
            """)
    List<Map<String, Object>> countAdminByStatus();

    @Select("""
            SELECT q.position AS name, COUNT(*) AS count
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
              AND q.position IS NOT NULL
              AND q.position <> ''
            GROUP BY q.position
            ORDER BY COUNT(*) DESC, q.position ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countPositionsByCompany(@Param("company") String company, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(DISTINCT q.position)
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
              AND q.position IS NOT NULL
              AND q.position <> ''
            """)
    long countDistinctPositionsByCompany(@Param("company") String company);

    @Update("""
            UPDATE t_interview_question
            SET status = #{status}, update_time = NOW(3)
            WHERE id = #{questionId}
            """)
    int updateStatus(@Param("questionId") Long questionId, @Param("status") int status);

    @Update("""
            <script>
            UPDATE t_interview_question
            SET update_time = NOW(3)
              <if test="questionText != null">, question_text = #{questionText}</if>
              <if test="normalizedHash != null">, normalized_hash = #{normalizedHash}</if>
              <if test="answerHint != null">, answer_hint = #{answerHint}</if>
              <if test="company != null">, company = #{company}</if>
              <if test="position != null">, position = #{position}</if>
              <if test="interviewRound != null">, interview_round = #{interviewRound}</if>
              <if test="difficulty != null">, difficulty = #{difficulty}</if>
              <if test="status != null">, status = #{status}</if>
              <if test="qualityScore != null">, quality_score = #{qualityScore}</if>
            WHERE id = #{id}
            </script>
            """)
    int updateAdmin(InterviewQuestionPO question);

    @Select("""
            SELECT DATE(q.create_time) AS name, COUNT(*) AS count
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
              AND q.create_time >= #{since}
            GROUP BY DATE(q.create_time)
            ORDER BY DATE(q.create_time) ASC
            """)
    List<Map<String, Object>> countByCompanySince(@Param("company") String company, @Param("since") LocalDateTime since);
}
