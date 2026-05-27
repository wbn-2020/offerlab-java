package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.AiExtractTaskPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiExtractTaskMapper extends BaseMapper<AiExtractTaskPO> {
    @Select("""
            SELECT *
            FROM t_ai_extract_task
            WHERE post_id = #{postId}
              AND task_type = #{taskType}
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    AiExtractTaskPO findLatest(@Param("postId") Long postId, @Param("taskType") String taskType);

    @Select("""
            <script>
            SELECT *
            FROM t_ai_extract_task
            WHERE 1 = 1
              <if test="status != null">
                AND task_status = #{status}
              </if>
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AiExtractTaskPO> listRecent(@Param("status") Integer status, @Param("limit") int limit);

    @Update("""
            UPDATE t_ai_extract_task
            SET task_status = 0,
                retry_count = retry_count + 1,
                error_message = NULL,
                update_time = NOW(3)
            WHERE id = #{id}
              AND task_status = 3
            """)
    int markForRetry(@Param("id") Long id);

    @Update("""
            UPDATE t_ai_extract_task
            SET task_status = #{status},
                update_time = NOW(3)
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
