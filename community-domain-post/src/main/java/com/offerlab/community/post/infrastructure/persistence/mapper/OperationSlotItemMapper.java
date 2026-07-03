package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationSlotItemPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OperationSlotItemMapper extends BaseMapper<OperationSlotItemPO> {

    @Select("""
            <script>
            SELECT *
            FROM t_operation_slot_item
            WHERE is_deleted = 0
              AND slot_id = #{slotId}
              <if test="status != null and status != ''">
              AND item_status = #{status}
              </if>
            ORDER BY sort_order ASC, update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<OperationSlotItemPO> listBySlot(@Param("slotId") Long slotId,
                                         @Param("status") String status,
                                         @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_operation_slot_item
            WHERE is_deleted = 0
              AND slot_id = #{slotId}
              AND source_type = #{sourceType}
              AND source_id = #{sourceId}
            LIMIT 1
            """)
    OperationSlotItemPO selectBySlotSource(@Param("slotId") Long slotId,
                                           @Param("sourceType") String sourceType,
                                           @Param("sourceId") Long sourceId);
}
