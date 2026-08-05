package com.offerlab.community.post.collaboration.infrastructure.persistence;

import com.offerlab.community.post.api.ContentMaintenanceTaskRevisionKey;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentMaintenanceTaskMapper extends ContentMaintenanceTaskAttemptMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_collab_content_maintenance_task'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM t_user_account
            WHERE id = #{uid}
              AND is_deleted = 0
            """)
    int userExists(@Param("uid") Long uid);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_content_maintenance_dispatch_batch
            WHERE id = #{batchId}
              AND domain = #{domain}
              AND source_type = 'CHANNEL_HEALTH'
            """)
    int dispatchBatchExists(@Param("batchId") Long batchId,
                            @Param("domain") Integer domain);

    @Select("""
            <script>
            SELECT DISTINCT task.source_post_id
            FROM t_collab_content_maintenance_task task
            INNER JOIN t_post_main source_post
              ON source_post.id = task.source_post_id
            WHERE source_post.author_id = #{authorUid}
              AND source_post.is_deleted = 0
              AND source_post.post_status = 1
              AND source_post.visibility = 1
              AND task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
              AND task.source_post_id IN
              <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
              </foreach>
            </script>
            """)
    List<Long> listActivePublicSourcePostIdsForAuthor(@Param("authorUid") Long authorUid,
                                                      @Param("postIds") Collection<Long> postIds);

    @Select("""
            <script>
            SELECT task.source_post_id AS sourcePostId,
                   task.source_ref_id AS sourceRefId,
                   task.task_status AS status,
                   task.task_priority AS priority,
                   task.current_attempt_no AS currentAttemptNo,
                   task.terminal_outcome_code AS terminalOutcomeCode,
                   task.close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision
            FROM t_collab_content_maintenance_task task
            WHERE task.source_type = #{sourceType}
              AND (
                <foreach collection="keys" item="key" separator=" OR ">
                  (task.source_post_id = #{key.sourcePostId}
                   AND task.source_ref_id = #{key.sourceRefId})
                </foreach>
              )
            </script>
            """)
    List<ContentMaintenanceTaskRow> listTaskStatusesBySourceRevision(
            @Param("sourceType") String sourceType,
            @Param("keys") Collection<ContentMaintenanceTaskRevisionKey> keys);

    @Insert("""
            INSERT INTO t_collab_content_maintenance_task (
                id, domain, source_type, source_ref_id, source_post_id,
                created_by_uid, assignee_uid, title, detail, task_status,
                dispatch_batch_id, task_priority, due_at, current_attempt_no,
                terminal_outcome_code, close_reason_code
            ) VALUES (
                #{id}, #{domain}, #{sourceType}, #{sourceRefId}, #{sourcePostId},
                #{createdByUid}, #{assigneeUid}, #{title}, #{detail}, 'OPEN',
                #{dispatchBatchId}, #{priority}, #{dueAt}, 0, NULL, NULL
            )
            """)
    int insert(@Param("id") Long id,
               @Param("domain") Integer domain,
               @Param("sourceType") String sourceType,
               @Param("sourceRefId") Long sourceRefId,
               @Param("sourcePostId") Long sourcePostId,
               @Param("createdByUid") Long createdByUid,
               @Param("assigneeUid") Long assigneeUid,
               @Param("title") String title,
               @Param("detail") String detail,
               @Param("dispatchBatchId") Long dispatchBatchId,
               @Param("priority") String priority,
               @Param("dueAt") LocalDateTime dueAt);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   dispatch_batch_id AS dispatchBatchId,
                   task_priority AS priority,
                   due_at AS dueAt,
                   current_attempt_no AS currentAttemptNo,
                   terminal_outcome_code AS terminalOutcomeCode,
                   close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE task.id = #{id}
            FOR UPDATE
            """)
    ContentMaintenanceTaskRow lockById(@Param("id") Long id);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   dispatch_batch_id AS dispatchBatchId,
                   task_priority AS priority,
                   due_at AS dueAt,
                   current_attempt_no AS currentAttemptNo,
                   terminal_outcome_code AS terminalOutcomeCode,
                   close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE id = #{id}
            """)
    ContentMaintenanceTaskRow selectById(@Param("id") Long id);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   dispatch_batch_id AS dispatchBatchId,
                   task_priority AS priority,
                   due_at AS dueAt,
                   current_attempt_no AS currentAttemptNo,
                   terminal_outcome_code AS terminalOutcomeCode,
                   close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE assignee_uid = #{uid}
              <if test="status != null and status != ''">
                AND task_status = #{status}
              </if>
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentMaintenanceTaskRow> listMine(@Param("uid") Long uid,
                                             @Param("status") String status,
                                             @Param("cursor") long cursor,
                                             @Param("limit") int limit);

    @Select("""
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   dispatch_batch_id AS dispatchBatchId,
                   task_priority AS priority,
                   due_at AS dueAt,
                   current_attempt_no AS currentAttemptNo,
                   terminal_outcome_code AS terminalOutcomeCode,
                   close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE assignee_uid = #{uid}
              AND task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<ContentMaintenanceTaskRow> listKnowledgeActions(@Param("uid") Long uid,
                                                         @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   dispatch_batch_id AS dispatchBatchId,
                   task_priority AS priority,
                   due_at AS dueAt,
                   current_attempt_no AS currentAttemptNo,
                   terminal_outcome_code AS terminalOutcomeCode,
                   close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE assignee_uid = #{uid}
              AND task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
              <if test="status != null and status != ''">
              AND task_status = #{status}
              </if>
              AND (
                    #{cursorTime} IS NULL
                    OR update_time &lt; #{cursorTime}
                    OR (update_time = #{cursorTime} AND id &lt; #{cursorId})
                  )
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentMaintenanceTaskRow> listKnowledgeActionsAfter(
            @Param("uid") Long uid,
            @Param("status") String status,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM t_collab_content_maintenance_task
            WHERE assignee_uid = #{uid}
              AND task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
              <if test="status != null and status != ''">
              AND task_status = #{status}
              </if>
            </script>
            """)
    long countKnowledgeActions(@Param("uid") Long uid,
                               @Param("status") String status);

    @Select("""
            <script>
            SELECT id,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   source_post_id AS sourcePostId,
                   created_by_uid AS createdByUid,
                   assignee_uid AS assigneeUid,
                   title,
                   detail,
                   task_status AS status,
                   delivery_type AS deliveryType,
                   delivery_ref_id AS deliveryRefId,
                   delivery_post_id AS deliveryPostId,
                   delivery_note AS deliveryNote,
                   review_note AS reviewNote,
                   dispatch_batch_id AS dispatchBatchId,
                   task_priority AS priority,
                   due_at AS dueAt,
                   current_attempt_no AS currentAttemptNo,
                   terminal_outcome_code AS terminalOutcomeCode,
                   close_reason_code AS closeReasonCode,
                   (SELECT attempt.decision
                      FROM t_collab_content_maintenance_task_attempt attempt
                     WHERE attempt.task_id = task.id
                     ORDER BY attempt.attempt_no DESC
                     LIMIT 1) AS latestAttemptDecision,
                   claimed_at AS claimedAt,
                   submitted_at AS submittedAt,
                   reviewed_by_uid AS reviewedByUid,
                   reviewed_at AS reviewedAt,
                   closed_by_uid AS closedByUid,
                   closed_at AS closedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            WHERE 1 = 1
              <if test="domain != null">
                AND domain = #{domain}
              </if>
              <if test="status != null and status != ''">
                AND task_status = #{status}
              </if>
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentMaintenanceTaskRow> listQueue(@Param("domain") Integer domain,
                                              @Param("status") String status,
                                              @Param("cursor") long cursor,
                                              @Param("limit") int limit);

    @Select("""
            <script>
            SELECT task.id,
                   task.domain,
                   task.source_type AS sourceType,
                   task.source_ref_id AS sourceRefId,
                   task.source_post_id AS sourcePostId,
                   source_post.post_type AS sourcePostType,
                   task.assignee_uid AS assigneeUid,
                   task.title,
                   task.task_status AS status,
                   task.create_time AS createTime,
                   task.update_time AS updateTime
            FROM t_collab_content_maintenance_task task
            LEFT JOIN t_post_main source_post
              ON source_post.id = task.source_post_id
            WHERE task.domain = #{domain}
              AND task.task_status = 'OPEN'
              AND (task.assignee_uid IS NULL OR task.assignee_uid = #{uid})
              AND (
                    task.source_post_id IS NULL
                    OR (
                        source_post.is_deleted = 0
                        AND source_post.post_status = 1
                        AND source_post.visibility = 1
                    )
                  )
              <if test="sourceType != null and sourceType != ''">
                AND task.source_type = #{sourceType}
              </if>
              <if test="contentType != null">
                AND source_post.post_type = #{contentType}
              </if>
              AND (#{cursor} = 0 OR task.id &lt; #{cursor})
            ORDER BY task.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentMaintenanceTaskRow> listCandidates(@Param("uid") Long uid,
                                                   @Param("domain") Integer domain,
                                                   @Param("sourceType") String sourceType,
                                                   @Param("contentType") Integer contentType,
                                                   @Param("cursor") long cursor,
                                                   @Param("limit") int limit);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET assignee_uid = #{uid},
                task_status = 'CLAIMED',
                claimed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'OPEN'
              AND (assignee_uid IS NULL OR assignee_uid = #{uid})
            """)
    int claim(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET assignee_uid = #{replacementUid},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status IN ('OPEN', 'CLAIMED')
              AND (assignee_uid IS NULL OR assignee_uid <> #{replacementUid})
            """)
    int reassign(@Param("id") Long id,
                 @Param("replacementUid") Long replacementUid);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'SUBMITTED',
                delivery_type = #{deliveryType},
                delivery_ref_id = #{deliveryRefId},
                delivery_post_id = #{deliveryPostId},
                delivery_note = #{note},
                current_attempt_no = #{attemptNo},
                terminal_outcome_code = NULL,
                close_reason_code = NULL,
                submitted_at = CURRENT_TIMESTAMP(3),
                review_note = NULL,
                reviewed_by_uid = NULL,
                reviewed_at = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'CLAIMED'
              AND assignee_uid = #{uid}
            """)
    int submit(@Param("id") Long id,
               @Param("uid") Long uid,
               @Param("deliveryType") String deliveryType,
               @Param("deliveryRefId") Long deliveryRefId,
               @Param("deliveryPostId") Long deliveryPostId,
               @Param("note") String note,
               @Param("attemptNo") Integer attemptNo);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET current_attempt_no = #{attemptNo},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND current_attempt_no = 0
            """)
    int setCurrentAttemptNo(@Param("id") Long id, @Param("attemptNo") Integer attemptNo);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'COMPLETED',
                review_note = #{note},
                terminal_outcome_code = 'VERIFIED_DELIVERY',
                close_reason_code = NULL,
                reviewed_by_uid = #{reviewerUid},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'SUBMITTED'
            """)
    int approve(@Param("id") Long id,
                @Param("reviewerUid") Long reviewerUid,
                @Param("reasonCode") String reasonCode,
                @Param("note") String note);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'CLAIMED',
                review_note = #{note},
                terminal_outcome_code = NULL,
                close_reason_code = NULL,
                reviewed_by_uid = #{reviewerUid},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status = 'SUBMITTED'
            """)
    int reject(@Param("id") Long id,
               @Param("reviewerUid") Long reviewerUid,
               @Param("reasonCode") String reasonCode,
               @Param("note") String note);

    @Update("""
            UPDATE t_collab_content_maintenance_task
            SET task_status = 'CLOSED',
                review_note = #{note},
                close_reason_code = #{reasonCode},
                closed_by_uid = #{closedByUid},
                closed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
            """)
    int close(@Param("id") Long id,
              @Param("closedByUid") Long closedByUid,
              @Param("reasonCode") String reasonCode,
              @Param("note") String note);
}
