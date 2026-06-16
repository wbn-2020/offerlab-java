package com.offerlab.community.search.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.search.infrastructure.persistence.po.ReviewQueueItemPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewQueueMapper extends BaseMapper<ReviewQueueItemPO> {

    String STATUS_PENDING = "pending";
    String STATUS_CLAIMED = "claimed";
    String STATUS_APPROVED = "approved";
    String STATUS_REJECTED = "rejected";
    String STATUS_CLOSED = "closed";

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_review_queue'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_review_queue (
                id, source_type, source_id, title, summary, risk_level, queue_status,
                creator_uid, priority, ext_json
            ) VALUES (
                #{id}, #{sourceType}, #{sourceId}, #{title}, #{summary}, #{riskLevel}, #{queueStatus},
                #{creatorUid}, #{priority}, #{extJson}
            )
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                summary = VALUES(summary),
                risk_level = VALUES(risk_level),
                priority = VALUES(priority),
                ext_json = VALUES(ext_json),
                is_deleted = 0,
                update_time = NOW(3)
            """)
    int upsertItem(ReviewQueueItemPO item);

    @Select("""
            <script>
            SELECT *
            FROM t_review_queue
            WHERE is_deleted = 0
            <if test="status != null and status != ''">
              AND queue_status = #{status}
            </if>
            <if test="sourceType != null and sourceType != ''">
              AND source_type = #{sourceType}
            </if>
            <if test="riskLevel != null and riskLevel != ''">
              AND risk_level = #{riskLevel}
            </if>
            ORDER BY priority DESC, create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ReviewQueueItemPO> list(@Param("status") String status,
                                 @Param("sourceType") String sourceType,
                                 @Param("riskLevel") String riskLevel,
                                 @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_review_queue
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    ReviewQueueItemPO findById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM t_review_queue
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND is_deleted = 0
            LIMIT 1
            """)
    ReviewQueueItemPO findBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    @Update("""
            UPDATE t_review_queue
            SET queue_status = 'claimed',
                assignee_uid = #{operatorUid},
                update_time = NOW(3)
            WHERE id = #{id}
              AND is_deleted = 0
              AND queue_status IN ('pending', 'claimed')
              AND (assignee_uid IS NULL OR assignee_uid = #{operatorUid})
            """)
    int claim(@Param("id") Long id, @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_review_queue
            SET queue_status = 'pending',
                assignee_uid = NULL,
                update_time = NOW(3)
            WHERE id = #{id}
              AND is_deleted = 0
              AND queue_status = 'claimed'
              AND (assignee_uid IS NULL OR assignee_uid = #{operatorUid})
            """)
    int release(@Param("id") Long id, @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_review_queue
            SET queue_status = #{status},
                handle_result = #{result},
                handle_note = #{note},
                assignee_uid = #{operatorUid},
                handled_time = NOW(3),
                update_time = NOW(3)
            WHERE id = #{id}
              AND is_deleted = 0
              AND queue_status IN ('pending', 'claimed')
              AND (assignee_uid IS NULL OR assignee_uid = #{operatorUid})
            """)
    int resolve(@Param("id") Long id,
                @Param("status") String status,
                @Param("result") String result,
                @Param("note") String note,
                @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_review_queue
            SET queue_status = #{status},
                handle_result = #{result},
                handle_note = #{note},
                assignee_uid = #{operatorUid},
                handled_time = NOW(3),
                update_time = NOW(3)
            WHERE source_type = #{sourceType}
              AND source_id = #{sourceId}
              AND is_deleted = 0
              AND queue_status IN ('pending', 'claimed')
            """)
    int resolveBySource(@Param("sourceType") String sourceType,
                        @Param("sourceId") Long sourceId,
                        @Param("status") String status,
                        @Param("result") String result,
                        @Param("note") String note,
                        @Param("operatorUid") Long operatorUid);

    @Select("""
            SELECT queue_status AS status, COUNT(*) AS count
            FROM t_review_queue
            WHERE is_deleted = 0
            GROUP BY queue_status
            """)
    List<Map<String, Object>> countByStatus();
}
