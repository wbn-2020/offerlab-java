package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationTopicPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperationTopicMapper extends BaseMapper<OperationTopicPO> {

    @Select("""
            <script>
            SELECT *
            FROM t_operation_topic
            WHERE is_deleted = 0
              <if test="status != null and status != ''">
              AND topic_status = #{status}
              </if>
              <if test="operationType != null and operationType != ''">
              AND operation_type = #{operationType}
              </if>
              <if test="keyword != null and keyword != ''">
              AND (
                    topic_name LIKE CONCAT('%', #{keyword}, '%')
                    OR slug LIKE CONCAT('%', #{keyword}, '%')
                    OR description LIKE CONCAT('%', #{keyword}, '%')
                  )
              </if>
            ORDER BY sort_order ASC, update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<OperationTopicPO> listTopics(@Param("status") String status,
                                      @Param("operationType") String operationType,
                                      @Param("keyword") String keyword,
                                      @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_operation_topic
            WHERE is_deleted = 0
              AND slug = #{slug}
            LIMIT 1
            """)
    OperationTopicPO selectBySlug(@Param("slug") String slug);
}
