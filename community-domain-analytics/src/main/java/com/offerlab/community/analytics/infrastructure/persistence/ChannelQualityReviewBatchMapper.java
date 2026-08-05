package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ChannelQualityReviewBatchMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_dispatch_batch'
            """)
    int batchTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task'
            """)
    int taskTableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task_attempt'
            """)
    int attemptTableExists();

    @Insert("""
            INSERT INTO t_collab_content_maintenance_dispatch_batch (
                id,
                domain,
                source_type,
                name,
                assignee_uid,
                priority,
                due_at,
                created_by_uid,
                candidate_count
            ) VALUES (
                #{id},
                #{domain},
                #{sourceType},
                #{name},
                #{assigneeUid},
                #{priority},
                #{dueAt},
                #{createdByUid},
                #{candidateCount}
            )
            """)
    int insert(@Param("id") Long id,
               @Param("domain") Integer domain,
               @Param("sourceType") String sourceType,
               @Param("name") String name,
               @Param("assigneeUid") Long assigneeUid,
               @Param("priority") String priority,
               @Param("dueAt") LocalDateTime dueAt,
               @Param("createdByUid") Long createdByUid,
               @Param("candidateCount") Integer candidateCount);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   name,
                   assignee_uid AS assigneeUid,
                   created_by_uid AS createdByUid,
                   priority,
                   due_at AS dueAt,
                   candidate_count AS candidateCount,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE id = #{id}
            """)
    ChannelQualityReviewBatchRow selectById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   name,
                   assignee_uid AS assigneeUid,
                   created_by_uid AS createdByUid,
                   priority,
                   due_at AS dueAt,
                   candidate_count AS candidateCount,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE domain = #{domain}
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ChannelQualityReviewBatchRow> listByDomain(
            @Param("domain") Integer domain,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT dispatch_batch_id AS batchId,
                   task_status AS status,
                   COUNT(*) AS taskCount
            FROM t_collab_content_maintenance_task
            WHERE dispatch_batch_id IN
            <foreach collection="batchIds" item="batchId" open="(" separator="," close=")">
                #{batchId}
            </foreach>
            GROUP BY dispatch_batch_id, task_status
            </script>
            """)
    List<ChannelQualityReviewBatchTaskStatusRow> listTaskStatusCounts(
            @Param("batchIds") Collection<Long> batchIds);

    @Select("""
            SELECT task.id AS taskId,
                   task.dispatch_batch_id AS batchId,
                   task.source_post_id AS sourcePostId,
                   task.source_ref_id AS sourceRefId,
                   task.title,
                   task.task_status AS status,
                   task.terminal_outcome_code AS terminalOutcomeCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision
            FROM t_collab_content_maintenance_task task
            WHERE task.dispatch_batch_id = #{batchId}
            ORDER BY task.id ASC
            LIMIT #{limit}
            """)
    List<ChannelQualityReviewBatchTaskRow> listTasksByBatchId(
            @Param("batchId") Long batchId,
            @Param("limit") int limit);
}
