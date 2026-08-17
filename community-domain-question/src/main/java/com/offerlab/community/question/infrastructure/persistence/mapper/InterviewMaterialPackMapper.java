package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewMaterialPackPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface InterviewMaterialPackMapper extends BaseMapper<InterviewMaterialPackPO> {
    @Select("""
            SELECT *
            FROM t_interview_material_pack
            WHERE uid = #{uid}
              AND post_id = #{postId}
            LIMIT 1
            """)
    InterviewMaterialPackPO selectByUserAndPost(@Param("uid") Long uid, @Param("postId") Long postId);

    @Select("""
            SELECT *
            FROM t_interview_material_pack
            WHERE uid = #{uid}
              AND id = #{id}
            LIMIT 1
            """)
    InterviewMaterialPackPO selectByUserAndId(@Param("uid") Long uid, @Param("id") Long id);

    @Insert("""
            INSERT INTO t_interview_material_pack
                (id, uid, post_id, source_post_version, generation_status,
                 star_situation, star_task, star_action, star_result,
                 resume_bullet_json, follow_up_question_json, technical_highlight_json, missing_hint_json,
                 user_note, saved_to_prep, provider, fallback_used)
            VALUES
                (#{id}, #{uid}, #{postId}, #{sourcePostVersion}, #{generationStatus},
                 #{starSituation}, #{starTask}, #{starAction}, #{starResult},
                 #{resumeBulletJson}, #{followUpQuestionJson}, #{technicalHighlightJson}, #{missingHintJson},
                 #{userNote}, #{savedToPrep}, #{provider}, #{fallbackUsed})
            ON DUPLICATE KEY UPDATE
                source_post_version = VALUES(source_post_version),
                generation_status = VALUES(generation_status),
                star_situation = VALUES(star_situation),
                star_task = VALUES(star_task),
                star_action = VALUES(star_action),
                star_result = VALUES(star_result),
                resume_bullet_json = VALUES(resume_bullet_json),
                follow_up_question_json = VALUES(follow_up_question_json),
                technical_highlight_json = VALUES(technical_highlight_json),
                missing_hint_json = VALUES(missing_hint_json),
                user_note = COALESCE(user_note, VALUES(user_note)),
                saved_to_prep = GREATEST(saved_to_prep, VALUES(saved_to_prep)),
                provider = VALUES(provider),
                fallback_used = VALUES(fallback_used),
                update_time = NOW(3)
            """)
    int upsertGenerated(InterviewMaterialPackPO po);

    @Update("""
            UPDATE t_interview_material_pack
            SET star_situation = #{starSituation},
                star_task = #{starTask},
                star_action = #{starAction},
                star_result = #{starResult},
                resume_bullet_json = #{resumeBulletJson},
                follow_up_question_json = #{followUpQuestionJson},
                technical_highlight_json = #{technicalHighlightJson},
                missing_hint_json = #{missingHintJson},
                user_note = #{userNote},
                update_time = NOW(3)
            WHERE id = #{id}
              AND uid = #{uid}
            """)
    int updateEditable(InterviewMaterialPackPO po);

    @Update("""
            UPDATE t_interview_material_pack
            SET saved_to_prep = 1,
                update_time = NOW(3)
            WHERE id = #{id}
              AND uid = #{uid}
            """)
    int markSavedToPrep(@Param("uid") Long uid, @Param("id") Long id);

    @Select("""
            <script>
            SELECT m.*
            FROM t_interview_material_pack m
            JOIN t_post_main p ON p.id = m.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE m.uid = #{uid}
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND (p.visibility = 1 OR p.author_id = #{uid})
              AND p.content_environment = 'COMMUNITY'
              <if test="savedOnly">
                AND m.saved_to_prep = 1
              </if>
              <if test="company != null and company != ''">
                AND (
                  e.company LIKE CONCAT('%', #{company}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')) LIKE CONCAT('%', #{company}, '%')
                )
              </if>
              <if test="position != null and position != ''">
                AND (
                  e.position LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')) LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{position}, '%')
                )
              </if>
              <if test="techStack != null and techStack != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{techStack}, '%')
                  OR CAST(m.technical_highlight_json AS CHAR) LIKE CONCAT('%', #{techStack}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM t_post_tag_ref ptr
                    JOIN t_tag t ON t.id = ptr.tag_id
                    WHERE ptr.post_id = p.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                )
              </if>
              <if test="interviewRound != null and interviewRound != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.round')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRound')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRounds')) LIKE CONCAT('%', #{interviewRound}, '%')
                )
              </if>
              <if test="postType != null">
                AND p.post_type = #{postType}
              </if>
            ORDER BY m.update_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<InterviewMaterialPackPO> selectByUserFiltered(@Param("uid") Long uid,
                                                       @Param("company") String company,
                                                       @Param("position") String position,
                                                       @Param("techStack") String techStack,
                                                       @Param("interviewRound") String interviewRound,
                                                       @Param("postType") Integer postType,
                                                       @Param("savedOnly") boolean savedOnly,
                                                       @Param("limit") int limit);

    @Select("""
            SELECT
              COUNT(*) AS materialPackCount,
              SUM(CASE WHEN saved_to_prep = 1 THEN 1 ELSE 0 END) AS savedMaterialPackCount
            FROM t_interview_material_pack
            WHERE uid = #{uid}
            """)
    Map<String, Object> countByUser(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT
              COUNT(*) AS materialPackCount,
              COALESCE(SUM(CASE WHEN m.saved_to_prep = 1 THEN 1 ELSE 0 END), 0) AS savedMaterialPackCount
            FROM t_interview_material_pack m
            JOIN t_post_main p ON p.id = m.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE m.uid = #{uid}
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND (p.visibility = 1 OR p.author_id = #{uid})
              AND p.content_environment = 'COMMUNITY'
              <if test="company != null and company != ''">
                AND (
                  e.company LIKE CONCAT('%', #{company}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')) LIKE CONCAT('%', #{company}, '%')
                )
              </if>
              <if test="position != null and position != ''">
                AND (
                  e.position LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')) LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{position}, '%')
                )
              </if>
              <if test="techStack != null and techStack != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{techStack}, '%')
                  OR CAST(m.technical_highlight_json AS CHAR) LIKE CONCAT('%', #{techStack}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM t_post_tag_ref ptr
                    JOIN t_tag t ON t.id = ptr.tag_id
                    WHERE ptr.post_id = p.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                )
              </if>
              <if test="interviewRound != null and interviewRound != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.round')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRound')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRounds')) LIKE CONCAT('%', #{interviewRound}, '%')
                )
              </if>
              <if test="postType != null">
                AND p.post_type = #{postType}
              </if>
            </script>
            """)
    Map<String, Object> countByUserFiltered(@Param("uid") Long uid,
                                            @Param("company") String company,
                                            @Param("position") String position,
                                            @Param("techStack") String techStack,
                                            @Param("interviewRound") String interviewRound,
                                            @Param("postType") Integer postType);

    @Select("""
            SELECT f.post_id
            FROM t_int_favorite f
            JOIN t_post_main p ON p.id = f.post_id
            WHERE f.user_id = #{uid}
              AND f.is_deleted = 0
              AND p.is_deleted = 0
            ORDER BY f.create_time DESC
            LIMIT #{limit}
            """)
    List<Long> selectFavoritePostIds(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT f.post_id
            FROM t_int_favorite f
            JOIN t_post_main p ON p.id = f.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE f.user_id = #{uid}
              AND f.is_deleted = 0
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.content_environment = 'COMMUNITY'
              <if test="company != null and company != ''">
                AND (
                  e.company LIKE CONCAT('%', #{company}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')) LIKE CONCAT('%', #{company}, '%')
                )
              </if>
              <if test="position != null and position != ''">
                AND (
                  e.position LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')) LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{position}, '%')
                )
              </if>
              <if test="techStack != null and techStack != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{techStack}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM t_post_tag_ref ptr
                    JOIN t_tag t ON t.id = ptr.tag_id
                    WHERE ptr.post_id = p.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                )
              </if>
              <if test="interviewRound != null and interviewRound != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.round')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRound')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRounds')) LIKE CONCAT('%', #{interviewRound}, '%')
                )
              </if>
              <if test="postType != null">
                AND p.post_type = #{postType}
              </if>
            ORDER BY f.create_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<Long> selectFavoritePostIdsFiltered(@Param("uid") Long uid,
                                             @Param("company") String company,
                                             @Param("position") String position,
                                             @Param("techStack") String techStack,
                                             @Param("interviewRound") String interviewRound,
                                             @Param("postType") Integer postType,
                                             @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_int_favorite f
            JOIN t_post_main p ON p.id = f.post_id
            WHERE f.user_id = #{uid}
              AND f.is_deleted = 0
              AND p.is_deleted = 0
            """)
    long countFavoritePosts(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_int_favorite f
            JOIN t_post_main p ON p.id = f.post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE f.user_id = #{uid}
              AND f.is_deleted = 0
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.content_environment = 'COMMUNITY'
              <if test="company != null and company != ''">
                AND (
                  e.company LIKE CONCAT('%', #{company}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')) LIKE CONCAT('%', #{company}, '%')
                )
              </if>
              <if test="position != null and position != ''">
                AND (
                  e.position LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')) LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{position}, '%')
                )
              </if>
              <if test="techStack != null and techStack != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{techStack}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM t_post_tag_ref ptr
                    JOIN t_tag t ON t.id = ptr.tag_id
                    WHERE ptr.post_id = p.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                )
              </if>
              <if test="interviewRound != null and interviewRound != ''">
                AND (
                  JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.round')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRound')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRounds')) LIKE CONCAT('%', #{interviewRound}, '%')
                )
              </if>
              <if test="postType != null">
                AND p.post_type = #{postType}
              </if>
            </script>
            """)
    long countFavoritePostsFiltered(@Param("uid") Long uid,
                                    @Param("company") String company,
                                    @Param("position") String position,
                                    @Param("techStack") String techStack,
                                    @Param("interviewRound") String interviewRound,
                                    @Param("postType") Integer postType);

    @Select("""
            <script>
            SELECT up.question_id
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE up.uid = #{uid}
              AND up.favorite = 1
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.content_environment = 'COMMUNITY'
              <if test="company != null and company != ''">
                AND (
                  q.company LIKE CONCAT('%', #{company}, '%')
                  OR e.company LIKE CONCAT('%', #{company}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')) LIKE CONCAT('%', #{company}, '%')
                )
              </if>
              <if test="position != null and position != ''">
                AND (
                  q.position LIKE CONCAT('%', #{position}, '%')
                  OR e.position LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')) LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{position}, '%')
                )
              </if>
              <if test="techStack != null and techStack != ''">
                AND (
                  q.exam_point LIKE CONCAT('%', #{techStack}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{techStack}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM t_interview_question_tag qt
                    JOIN t_tag t ON t.id = qt.tag_id
                    WHERE qt.question_id = q.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                  OR EXISTS (
                    SELECT 1
                    FROM t_post_tag_ref ptr
                    JOIN t_tag t ON t.id = ptr.tag_id
                    WHERE ptr.post_id = p.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                )
              </if>
              <if test="interviewRound != null and interviewRound != ''">
                AND (
                  q.interview_round LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.round')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRound')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRounds')) LIKE CONCAT('%', #{interviewRound}, '%')
                )
              </if>
              <if test="postType != null">
                AND p.post_type = #{postType}
              </if>
            ORDER BY up.update_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<Long> selectFavoriteQuestionIdsFiltered(@Param("uid") Long uid,
                                                 @Param("company") String company,
                                                 @Param("position") String position,
                                                 @Param("techStack") String techStack,
                                                 @Param("interviewRound") String interviewRound,
                                                 @Param("postType") Integer postType,
                                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_user_question_progress up
            JOIN t_interview_question q ON q.id = up.question_id
            JOIN t_post_main p ON p.id = q.source_post_id
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE up.uid = #{uid}
              AND up.favorite = 1
              AND q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.content_environment = 'COMMUNITY'
              <if test="company != null and company != ''">
                AND (
                  q.company LIKE CONCAT('%', #{company}, '%')
                  OR e.company LIKE CONCAT('%', #{company}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.company')) LIKE CONCAT('%', #{company}, '%')
                )
              </if>
              <if test="position != null and position != ''">
                AND (
                  q.position LIKE CONCAT('%', #{position}, '%')
                  OR e.position LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.position')) LIKE CONCAT('%', #{position}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.scenario')) LIKE CONCAT('%', #{position}, '%')
                )
              </if>
              <if test="techStack != null and techStack != ''">
                AND (
                  q.exam_point LIKE CONCAT('%', #{techStack}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.techStacks')) LIKE CONCAT('%', #{techStack}, '%')
                  OR EXISTS (
                    SELECT 1
                    FROM t_interview_question_tag qt
                    JOIN t_tag t ON t.id = qt.tag_id
                    WHERE qt.question_id = q.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                  OR EXISTS (
                    SELECT 1
                    FROM t_post_tag_ref ptr
                    JOIN t_tag t ON t.id = ptr.tag_id
                    WHERE ptr.post_id = p.id
                      AND t.tag_name LIKE CONCAT('%', #{techStack}, '%')
                  )
                )
              </if>
              <if test="interviewRound != null and interviewRound != ''">
                AND (
                  q.interview_round LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.round')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRound')) LIKE CONCAT('%', #{interviewRound}, '%')
                  OR JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.interviewRounds')) LIKE CONCAT('%', #{interviewRound}, '%')
                )
              </if>
              <if test="postType != null">
                AND p.post_type = #{postType}
              </if>
            </script>
            """)
    long countFavoriteQuestionsFiltered(@Param("uid") Long uid,
                                        @Param("company") String company,
                                        @Param("position") String position,
                                        @Param("techStack") String techStack,
                                        @Param("interviewRound") String interviewRound,
                                        @Param("postType") Integer postType);
}
