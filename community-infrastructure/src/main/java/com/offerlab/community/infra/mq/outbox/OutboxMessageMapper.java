package com.offerlab.community.infra.mq.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Outbox message mapper.
 */
@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    int STATUS_PENDING = 0;
    int STATUS_SENT = 1;
    int STATUS_FAILED = 2;
    int STATUS_SENDING = 3;

    @Update("""
            UPDATE t_outbox_message
            SET msg_status = 3,
                lock_owner = #{owner},
                lock_until = #{lockUntil},
                update_time = NOW(3)
            WHERE (
                  (
                    msg_status = 0
                    AND (next_retry_time IS NULL OR next_retry_time <= NOW(3))
                  )
                  OR (
                    msg_status = 3
                    AND lock_until <= NOW(3)
                  )
            )
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    int claimPending(@Param("owner") String owner,
                     @Param("lockUntil") LocalDateTime lockUntil,
                     @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_outbox_message
            WHERE msg_status = 3
              AND lock_owner = #{owner}
              AND lock_until > NOW(3)
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<OutboxMessage> findClaimed(@Param("owner") String owner, @Param("limit") int limit);

    @Update("""
            UPDATE t_outbox_message
            SET lock_until = #{lockUntil},
                update_time = NOW(3)
            WHERE id = #{id}
              AND msg_status = 3
              AND lock_owner = #{owner}
              AND lock_until > NOW(3)
            """)
    int renewClaim(@Param("id") Long id,
                   @Param("owner") String owner,
                   @Param("lockUntil") LocalDateTime lockUntil);

    @Update("""
            UPDATE t_outbox_message
            SET msg_status = 1,
                next_retry_time = NULL,
                lock_owner = NULL,
                lock_until = NULL,
                update_time = NOW(3)
            WHERE id = #{id}
              AND msg_status = 3
              AND lock_owner = #{owner}
            """)
    int markSent(@Param("id") Long id, @Param("owner") String owner);

    @Update("""
            UPDATE t_outbox_message
            SET msg_status = #{status},
                retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                lock_owner = NULL,
                lock_until = NULL,
                update_time = NOW(3)
            WHERE id = #{id}
              AND msg_status = 3
              AND lock_owner = #{owner}
            """)
    int updateRetry(@Param("id") Long id,
                    @Param("owner") String owner,
                    @Param("status") Integer status,
                    @Param("retryCount") Integer retryCount,
                    @Param("nextRetryTime") LocalDateTime nextRetryTime);

    @Select("SELECT msg_status AS status, COUNT(*) AS count FROM t_outbox_message GROUP BY msg_status")
    List<Map<String, Object>> countByStatus();

    @Select("""
            SELECT COUNT(*)
            FROM t_outbox_message
            WHERE msg_status = 0
              AND (next_retry_time IS NULL OR next_retry_time <= NOW(3))
            """)
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

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_outbox_message
            <where>
              <if test="status != null">
                msg_status = #{status}
              </if>
            </where>
            </script>
            """)
    long countPage(@Param("status") Integer status);

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
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<OutboxMessage> pageRecent(@Param("status") Integer status,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Select("SELECT * FROM t_outbox_message WHERE id = #{id}")
    OutboxMessage findById(@Param("id") Long id);

    @Select("""
            SELECT *
            FROM t_outbox_message
            WHERE aggregate_type = #{aggregateType}
              AND aggregate_id = #{aggregateId}
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    OutboxMessage findLatestByAggregate(@Param("aggregateType") String aggregateType,
                                        @Param("aggregateId") Long aggregateId);

    @Update("""
            UPDATE t_outbox_message
            SET msg_status = 0,
                next_retry_time = NULL,
                lock_owner = NULL,
                lock_until = NULL,
                update_time = NOW(3)
            WHERE id = #{id}
              AND msg_status = 2
            """)
    int markFailedForRetry(@Param("id") Long id);

    @Update("""
            <script>
            UPDATE t_outbox_message
            SET msg_status = 0,
                next_retry_time = NULL,
                lock_owner = NULL,
                lock_until = NULL,
                update_time = NOW(3)
            WHERE msg_status = 2
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    int markFailedForRetryBatch(@Param("ids") List<Long> ids);

    @Delete("""
            DELETE FROM t_outbox_message
            WHERE msg_status = #{status}
              AND next_retry_time IS NULL
              AND create_time < #{before}
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    int deleteTerminalBefore(@Param("status") Integer status,
                             @Param("before") LocalDateTime before,
                             @Param("limit") int limit);
}
