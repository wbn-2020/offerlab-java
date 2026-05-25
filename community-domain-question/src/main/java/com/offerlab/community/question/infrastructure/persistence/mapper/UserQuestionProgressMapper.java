package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.UserQuestionProgressPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
                (id, uid, question_id, progress_status, favorite, note)
            VALUES
                (#{id}, #{uid}, #{questionId}, #{status}, #{favorite}, #{note})
            ON DUPLICATE KEY UPDATE
                progress_status = COALESCE(VALUES(progress_status), progress_status),
                favorite = VALUES(favorite),
                note = COALESCE(VALUES(note), note),
                update_time = NOW(3)
            """)
    int upsert(@Param("id") Long id,
               @Param("uid") Long uid,
               @Param("questionId") Long questionId,
               @Param("status") String status,
               @Param("favorite") Integer favorite,
               @Param("note") String note);

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

    @Select("""
            SELECT *
            FROM t_user_question_progress
            WHERE uid = #{uid}
            ORDER BY update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentByUser(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_user_question_progress
            WHERE uid = #{uid}
              AND favorite = 1
            ORDER BY update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentFavorites(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_user_question_progress
            WHERE uid = #{uid}
              AND progress_status = #{status}
            ORDER BY update_time DESC
            LIMIT #{limit}
            """)
    List<UserQuestionProgressPO> selectRecentByStatus(@Param("uid") Long uid,
                                                      @Param("status") String status,
                                                      @Param("limit") int limit);
}
