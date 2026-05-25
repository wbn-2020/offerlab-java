package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionTagPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface InterviewQuestionTagMapper extends BaseMapper<InterviewQuestionTagPO> {
    @Insert("""
            INSERT IGNORE INTO t_interview_question_tag (id, question_id, tag_id)
            VALUES (#{id}, #{questionId}, #{tagId})
            """)
    int insertIgnore(@Param("id") Long id, @Param("questionId") Long questionId, @Param("tagId") Long tagId);

    @Delete("""
            DELETE FROM t_interview_question_tag
            WHERE question_id IN (
                SELECT id FROM t_interview_question WHERE source_post_id = #{postId}
            )
            """)
    int deleteByPostId(@Param("postId") Long postId);

    @Select("""
            <script>
            SELECT r.question_id AS post_id, t.id, t.tag_name, t.tag_type, t.use_count, t.is_official
            FROM t_interview_question_tag r
            JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
            WHERE r.question_id IN
            <foreach collection="questionIds" item="questionId" open="(" separator="," close=")">
                #{questionId}
            </foreach>
            ORDER BY r.question_id ASC, t.id ASC
            </script>
            """)
    List<PostTagView> selectTagsByQuestionIds(@Param("questionIds") Collection<Long> questionIds);

    @Select("""
            SELECT t.tag_name AS name, COUNT(*) AS count
            FROM t_interview_question q
            JOIN t_interview_question_tag r ON r.question_id = q.id
            JOIN t_tag t ON t.id = r.tag_id AND t.is_deleted = 0
            JOIN t_post_main p ON p.id = q.source_post_id
            WHERE q.status = 1
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND q.company = #{company}
            GROUP BY t.id, t.tag_name
            ORDER BY COUNT(*) DESC, t.use_count DESC, t.id ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countTagsByCompany(@Param("company") String company, @Param("limit") int limit);
}
