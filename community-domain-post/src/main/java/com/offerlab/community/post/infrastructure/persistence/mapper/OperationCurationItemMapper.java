package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationCurationItemPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperationCurationItemMapper extends BaseMapper<OperationCurationItemPO> {

    @Select("""
            <script>
            SELECT *
            FROM t_operation_curation_item
            WHERE is_deleted = 0
              <if test="status != null and status != ''">
              AND item_status = #{status}
              </if>
              <if test="sourceType != null and sourceType != ''">
              AND source_type = #{sourceType}
              </if>
            ORDER BY sort_order ASC, update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<OperationCurationItemPO> listItems(@Param("status") String status,
                                            @Param("sourceType") String sourceType,
                                            @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_operation_curation_item
            WHERE is_deleted = 0
              AND source_type = #{sourceType}
              AND source_id = #{sourceId}
            LIMIT 1
            """)
    OperationCurationItemPO selectBySource(@Param("sourceType") String sourceType,
                                           @Param("sourceId") Long sourceId);
}
