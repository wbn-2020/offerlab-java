package com.offerlab.community.infra.mq.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox 消息表 Mapper
 */
@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    /**
     * 查询待发送的消息（包括重试消息）
     * @param limit 查询数量
     * @return 消息列表
     */
    @Select("SELECT * FROM t_outbox_message WHERE msg_status = 0 AND (next_retry_time IS NULL OR next_retry_time <= NOW()) LIMIT #{limit}")
    List<OutboxMessage> findPending(@Param("limit") int limit);

    /**
     * 标记消息已发送
     * @param id 消息 ID
     */
    @Update("UPDATE t_outbox_message SET msg_status = 1, update_time = NOW() WHERE id = #{id}")
    void markSent(@Param("id") Long id);

    /**
     * 标记消息失败并更新重试信息
     * @param id 消息 ID
     * @param retryCount 新的重试次数
     * @param nextRetryTime 下次重试时间
     */
    @Update("UPDATE t_outbox_message SET msg_status = #{status}, retry_count = #{retryCount}, next_retry_time = #{nextRetryTime}, update_time = NOW() WHERE id = #{id}")
    void updateRetry(@Param("id") Long id, @Param("status") Integer status, @Param("retryCount") Integer retryCount, @Param("nextRetryTime") LocalDateTime nextRetryTime);

    @Select("SELECT msg_status AS status, COUNT(*) AS count FROM t_outbox_message GROUP BY msg_status")
    List<Map<String, Object>> countByStatus();

    @Select("SELECT COUNT(*) FROM t_outbox_message WHERE msg_status = 0 AND (next_retry_time IS NULL OR next_retry_time <= NOW())")
    long countDuePending();

    @Select("""
            <script>
            SELECT *
            FROM t_outbox_message
            <where>
              <if test="status != null">
                msg_status = #{status}
              </if>
            </where>
            ORDER BY create_time DESC
            LIMIT #{limit}
            </script>
            """)
    List<OutboxMessage> listRecent(@Param("status") Integer status, @Param("limit") int limit);

    @Select("SELECT * FROM t_outbox_message WHERE id = #{id}")
    OutboxMessage findById(@Param("id") Long id);

    @Update("""
            UPDATE t_outbox_message
            SET msg_status = 0,
                next_retry_time = NULL,
                update_time = NOW()
            WHERE id = #{id}
              AND msg_status = 2
            """)
    int markFailedForRetry(@Param("id") Long id);

    @Update("""
            <script>
            UPDATE t_outbox_message
            SET msg_status = 0,
                next_retry_time = NULL,
                update_time = NOW()
            WHERE msg_status = 2
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int markFailedForRetryBatch(@Param("ids") List<Long> ids);
}
