package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.OperationSlotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OperationSlotMapper extends BaseMapper<OperationSlotPO> {

    @Select("""
            <script>
            SELECT *
            FROM t_operation_slot
            WHERE is_deleted = 0
              <if test="status != null and status != ''">
              AND slot_status = #{status}
              </if>
            ORDER BY sort_order ASC, update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<OperationSlotPO> listSlots(@Param("status") String status, @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_operation_slot
            WHERE is_deleted = 0
              AND slot_code = #{slotCode}
            LIMIT 1
            """)
    OperationSlotPO selectByCode(@Param("slotCode") String slotCode);

    @Select("""
            SELECT *
            FROM t_operation_slot
            WHERE is_deleted = 0
              AND id = #{slotId}
            FOR UPDATE
            """)
    OperationSlotPO selectByIdForUpdate(@Param("slotId") Long slotId);

    @Select("""
            SELECT *
            FROM t_operation_slot
            WHERE is_deleted = 0
              AND slot_code = #{slotCode}
            FOR UPDATE
            """)
    OperationSlotPO selectByCodeForUpdate(@Param("slotCode") String slotCode);

    @Select("""
            SELECT COUNT(*)
            FROM t_operation_slot
            WHERE is_deleted = 0
            """)
    long countActiveRows();

    @Update("""
            UPDATE t_operation_slot
            SET rollback_snapshot_json = #{rollbackSnapshotJson},
                slot_status = #{slotStatus},
                current_version = #{nextVersion},
                published_snapshot_json = #{publishedSnapshotJson},
                updated_by = #{operatorUid},
                update_time = NOW(3)
            WHERE id = #{slotId}
              AND is_deleted = 0
              AND COALESCE(current_version, 0) = #{expectedVersion}
            """)
    int publishIfCurrentVersion(@Param("slotId") Long slotId,
                                @Param("expectedVersion") int expectedVersion,
                                @Param("nextVersion") int nextVersion,
                                @Param("slotStatus") String slotStatus,
                                @Param("rollbackSnapshotJson") String rollbackSnapshotJson,
                                @Param("publishedSnapshotJson") String publishedSnapshotJson,
                                @Param("operatorUid") Long operatorUid);
}
