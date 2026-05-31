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
                AND (
                    q.question_text LIKE CONCAT('%', #{keyword}, '%')
                    OR q.answer_hint LIKE CONCAT('%', #{keyword}, '%')
                    OR q.exam_point LIKE CONCAT('%', #{keyword}, '%')
                    OR q.reference_answer LIKE CONCAT('%', #{keyword}, '%')
                    OR q.source_snippet LIKE CONCAT('%', #{keyword}, '%')
                )
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
              <if test='usePersonalFilter'>
                AND EXISTS (
                    SELECT 1
                    FROM t_user_question_progress up
                    WHERE up.uid = #{viewerUid}
                      AND up.question_id = q.id
                      <if test="mistakeReason != null and mistakeReason != ''">
                        <choose>
                          <when test="mistakeReason == 'any'">
                            AND up.mistake_reason IS NOT NULL
                            AND up.mistake_reason &lt;&gt; ''
                          </when>
                          <otherwise>
                            AND up.mistake_reason = #{mistakeReason}
                          </otherwise>
                        </choose>
                      </if>
                      <if test="progressStatus != null and progressStatus != ''">
                        AND up.progress_status = #{progressStatus}
                      </if>
                      <if test='hasNote'>
                        AND up.note IS NOT NULL
                        AND up.note != ''
                      </if>
                      <if test='hasAnswerDraft'>
                        AND ((up.answer_draft IS NOT NULL AND up.answer_draft != '') OR (up.star_story IS NOT NULL AND up.star_story != ''))
                      </if>
                      <if test='hasStarStory'>
                        AND up.star_story IS NOT NULL
                        AND up.star_story != ''
                      </if>
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
                                            @Param("viewerUid") Long viewerUid,
                                            @Param("mistakeReason") String mistakeReason,
                                            @Param("progressStatus") String progressStatus,
                                            @Param("usePersonalFilter") boolean usePersonalFilter,
                                            @Param("hasNote") boolean hasNote,
                                             @Param("hasAnswerDraft") boolean hasAnswerDraft,
                                             @Param("hasStarStory") boolean hasStarStory,
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
            SELECT q.id
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.normalized_hash = #{normalizedHash}
            ORDER BY (q.canonical_id IS NULL) DESC, q.appear_count DESC, q.create_time ASC, q.id ASC
            LIMIT 1
            """)
    Long selectCanonicalIdByHash(@Param("normalizedHash") String normalizedHash);

    @Select("""
            SELECT COUNT(DISTINCT q.source_post_id)
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.normalized_hash = #{normalizedHash}
            """)
    int countVisibleSourcesByHash(@Param("normalizedHash") String normalizedHash);

    @Update("""
            UPDATE t_interview_question
            SET canonical_id = CASE WHEN id = #{canonicalId} THEN NULL ELSE #{canonicalId} END,
                appear_count = #{appearCount},
                update_time = NOW(3)
            WHERE normalized_hash = #{normalizedHash}
              AND status = 1
            """)
    int updateCanonicalGroup(@Param("normalizedHash") String normalizedHash,
                             @Param("canonicalId") Long canonicalId,
                             @Param("appearCount") int appearCount);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.normalized_hash = #{normalizedHash}
            ORDER BY q.appear_count DESC, q.create_time ASC, q.id ASC
            """)
    List<InterviewQuestionPO> selectVisibleByHash(@Param("normalizedHash") String normalizedHash);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND (q.normalized_hash = #{normalizedHash}
                   OR (#{canonicalId} IS NOT NULL AND (q.canonical_id = #{canonicalId} OR q.id = #{canonicalId})))
            ORDER BY (q.id = #{canonicalId}) DESC, q.status ASC, q.appear_count DESC, q.create_time ASC, q.id ASC
            """)
    List<InterviewQuestionPO> selectAdminByHash(@Param("normalizedHash") String normalizedHash,
                                                @Param("canonicalId") Long canonicalId);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.id <> #{questionId}
              AND q.status IN (0, 1)
              AND q.normalized_hash <> #{normalizedHash}
              AND (
                (#{company} IS NOT NULL AND #{company} <> '' AND q.company = #{company})
                OR (#{position} IS NOT NULL AND #{position} <> '' AND q.position = #{position})
                OR q.appear_count >= 2
              )
            ORDER BY (q.company = #{company}) DESC,
                     (q.position = #{position}) DESC,
                     q.appear_count DESC,
                     q.quality_score DESC,
                     q.create_time DESC
            LIMIT #{limit}
            """)
    List<InterviewQuestionPO> selectSemanticDuplicateCandidates(@Param("questionId") Long questionId,
                                                                @Param("normalizedHash") String normalizedHash,
                                                                @Param("company") String company,
                                                                @Param("position") String position,
                                                                @Param("limit") int limit);

    @Update("""
            UPDATE t_interview_question
            SET canonical_id = #{canonicalId},
                update_time = NOW(3)
            WHERE id = #{questionId}
            """)
    int updateCanonicalId(@Param("questionId") Long questionId, @Param("canonicalId") Long canonicalId);

    @Update("""
            UPDATE t_interview_question
            SET canonical_id = CASE WHEN id = #{canonicalId} THEN NULL ELSE #{canonicalId} END,
                appear_count = #{appearCount},
                update_time = NOW(3)
            WHERE normalized_hash = #{normalizedHash}
            """)
    int updateAdminCanonicalGroup(@Param("normalizedHash") String normalizedHash,
                                  @Param("canonicalId") Long canonicalId,
                                  @Param("appearCount") int appearCount);

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
                (#{canonicalId} IS NOT NULL AND (q.canonical_id = #{canonicalId} OR q.id = #{canonicalId}))
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
    List<Map<String, Object>> countCompaniesForAliasCandidates(@Param("limit") int limit);

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
            SELECT COUNT(*) AS sampleCount,
                   MAX(q.update_time) AS updatedAt
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
            """)
    Map<String, Object> summarizeCompanyQuestions(@Param("company") String company);

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
            <script>
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              <if test="company != null and company != ''">
                AND q.company LIKE CONCAT('%', #{company}, '%')
              </if>
              <if test="position != null and position != ''">
                AND q.position LIKE CONCAT('%', #{position}, '%')
              </if>
              <if test="difficulty != null and difficulty != ''">
                AND q.difficulty = #{difficulty}
              </if>
            ORDER BY (q.appear_count * 5 + q.quality_score) DESC, q.create_time DESC, q.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<InterviewQuestionPO> selectMockInterviewQuestions(@Param("company") String company,
                                                           @Param("position") String position,
                                                           @Param("difficulty") String difficulty,
                                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            JOIN t_interview_question_tag qt ON qt.question_id = q.id
            JOIN t_tag t ON t.id = qt.tag_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND t.tag_name = #{tagName}
              <if test="difficulty != null and difficulty != ''">
                AND q.difficulty = #{difficulty}
              </if>
            ORDER BY (q.appear_count * 5 + q.quality_score) DESC, q.create_time DESC, q.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<InterviewQuestionPO> selectMockInterviewQuestionsByTag(@Param("tagName") String tagName,
                                                                @Param("difficulty") String difficulty,
                                                                @Param("limit") int limit);

    @Select("""
            SELECT q.*
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            JOIN t_interview_question_tag qt ON qt.question_id = q.id
            JOIN t_tag t ON t.id = qt.tag_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND t.tag_name = #{tagName}
            ORDER BY (q.appear_count * 5 + q.quality_score) DESC, q.create_time DESC
            LIMIT #{limit}
            """)
    List<InterviewQuestionPO> selectTopByTagName(@Param("tagName") String tagName, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT q.id)
            FROM t_interview_question q
            JOIN t_post_main p ON p.id = q.source_post_id
            <if test="tagName != null and tagName != ''">
              JOIN t_interview_question_tag qt ON qt.question_id = q.id
              JOIN t_tag t ON t.id = qt.tag_id
            </if>
            WHERE q.status = 1
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
    int countPublicByTarget(@Param("company") String company,
                            @Param("position") String position,
                            @Param("tagName") String tagName);

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
              <if test="keyword != null and keyword != ''">
                AND (
                    q.question_text LIKE CONCAT('%', #{keyword}, '%')
                    OR q.answer_hint LIKE CONCAT('%', #{keyword}, '%')
                    OR q.exam_point LIKE CONCAT('%', #{keyword}, '%')
                    OR q.reference_answer LIKE CONCAT('%', #{keyword}, '%')
                    OR q.source_snippet LIKE CONCAT('%', #{keyword}, '%')
                    OR q.quality_reason LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test="company != null and company != ''">
                AND q.company LIKE CONCAT('%', #{company}, '%')
              </if>
              <if test="position != null and position != ''">
                AND q.position LIKE CONCAT('%', #{position}, '%')
              </if>
              <if test="minQualityScore != null">
                AND q.quality_score &gt;= #{minQualityScore}
              </if>
              <if test="maxQualityScore != null">
                AND q.quality_score &lt;= #{maxQualityScore}
              </if>
              <if test="sourcePostId != null">
                AND q.source_post_id = #{sourcePostId}
              </if>
              <if test="taskStatus != null">
                AND q.source_post_id IN (
                    SELECT t.post_id
                    FROM t_ai_extract_task t
                    WHERE t.task_type = 'question_extract'
                      AND t.task_status = #{taskStatus}
                )
              </if>
            ORDER BY q.update_time DESC, q.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<InterviewQuestionPO> selectAdminRecent(@Param("status") Integer status,
                                                @Param("keyword") String keyword,
                                                @Param("company") String company,
                                                @Param("position") String position,
                                                @Param("minQualityScore") Integer minQualityScore,
                                                @Param("maxQualityScore") Integer maxQualityScore,
                                                @Param("sourcePostId") Long sourcePostId,
                                                @Param("taskStatus") Integer taskStatus,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

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
              <if test="keyword != null and keyword != ''">
                AND (
                    q.question_text LIKE CONCAT('%', #{keyword}, '%')
                    OR q.answer_hint LIKE CONCAT('%', #{keyword}, '%')
                    OR q.exam_point LIKE CONCAT('%', #{keyword}, '%')
                    OR q.reference_answer LIKE CONCAT('%', #{keyword}, '%')
                    OR q.source_snippet LIKE CONCAT('%', #{keyword}, '%')
                    OR q.quality_reason LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
              <if test="company != null and company != ''">
                AND q.company LIKE CONCAT('%', #{company}, '%')
              </if>
              <if test="position != null and position != ''">
                AND q.position LIKE CONCAT('%', #{position}, '%')
              </if>
              <if test="minQualityScore != null">
                AND q.quality_score &gt;= #{minQualityScore}
              </if>
              <if test="maxQualityScore != null">
                AND q.quality_score &lt;= #{maxQualityScore}
              </if>
              <if test="sourcePostId != null">
                AND q.source_post_id = #{sourcePostId}
              </if>
              <if test="taskStatus != null">
                AND q.source_post_id IN (
                    SELECT t.post_id
                    FROM t_ai_extract_task t
                    WHERE t.task_type = 'question_extract'
                      AND t.task_status = #{taskStatus}
                )
              </if>
            </script>
            """)
    long countAdminRecent(@Param("status") Integer status,
                          @Param("keyword") String keyword,
                          @Param("company") String company,
                          @Param("position") String position,
                          @Param("minQualityScore") Integer minQualityScore,
                          @Param("maxQualityScore") Integer maxQualityScore,
                          @Param("sourcePostId") Long sourcePostId,
                          @Param("taskStatus") Integer taskStatus);

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
              <if test="examPoint != null">, exam_point = #{examPoint}</if>
              <if test="referenceAnswer != null">, reference_answer = #{referenceAnswer}</if>
              <if test="sourceSnippet != null">, source_snippet = #{sourceSnippet}</if>
              <if test="qualityReason != null">, quality_reason = #{qualityReason}</if>
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
