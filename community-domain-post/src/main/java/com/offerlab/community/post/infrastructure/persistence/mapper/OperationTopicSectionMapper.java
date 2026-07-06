package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationTopicSectionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OperationTopicSectionMapper extends BaseMapper<OperationTopicSectionPO> {

    @Select("""
            <script>
            SELECT *
            FROM t_operation_topic_section
            WHERE is_deleted = 0
              AND topic_id = #{topicId}
              <if test="status != null and status != ''">
              AND section_status = #{status}
              </if>
            ORDER BY sort_order ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<OperationTopicSectionPO> listByTopic(@Param("topicId") Long topicId,
                                              @Param("status") String status,
                                              @Param("limit") int limit);

    @Update("""
            UPDATE t_operation_topic_section
            SET is_deleted = 1, updated_by = #{operatorUid}
            WHERE topic_id = #{topicId}
              AND is_deleted = 0
            """)
    int softDeleteByTopic(@Param("topicId") Long topicId, @Param("operatorUid") Long operatorUid);
}
