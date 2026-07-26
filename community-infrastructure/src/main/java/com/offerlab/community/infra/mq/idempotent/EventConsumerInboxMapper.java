package com.offerlab.community.infra.mq.idempotent;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface EventConsumerInboxMapper {

    @Insert("""
            INSERT INTO t_event_consumer_inbox (
                id, consumer_name, idempotency_key, event_type, create_time
            ) VALUES (
                #{id}, #{consumerName}, #{idempotencyKey}, #{eventType}, NOW(3)
            )
            """)
    int insertIfAbsent(@Param("id") Long id,
                       @Param("consumerName") String consumerName,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("eventType") String eventType);

    @Delete("""
            DELETE FROM t_event_consumer_inbox
            WHERE create_time < #{before}
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    int deleteBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
