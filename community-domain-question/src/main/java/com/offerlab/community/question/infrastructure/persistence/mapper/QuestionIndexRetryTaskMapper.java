package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.QuestionIndexRetryTaskPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface QuestionIndexRetryTaskMapper extends BaseMapper<QuestionIndexRetryTaskPO> {
    int STATUS_PENDING = 0;
    int STATUS_DONE = 1;
    int STATUS_FAILED = 2;
    int STATUS_RUNNING = 3;

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_question_index_retry_task'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_question_index_retry_task (
                id, dedup_key, question_id, operation, task_status, retry_count, next_retry_time, last_error
            ) VALUES (
                #{id}, #{dedupKey}, #{questionId}, #{operation}, #{taskStatus}, #{retryCount}, #{nextRetryTime}, #{lastError}
            )
            ON DUPLICATE KEY UPDATE
                operation = VALUES(operation),
                task_status = 0,
                retry_count = 0,
                next_retry_time = VALUES(next_retry_time),
                lock_owner = NULL,
                lock_until = NULL,
                last_error = VALUES(last_error),
                update_time = NOW(3)
            """)
    int upsertPending(QuestionIndexRetryTaskPO task);

    @Update("""
            UPDATE t_question_index_retry_task
            SET task_status = 3,
                lock_owner = #{owner},
                lock_until = #{lockUntil},
                update_time = NOW(3)
            WHERE (
                (task_status = 0 AND (next_retry_time IS NULL OR next_retry_time <= NOW(3)))
                OR (task_status = 3 AND lock_until <= NOW(3))
            )
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    int claimDue(@Param("owner") String owner,
                 @Param("lockUntil") LocalDateTime lockUntil,
                 @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_question_index_retry_task
            WHERE task_status = 3
              AND lock_owner = #{owner}
              AND lock_until > NOW(3)
            ORDER BY create_time ASC
            LIMIT #{limit}
            """)
    List<QuestionIndexRetryTaskPO> findClaimed(@Param("owner") String owner, @Param("limit") int limit);

    @Update("""
            UPDATE t_question_index_retry_task
            SET task_status = 1,
                lock_owner = NULL,
                lock_until = NULL,
                last_error = NULL,
                update_time = NOW(3)
            WHERE id = #{id}
              AND task_status = 3
              AND lock_owner = #{owner}
            """)
    int markDone(@Param("id") Long id, @Param("owner") String owner);

    @Update("""
            UPDATE t_question_index_retry_task
            SET task_status = #{status},
                retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                lock_owner = NULL,
                lock_until = NULL,
                last_error = #{lastError},
                update_time = NOW(3)
            WHERE id = #{id}
              AND task_status = 3
              AND lock_owner = #{owner}
            """)
    int updateRetry(@Param("id") Long id,
                    @Param("owner") String owner,
                    @Param("status") Integer status,
                    @Param("retryCount") Integer retryCount,
                    @Param("nextRetryTime") LocalDateTime nextRetryTime,
                    @Param("lastError") String lastError);

    @Select("SELECT task_status AS status, COUNT(*) AS count FROM t_question_index_retry_task GROUP BY task_status")
    List<Map<String, Object>> countByStatus();

    @Select("""
            SELECT COUNT(*)
            FROM t_question_index_retry_task
            WHERE task_status = 0
              AND (next_retry_time IS NULL OR next_retry_time <= NOW(3))
            """)
    long countDuePending();
}
