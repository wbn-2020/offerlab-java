package com.offerlab.community.analytics.infrastructure.persistence.mapper;

import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.AuditReplayRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.IssueRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.KnowledgeLifecycleSourceHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconcileRequestRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.ReconciliationRunRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.DeliveryHealthRow;
import com.offerlab.community.analytics.infrastructure.persistence.ProjectionHealthRows.RewardInboxDeliveryHealthRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectionHealthMapper {

    @Select("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_admin_audit_log',
                't_projection_reconcile_request',
                't_incentive_account',
                't_incentive_ledger',
                't_incentive_reconciliation_run',
                't_incentive_reconciliation_item',
                't_incentive_reward_inbox',
                't_community_role_definition',
                't_community_role_grant',
                't_incentive_freeze_record',
                't_collab_content_maintenance_task',
                't_collab_content_need',
                't_collab_content_need_event',
                't_notif_retry_task',
                't_search_index_retry_task',
                't_feed_feedback_preference',
                't_collab_topic_post',
                't_post_main',
                't_post_extension',
                't_int_post_trust_state',
                't_collab_series',
                't_collab_series_submission',
                't_outbox_message',
                't_int_content_suggestion',
                't_post_reference',
                't_post_knowledge_relation',
                't_int_post_outcome'
              )
            """)
    List<String> selectExistingProjectionTables();

    @Select("""
            SELECT id AS runId,
                   run_status AS status,
                   mismatch_count AS issueCount,
                   COALESCE(finish_time, create_time) AS checkedAt
            FROM t_incentive_reconciliation_run
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    ReconciliationRunRow selectLatestIncentiveReconciliation();

    @Select("""
            SELECT MAX(CASE WHEN msg_status = 1 THEN id END) AS watermark,
                   SUM(CASE WHEN msg_status IN (0, 3) THEN 1 ELSE 0 END) AS backlogCount,
                   SUM(CASE WHEN msg_status = 2 THEN 1 ELSE 0 END) AS failedCount,
                   MIN(CASE WHEN msg_status IN (0, 3) THEN create_time END) AS oldestBacklogAt,
                   MAX(CASE WHEN msg_status = 1 THEN update_time END) AS lastSuccessAt,
                   MAX(CASE WHEN msg_status = 2 THEN update_time END) AS lastFailureAt
            FROM t_outbox_message
            """)
    DeliveryHealthRow selectOutboxDeliveryHealth();

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id
                FROM t_outbox_message
                WHERE msg_status = 2
                   OR (msg_status = 3 AND lock_until <= CURRENT_TIMESTAMP(3))
                   OR (msg_status = 0
                       AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP(3))
                       AND create_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR))
                ORDER BY id
                LIMIT #{cap}
            ) bounded
            """)
    long countOutboxDeliveryIssues(@Param("cap") int cap);

    @Select("""
            SELECT id AS issueId,
                   CASE
                       WHEN msg_status = 2 THEN 'OUTBOX_DELIVERY_FAILED'
                       WHEN msg_status = 3 THEN 'OUTBOX_DELIVERY_LOCK_EXPIRED'
                       ELSE 'OUTBOX_DELIVERY_STALLED'
                   END AS issueType,
                   CASE WHEN msg_status = 2 THEN 'HIGH' ELSE 'MEDIUM' END AS severity,
                   'OUTBOX_MESSAGE' AS subjectType,
                   CAST(id AS CHAR) AS subjectId,
                   CONCAT('Outbox 事件 ', topic, ' 需要通过既有运维入口处理') AS summary,
                   update_time AS detectedAt
            FROM t_outbox_message
            WHERE (
                    msg_status = 2
                    OR (msg_status = 3 AND lock_until <= CURRENT_TIMESTAMP(3))
                    OR (msg_status = 0
                        AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP(3))
                        AND create_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR))
                  )
              AND (#{cursor} = 0 OR id < #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listOutboxDeliveryIssues(@Param("cursor") long cursor,
                                            @Param("limit") int limit);

    @Select("""
            SELECT MAX(CASE WHEN inbox_status IN ('APPLIED', 'REJECTED') THEN id END) AS watermark,
                   SUM(CASE WHEN inbox_status = 'PENDING' THEN 1 ELSE 0 END) AS backlogCount,
                   MIN(CASE WHEN inbox_status = 'PENDING' THEN create_time END) AS oldestBacklogAt,
                   MAX(CASE WHEN inbox_status = 'APPLIED' THEN processed_time END) AS lastSuccessAt,
                   MAX(CASE WHEN inbox_status = 'REJECTED' THEN processed_time END) AS lastFailureAt
            FROM t_incentive_reward_inbox
            """)
    RewardInboxDeliveryHealthRow selectRewardInboxDeliveryHealth();

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id
                FROM t_incentive_reward_inbox
                WHERE inbox_status = 'PENDING'
                  AND create_time <= #{cutoff}
                ORDER BY create_time, id
                LIMIT #{cap}
            ) bounded
            """)
    long countOverdueRewardInboxDeliveryIssues(@Param("cutoff") LocalDateTime cutoff,
                                               @Param("cap") int cap);

    @Select("""
            SELECT id AS issueId,
                   'REWARD_INBOX_PENDING_OVER_SLA' AS issueType,
                   'HIGH' AS severity,
                   'INCENTIVE_REWARD_INBOX' AS subjectType,
                   CAST(id AS CHAR) AS subjectId,
                   CONCAT('奖励 Inbox 超过 SLA 仍为 PENDING，规则 ', rule_code,
                          '，接收用户 ', recipient_uid) AS summary,
                   DATE_ADD(create_time, INTERVAL 60 MINUTE) AS detectedAt
            FROM t_incentive_reward_inbox
            WHERE inbox_status = 'PENDING'
              AND create_time <= #{cutoff}
              AND (#{cursor} = 0 OR id < #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listOverdueRewardInboxDeliveryIssues(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            SELECT item.id AS issueId,
                   'ACCOUNT_LEDGER_MISMATCH' AS issueType,
                   'HIGH' AS severity,
                   'INCENTIVE_ACCOUNT' AS subjectType,
                   CAST(item.account_id AS CHAR) AS subjectId,
                   CONCAT('激励账户与账本绝对差异为 ', item.absolute_difference) AS summary,
                   run.create_time AS detectedAt
            FROM t_incentive_reconciliation_item item
            INNER JOIN (
                SELECT id, create_time
                FROM t_incentive_reconciliation_run
                ORDER BY create_time DESC, id DESC
                LIMIT 1
            ) run ON run.id = item.run_id
            WHERE (#{cursor} = 0 OR item.id < #{cursor})
            ORDER BY item.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listIncentiveAccountIssues(@Param("cursor") long cursor,
                                              @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id
                FROM t_community_role_grant
                WHERE grant_status IN ('ACTIVE', 'SUSPENDED')
                  AND expires_at IS NOT NULL
                  AND expires_at <= CURRENT_TIMESTAMP(3)
                ORDER BY expires_at, id
                LIMIT #{cap}
            ) bounded
            """)
    long countExpiredRoleGrants(@Param("cap") int cap);

    @Select("""
            SELECT id AS issueId,
                   'ROLE_GRANT_EXPIRED_NOT_CLOSED' AS issueType,
                   'HIGH' AS severity,
                   'COMMUNITY_ROLE_GRANT' AS subjectType,
                   CAST(id AS CHAR) AS subjectId,
                   CONCAT('角色 ', role_code, ' / ', domain_code, ' 已到期但状态仍为 ', grant_status) AS summary,
                   expires_at AS detectedAt
            FROM t_community_role_grant
            WHERE grant_status IN ('ACTIVE', 'SUSPENDED')
              AND expires_at IS NOT NULL
              AND expires_at <= CURRENT_TIMESTAMP(3)
              AND (#{cursor} = 0 OR id < #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listExpiredRoleGrantIssues(@Param("cursor") long cursor,
                                              @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT task.id
                FROM t_collab_content_maintenance_task task
                WHERE task.assignee_uid IS NOT NULL
                  AND task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM t_community_role_grant role_grant
                      INNER JOIN t_community_role_definition role_definition
                        ON role_definition.role_code = role_grant.role_code
                       AND role_definition.domain_code = role_grant.domain_code
                       AND role_definition.enabled = 1
                      WHERE role_grant.user_id = task.assignee_uid
                        AND role_grant.role_code = 'CHANNEL_RESOURCE_MAINTAINER'
                        AND role_grant.domain_code = CASE task.domain
                            WHEN 1 THEN 'TECH'
                            WHEN 2 THEN 'CAREER'
                            WHEN 3 THEN 'READING'
                            WHEN 4 THEN 'LIFESTYLE'
                            WHEN 5 THEN 'INVESTMENT'
                            ELSE ''
                        END
                        AND role_grant.grant_status = 'ACTIVE'
                        AND (role_grant.expires_at IS NULL
                             OR role_grant.expires_at > CURRENT_TIMESTAMP(3))
                        AND (
                            role_definition.requires_no_risk_freeze = 0
                            OR NOT EXISTS (
                                SELECT 1
                                FROM t_incentive_freeze_record freeze_record
                                WHERE freeze_record.user_id = role_grant.user_id
                                  AND freeze_record.freeze_status = 'ACTIVE'
                            )
                        )
                  )
                ORDER BY task.id
                LIMIT #{cap}
            ) bounded
            """)
    long countInvalidMaintenanceAssignments(@Param("cap") int cap);

    @Select("""
            SELECT task.id AS issueId,
                   'MAINTENANCE_ASSIGNEE_ROLE_INACTIVE' AS issueType,
                   'HIGH' AS severity,
                   'CONTENT_MAINTENANCE_TASK' AS subjectType,
                   CAST(task.id AS CHAR) AS subjectId,
                   CONCAT('任务领域 ', task.domain, ' 的当前受派人缺少有效维护角色') AS summary,
                   task.update_time AS detectedAt
            FROM t_collab_content_maintenance_task task
            WHERE task.assignee_uid IS NOT NULL
              AND task.task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_community_role_grant role_grant
                  INNER JOIN t_community_role_definition role_definition
                    ON role_definition.role_code = role_grant.role_code
                   AND role_definition.domain_code = role_grant.domain_code
                   AND role_definition.enabled = 1
                  WHERE role_grant.user_id = task.assignee_uid
                    AND role_grant.role_code = 'CHANNEL_RESOURCE_MAINTAINER'
                    AND role_grant.domain_code = CASE task.domain
                        WHEN 1 THEN 'TECH'
                        WHEN 2 THEN 'CAREER'
                        WHEN 3 THEN 'READING'
                        WHEN 4 THEN 'LIFESTYLE'
                        WHEN 5 THEN 'INVESTMENT'
                        ELSE ''
                    END
                    AND role_grant.grant_status = 'ACTIVE'
                    AND (role_grant.expires_at IS NULL
                         OR role_grant.expires_at > CURRENT_TIMESTAMP(3))
                    AND (
                        role_definition.requires_no_risk_freeze = 0
                        OR NOT EXISTS (
                            SELECT 1
                            FROM t_incentive_freeze_record freeze_record
                            WHERE freeze_record.user_id = role_grant.user_id
                              AND freeze_record.freeze_status = 'ACTIVE'
                        )
                    )
              )
              AND (#{cursor} = 0 OR task.id < #{cursor})
            ORDER BY task.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listInvalidMaintenanceAssignments(@Param("cursor") long cursor,
                                                     @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT task.id
                FROM t_collab_content_maintenance_task task
                LEFT JOIN t_post_main delivery_post
                  ON delivery_post.id = task.delivery_post_id
                LEFT JOIN t_collab_series delivery_series
                  ON delivery_series.id = task.delivery_ref_id
                WHERE task.task_status = 'COMPLETED'
                  AND NOT (
                      (
                          task.delivery_type IN ('POST', 'QUESTION')
                          AND task.delivery_ref_id = task.delivery_post_id
                          AND delivery_post.is_deleted = 0
                          AND delivery_post.post_status = 1
                          AND delivery_post.visibility = 1
                          AND delivery_post.content_environment = 'COMMUNITY'
                      )
                      OR (
                          task.delivery_type = 'SERIES'
                          AND task.delivery_post_id IS NULL
                          AND delivery_series.moderation_hidden = 0
                          AND EXISTS (
                              SELECT 1
                              FROM t_collab_series_submission submission
                              INNER JOIN t_post_main series_post
                                ON series_post.id = submission.post_id
                               AND series_post.is_deleted = 0
                               AND series_post.post_status = 1
                               AND series_post.visibility = 1
                               AND series_post.content_environment = 'COMMUNITY'
                              WHERE submission.series_id = delivery_series.id
                                AND submission.review_status = 'APPROVED'
                          )
                      )
                  )
                ORDER BY task.id
                LIMIT #{cap}
            ) bounded
            """)
    long countNonPublicMaintenanceContributions(@Param("cap") int cap);

    @Select("""
            SELECT task.id AS issueId,
                   'COMPLETED_MAINTENANCE_DELIVERY_NOT_PUBLIC' AS issueType,
                   'HIGH' AS severity,
                   'CONTENT_MAINTENANCE_TASK' AS subjectType,
                   CAST(task.id AS CHAR) AS subjectId,
                   '已完成维护任务的交付资源当前不可公开展示' AS summary,
                   COALESCE(task.reviewed_at, task.update_time) AS detectedAt
            FROM t_collab_content_maintenance_task task
            LEFT JOIN t_post_main delivery_post
              ON delivery_post.id = task.delivery_post_id
            LEFT JOIN t_collab_series delivery_series
              ON delivery_series.id = task.delivery_ref_id
            WHERE task.task_status = 'COMPLETED'
              AND NOT (
                  (
                      task.delivery_type IN ('POST', 'QUESTION')
                      AND task.delivery_ref_id = task.delivery_post_id
                      AND delivery_post.is_deleted = 0
                      AND delivery_post.post_status = 1
                      AND delivery_post.visibility = 1
                      AND delivery_post.content_environment = 'COMMUNITY'
                  )
                  OR (
                      task.delivery_type = 'SERIES'
                      AND task.delivery_post_id IS NULL
                      AND delivery_series.moderation_hidden = 0
                      AND EXISTS (
                          SELECT 1
                          FROM t_collab_series_submission submission
                          INNER JOIN t_post_main series_post
                            ON series_post.id = submission.post_id
                           AND series_post.is_deleted = 0
                           AND series_post.post_status = 1
                           AND series_post.visibility = 1
                           AND series_post.content_environment = 'COMMUNITY'
                          WHERE submission.series_id = delivery_series.id
                            AND submission.review_status = 'APPROVED'
                      )
                  )
              )
              AND (#{cursor} = 0 OR task.id < #{cursor})
            ORDER BY task.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listNonPublicMaintenanceContributions(@Param("cursor") long cursor,
                                                         @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT need.id
                FROM t_collab_content_need need
                WHERE need.need_status = 'COMPLETED'
                  AND need.moderation_hidden = 0
                  AND NOT EXISTS (
                      SELECT 1
                      FROM t_collab_content_need_event event
                      WHERE event.need_id = need.id
                        AND event.visibility_scope = 'PUBLIC'
                        AND event.event_type IN ('ACCEPTED', 'COMPLETED')
                        AND event.to_status = 'COMPLETED'
                  )
                ORDER BY need.id
                LIMIT #{cap}
            ) bounded
            """)
    long countMissingCollaborationTimelineFacts(@Param("cap") int cap);

    @Select("""
            SELECT need.id AS issueId,
                   'COMPLETED_NEED_PUBLIC_EVENT_MISSING' AS issueType,
                   'HIGH' AS severity,
                   'COLLABORATION_NEED' AS subjectType,
                   CAST(need.id AS CHAR) AS subjectId,
                   '已完成需求缺少可公开的完成时间线事实' AS summary,
                   need.update_time AS detectedAt
            FROM t_collab_content_need need
            WHERE need.need_status = 'COMPLETED'
              AND need.moderation_hidden = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_collab_content_need_event event
                  WHERE event.need_id = need.id
                    AND event.visibility_scope = 'PUBLIC'
                    AND event.event_type IN ('ACCEPTED', 'COMPLETED')
                    AND event.to_status = 'COMPLETED'
              )
              AND (#{cursor} = 0 OR need.id < #{cursor})
            ORDER BY need.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listMissingCollaborationTimelineFacts(@Param("cursor") long cursor,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id
                FROM t_notif_retry_task
                WHERE task_status = 2
                   OR (task_status = 3 AND lock_until <= CURRENT_TIMESTAMP(3))
                   OR (
                       task_status = 0
                       AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP(3))
                       AND update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR)
                   )
                ORDER BY id
                LIMIT #{cap}
            ) bounded
            """)
    long countNotificationDeliveryIssues(@Param("cap") int cap);

    @Select("""
            SELECT id AS issueId,
                   CASE
                       WHEN task_status = 2 THEN 'NOTIFICATION_RETRY_FAILED'
                       WHEN task_status = 3 THEN 'NOTIFICATION_RETRY_LOCK_EXPIRED'
                       ELSE 'NOTIFICATION_RETRY_STALLED'
                   END AS issueType,
                   CASE WHEN task_status = 2 THEN 'HIGH' ELSE 'MEDIUM' END AS severity,
                   'NOTIFICATION_RETRY_TASK' AS subjectType,
                   CAST(id AS CHAR) AS subjectId,
                   '通知重试投影需要通过既有通知运维入口处理' AS summary,
                   update_time AS detectedAt
            FROM t_notif_retry_task
            WHERE (
                    task_status = 2
                    OR (task_status = 3 AND lock_until <= CURRENT_TIMESTAMP(3))
                    OR (
                        task_status = 0
                        AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP(3))
                        AND update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR)
                    )
                  )
              AND (#{cursor} = 0 OR id < #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listNotificationDeliveryIssues(@Param("cursor") long cursor,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT id
                FROM t_search_index_retry_task
                WHERE task_status = 2
                   OR (task_status = 3 AND lock_until <= CURRENT_TIMESTAMP(3))
                   OR (
                       task_status = 0
                       AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP(3))
                       AND update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR)
                   )
                ORDER BY id
                LIMIT #{cap}
            ) bounded
            """)
    long countSearchIndexQueueIssues(@Param("cap") int cap);

    @Select("""
            SELECT id AS issueId,
                   CASE
                       WHEN task_status = 2 THEN 'SEARCH_INDEX_RETRY_FAILED'
                       WHEN task_status = 3 THEN 'SEARCH_INDEX_RETRY_LOCK_EXPIRED'
                       ELSE 'SEARCH_INDEX_RETRY_STALLED'
                   END AS issueType,
                   CASE WHEN task_status = 2 THEN 'HIGH' ELSE 'MEDIUM' END AS severity,
                   'SEARCH_INDEX_RETRY_TASK' AS subjectType,
                   CAST(id AS CHAR) AS subjectId,
                   '搜索索引任务需要通过既有搜索运维入口处理' AS summary,
                   update_time AS detectedAt
            FROM t_search_index_retry_task
            WHERE (
                    task_status = 2
                    OR (task_status = 3 AND lock_until <= CURRENT_TIMESTAMP(3))
                    OR (
                        task_status = 0
                        AND (next_retry_time IS NULL OR next_retry_time <= CURRENT_TIMESTAMP(3))
                        AND update_time <= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 HOUR)
                    )
                  )
              AND (#{cursor} = 0 OR id < #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listSearchIndexQueueIssues(@Param("cursor") long cursor,
                                              @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT preference.id
                FROM t_feed_feedback_preference preference
                LEFT JOIN t_post_main post ON post.id = preference.post_id
                WHERE preference.expires_at > CURRENT_TIMESTAMP(3)
                  AND (
                      post.id IS NULL
                      OR post.is_deleted <> 0
                      OR post.post_status <> 1
                      OR post.visibility <> 1
                  )
                ORDER BY preference.id
                LIMIT #{cap}
            ) bounded
            """)
    long countFeedFeedbackVisibilityIssues(@Param("cap") int cap);

    @Select("""
            SELECT preference.id AS issueId,
                   'FEED_FEEDBACK_TARGET_NOT_PUBLIC' AS issueType,
                   'MEDIUM' AS severity,
                   'FEED_FEEDBACK_PREFERENCE' AS subjectType,
                   CAST(preference.id AS CHAR) AS subjectId,
                   '有效 Feed 控制指向已不可公开访问的内容' AS summary,
                   preference.update_time AS detectedAt
            FROM t_feed_feedback_preference preference
            LEFT JOIN t_post_main post ON post.id = preference.post_id
            WHERE preference.expires_at > CURRENT_TIMESTAMP(3)
              AND (
                  post.id IS NULL
                  OR post.is_deleted <> 0
                  OR post.post_status <> 1
                  OR post.visibility <> 1
              )
              AND (#{cursor} = 0 OR preference.id < #{cursor})
            ORDER BY preference.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listFeedFeedbackVisibilityIssues(@Param("cursor") long cursor,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM (
                SELECT relation.id
                FROM t_collab_topic_post relation
                LEFT JOIN t_post_main post ON post.id = relation.post_id
                WHERE post.id IS NULL
                   OR post.is_deleted <> 0
                   OR post.post_status <> 1
                   OR post.visibility <> 1
                ORDER BY relation.id
                LIMIT #{cap}
            ) bounded
            """)
    long countTopicSpaceVisibilityIssues(@Param("cap") int cap);

    @Select("""
            SELECT relation.id AS issueId,
                   'TOPIC_SPACE_RELATION_NOT_PUBLIC' AS issueType,
                   'HIGH' AS severity,
                   'TOPIC_POST_RELATION' AS subjectType,
                   CAST(relation.id AS CHAR) AS subjectId,
                   '主题空间关联指向已删除、隐藏或未发布内容' AS summary,
                   relation.create_time AS detectedAt
            FROM t_collab_topic_post relation
            LEFT JOIN t_post_main post ON post.id = relation.post_id
            WHERE (
                    post.id IS NULL
                    OR post.is_deleted <> 0
                    OR post.post_status <> 1
                    OR post.visibility <> 1
                  )
              AND (#{cursor} = 0 OR relation.id < #{cursor})
            ORDER BY relation.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listTopicSpaceVisibilityIssues(@Param("cursor") long cursor,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS issueCount,
                   MIN(issueAt) AS oldestIssueAt
            FROM (
                SELECT create_time AS issueAt
                FROM t_int_content_suggestion
                WHERE resolution = 'PENDING'
                ORDER BY create_time, id
                LIMIT #{cap}
            ) bounded
            """)
    KnowledgeLifecycleSourceHealthRow selectPendingSuggestionHealth(@Param("cap") int cap);

    @Select("""
            SELECT t_int_content_suggestion.id AS issueId,
                   'CONTENT_SUGGESTION_PENDING' AS issueType,
                   'MEDIUM' AS severity,
                   'CONTENT_SUGGESTION' AS subjectType,
                   CAST(t_int_content_suggestion.id AS CHAR) AS subjectId,
                   CONCAT('内容建议仍待处理，目标范围 ', t_int_content_suggestion.target_scope) AS summary,
                   t_int_content_suggestion.create_time AS detectedAt,
                   CASE
                       WHEN host_post.id IS NOT NULL
                        AND host_post.is_deleted = 0
                        AND host_post.post_status = 1
                        AND host_post.visibility = 1
                        AND host_post.content_environment = 'COMMUNITY'
                        AND host_post.content_environment = 'COMMUNITY'
                        AND host_post.content_environment = 'COMMUNITY'
                        AND host_post.content_environment = 'COMMUNITY'
                           THEN t_int_content_suggestion.post_id
                   END AS relatedPostId,
                   ext.domain AS domain
            FROM t_int_content_suggestion
            LEFT JOIN t_post_main host_post
              ON host_post.id = t_int_content_suggestion.post_id
            LEFT JOIN t_post_extension ext
              ON ext.post_id = t_int_content_suggestion.post_id
            WHERE t_int_content_suggestion.resolution = 'PENDING'
              AND (
                    #{cursor} = 0
                    OR t_int_content_suggestion.id < #{cursor}
                    OR (#{includeCursorId} = 1 AND t_int_content_suggestion.id = #{cursor})
                  )
            ORDER BY t_int_content_suggestion.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listPendingSuggestionIssues(@Param("cursor") long cursor,
                                               @Param("includeCursorId") boolean includeCursorId,
                                               @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS issueCount,
                   MIN(issueAt) AS oldestIssueAt
            FROM (
                SELECT update_time AS issueAt
                FROM t_post_reference
                WHERE reference_status = 'BROKEN'
                  AND is_deleted = 0
                ORDER BY update_time, id
                LIMIT #{cap}
            ) bounded
            """)
    KnowledgeLifecycleSourceHealthRow selectBrokenReferenceHealth(@Param("cap") int cap);

    @Select("""
            SELECT t_post_reference.id AS issueId,
                   'POST_REFERENCE_BROKEN' AS issueType,
                   'HIGH' AS severity,
                   'POST_REFERENCE' AS subjectType,
                   CAST(t_post_reference.id AS CHAR) AS subjectId,
                   CONCAT('文章引用已标记失效：', t_post_reference.title,
                          COALESCE(CONCAT('；原因：', t_post_reference.broken_reason), '')) AS summary,
                   t_post_reference.update_time AS detectedAt,
                   CASE
                       WHEN host_post.id IS NOT NULL
                        AND host_post.is_deleted = 0
                        AND host_post.post_status = 1
                        AND host_post.visibility = 1
                        AND host_post.content_environment = 'COMMUNITY'
                           THEN t_post_reference.post_id
                   END AS relatedPostId,
                   ext.domain AS domain
            FROM t_post_reference
            LEFT JOIN t_post_main host_post
              ON host_post.id = t_post_reference.post_id
            LEFT JOIN t_post_extension ext
              ON ext.post_id = t_post_reference.post_id
            WHERE t_post_reference.reference_status = 'BROKEN'
              AND t_post_reference.is_deleted = 0
              AND (
                    #{cursor} = 0
                    OR t_post_reference.id < #{cursor}
                    OR (#{includeCursorId} = 1 AND t_post_reference.id = #{cursor})
                  )
            ORDER BY t_post_reference.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listBrokenReferenceIssues(@Param("cursor") long cursor,
                                             @Param("includeCursorId") boolean includeCursorId,
                                             @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS issueCount,
                   MIN(issueAt) AS oldestIssueAt
            FROM (
                SELECT create_time AS issueAt
                FROM t_post_knowledge_relation
                WHERE review_status = 'PENDING'
                  AND is_deleted = 0
                ORDER BY create_time, id
                LIMIT #{cap}
            ) bounded
            """)
    KnowledgeLifecycleSourceHealthRow selectPendingKnowledgeRelationHealth(@Param("cap") int cap);

    @Select("""
            SELECT t_post_knowledge_relation.id AS issueId,
                   'KNOWLEDGE_RELATION_PENDING' AS issueType,
                   CASE t_post_knowledge_relation.risk_level
                       WHEN 'HIGH' THEN 'HIGH'
                       WHEN 'MEDIUM' THEN 'MEDIUM'
                       ELSE 'LOW'
                   END AS severity,
                   'POST_KNOWLEDGE_RELATION' AS subjectType,
                   CAST(t_post_knowledge_relation.id AS CHAR) AS subjectId,
                   CONCAT('知识关系 ', t_post_knowledge_relation.relation_type, ' 仍待审核') AS summary,
                   t_post_knowledge_relation.create_time AS detectedAt,
                   CASE
                       WHEN host_post.id IS NOT NULL
                        AND host_post.is_deleted = 0
                        AND host_post.post_status = 1
                        AND host_post.visibility = 1
                        AND host_post.content_environment = 'COMMUNITY'
                           THEN t_post_knowledge_relation.source_post_id
                   END AS relatedPostId,
                   ext.domain AS domain
            FROM t_post_knowledge_relation
            LEFT JOIN t_post_main host_post
              ON host_post.id = t_post_knowledge_relation.source_post_id
            LEFT JOIN t_post_extension ext
              ON ext.post_id = t_post_knowledge_relation.source_post_id
            WHERE t_post_knowledge_relation.review_status = 'PENDING'
              AND t_post_knowledge_relation.is_deleted = 0
              AND (
                    #{cursor} = 0
                    OR t_post_knowledge_relation.id < #{cursor}
                    OR (#{includeCursorId} = 1 AND t_post_knowledge_relation.id = #{cursor})
                  )
            ORDER BY t_post_knowledge_relation.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listPendingKnowledgeRelationIssues(@Param("cursor") long cursor,
                                                      @Param("includeCursorId") boolean includeCursorId,
                                                      @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS issueCount,
                   MIN(issueAt) AS oldestIssueAt
            FROM (
                SELECT COALESCE(relation.reviewed_at, relation.update_time, relation.create_time) AS issueAt
                FROM t_post_knowledge_relation relation
                LEFT JOIN t_post_main target_post
                  ON target_post.id = relation.target_post_id
                WHERE relation.review_status = 'APPROVED'
                  AND relation.visibility_status = 'VISIBLE'
                  AND relation.is_deleted = 0
                  AND (
                      target_post.id IS NULL
                      OR target_post.is_deleted <> 0
                      OR target_post.post_status <> 1
                      OR target_post.visibility <> 1
                      OR COALESCE(target_post.content_environment, '') <> 'COMMUNITY'
                  )
                ORDER BY issueAt, relation.id
                LIMIT #{cap}
            ) bounded
            """)
    KnowledgeLifecycleSourceHealthRow selectInvalidPublicRelationTargetHealth(@Param("cap") int cap);

    @Select("""
            SELECT relation.id AS issueId,
                   'KNOWLEDGE_RELATION_TARGET_NOT_PUBLIC' AS issueType,
                   'HIGH' AS severity,
                   'POST_KNOWLEDGE_RELATION' AS subjectType,
                   CAST(relation.id AS CHAR) AS subjectId,
                   CONCAT('公开知识关系 ', relation.relation_type,
                          ' 指向当前不可公开访问的文章 ', relation.target_post_id) AS summary,
                   COALESCE(relation.reviewed_at, relation.update_time, relation.create_time) AS detectedAt,
                   CASE
                       WHEN source_post.id IS NOT NULL
                        AND source_post.is_deleted = 0
                        AND source_post.post_status = 1
                        AND source_post.visibility = 1
                        AND source_post.content_environment = 'COMMUNITY'
                           THEN relation.source_post_id
                   END AS relatedPostId,
                   ext.domain AS domain
            FROM t_post_knowledge_relation relation
            LEFT JOIN t_post_main target_post
              ON target_post.id = relation.target_post_id
            LEFT JOIN t_post_main source_post
              ON source_post.id = relation.source_post_id
            LEFT JOIN t_post_extension ext
              ON ext.post_id = relation.source_post_id
            WHERE relation.review_status = 'APPROVED'
              AND relation.visibility_status = 'VISIBLE'
              AND relation.is_deleted = 0
              AND (
                  target_post.id IS NULL
                  OR target_post.is_deleted <> 0
                  OR target_post.post_status <> 1
                  OR target_post.visibility <> 1
                  OR COALESCE(target_post.content_environment, '') <> 'COMMUNITY'
              )
              AND (
                    #{cursor} = 0
                    OR relation.id < #{cursor}
                    OR (#{includeCursorId} = 1 AND relation.id = #{cursor})
                  )
            ORDER BY relation.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listInvalidPublicRelationTargetIssues(@Param("cursor") long cursor,
                                                        @Param("includeCursorId") boolean includeCursorId,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS issueCount,
                   MIN(issueAt) AS oldestIssueAt
            FROM (
                SELECT follow_up_at AS issueAt
                FROM t_int_post_outcome
                WHERE outcome_status = 'ACTIVE'
                  AND is_deleted = 0
                  AND follow_up_at IS NOT NULL
                  AND follow_up_at <= CURRENT_TIMESTAMP(3)
                ORDER BY follow_up_at, id
                LIMIT #{cap}
            ) bounded
            """)
    KnowledgeLifecycleSourceHealthRow selectDueOutcomeRevisitHealth(@Param("cap") int cap);

    @Select("""
            SELECT t_int_post_outcome.id AS issueId,
                   'POST_OUTCOME_REVISIT_DUE' AS issueType,
                   'MEDIUM' AS severity,
                   'POST_OUTCOME' AS subjectType,
                   CAST(t_int_post_outcome.id AS CHAR) AS subjectId,
                   CONCAT('文章结果 ', t_int_post_outcome.outcome_type, ' 已到复访时间') AS summary,
                   t_int_post_outcome.follow_up_at AS detectedAt,
                   CASE
                       WHEN host_post.id IS NOT NULL
                        AND host_post.is_deleted = 0
                        AND host_post.post_status = 1
                        AND host_post.visibility = 1
                        AND host_post.content_environment = 'COMMUNITY'
                           THEN t_int_post_outcome.post_id
                   END AS relatedPostId,
                   ext.domain AS domain
            FROM t_int_post_outcome
            LEFT JOIN t_post_main host_post
              ON host_post.id = t_int_post_outcome.post_id
            LEFT JOIN t_post_extension ext
              ON ext.post_id = t_int_post_outcome.post_id
            WHERE t_int_post_outcome.outcome_status = 'ACTIVE'
              AND t_int_post_outcome.is_deleted = 0
              AND t_int_post_outcome.follow_up_at IS NOT NULL
              AND t_int_post_outcome.follow_up_at <= CURRENT_TIMESTAMP(3)
              AND (
                    #{cursor} = 0
                    OR t_int_post_outcome.id < #{cursor}
                    OR (#{includeCursorId} = 1 AND t_int_post_outcome.id = #{cursor})
                  )
            ORDER BY t_int_post_outcome.id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listDueOutcomeRevisitIssues(@Param("cursor") long cursor,
                                              @Param("includeCursorId") boolean includeCursorId,
                                              @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) AS issueCount,
                   MIN(issueAt) AS oldestIssueAt
            FROM (
                SELECT state.update_time AS issueAt
                FROM t_int_post_trust_state state
                INNER JOIN t_post_main post ON post.id = state.post_id
                WHERE state.freshness_status IN (
                          'POSSIBLY_STALE',
                          'AWAITING_AUTHOR_CONFIRMATION'
                      )
                  AND post.is_deleted = 0
                  AND post.post_status = 1
                  AND post.visibility = 1
                  AND post.content_environment = 'COMMUNITY'
                ORDER BY state.update_time, state.post_id
                LIMIT #{cap}
            ) bounded
            """)
    KnowledgeLifecycleSourceHealthRow selectFreshnessAttentionHealth(@Param("cap") int cap);

    @Select("""
            SELECT state.post_id AS issueId,
                   CASE state.freshness_status
                       WHEN 'POSSIBLY_STALE'
                           THEN 'POST_FRESHNESS_POSSIBLY_STALE'
                       WHEN 'AWAITING_AUTHOR_CONFIRMATION'
                           THEN 'POST_FRESHNESS_AWAITING_CONFIRMATION'
                   END AS issueType,
                   CASE state.freshness_status
                       WHEN 'POSSIBLY_STALE' THEN 'MEDIUM'
                       WHEN 'AWAITING_AUTHOR_CONFIRMATION' THEN 'HIGH'
                   END AS severity,
                   'POST' AS subjectType,
                   CAST(state.post_id AS CHAR) AS subjectId,
                   CASE state.freshness_status
                       WHEN 'POSSIBLY_STALE'
                           THEN CONCAT('公开文章「', post.title, '」可能已过时')
                        WHEN 'AWAITING_AUTHOR_CONFIRMATION'
                            THEN CONCAT('公开文章「', post.title, '」等待作者确认新鲜度')
                   END AS summary,
                   state.update_time AS detectedAt,
                   state.post_id AS relatedPostId,
                   ext.domain AS domain
            FROM t_int_post_trust_state state
            INNER JOIN t_post_main post ON post.id = state.post_id
            LEFT JOIN t_post_extension ext ON ext.post_id = state.post_id
            WHERE state.freshness_status IN (
                      'POSSIBLY_STALE',
                      'AWAITING_AUTHOR_CONFIRMATION'
                  )
              AND post.is_deleted = 0
              AND post.post_status = 1
              AND post.visibility = 1
              AND post.content_environment = 'COMMUNITY'
              AND (
                    #{cursor} = 0
                    OR state.post_id < #{cursor}
                    OR (#{includeCursorId} = 1 AND state.post_id = #{cursor})
                  )
            ORDER BY state.post_id DESC
            LIMIT #{limit}
            """)
    List<IssueRow> listFreshnessAttentionIssues(@Param("cursor") long cursor,
                                                @Param("includeCursorId") boolean includeCursorId,
                                                @Param("limit") int limit);

    @Select("""
            SELECT operator_uid AS operatorUid,
                   CAST(after_json AS CHAR) AS afterJson
            FROM t_admin_audit_log
            WHERE action = 'COMMUNITY_PROJECTION_RECONCILE'
              AND resource_type = 'COMMUNITY_PROJECTION'
              AND resource_id = #{resourceId}
            ORDER BY id DESC
            LIMIT 1
            """)
    AuditReplayRow selectReconciliationAudit(@Param("resourceId") String resourceId);

    @Insert("""
            INSERT IGNORE INTO t_projection_reconcile_request (
                id, resource_id, operator_uid, projection_type,
                idempotency_key, request_fingerprint, request_status
            )
            VALUES (
                #{id}, #{resourceId}, #{operatorUid}, #{projectionType},
                #{idempotencyKey}, #{requestFingerprint}, 'PENDING'
            )
            """)
    int reserveReconciliationRequest(@Param("id") Long id,
                                     @Param("resourceId") String resourceId,
                                     @Param("operatorUid") Long operatorUid,
                                     @Param("projectionType") String projectionType,
                                     @Param("idempotencyKey") String idempotencyKey,
                                     @Param("requestFingerprint") String requestFingerprint);

    @Select("""
            SELECT operator_uid AS operatorUid,
                   projection_type AS projectionType,
                   request_fingerprint AS requestFingerprint,
                   request_status AS requestStatus,
                   CAST(result_json AS CHAR) AS resultJson
            FROM t_projection_reconcile_request
            WHERE resource_id = #{resourceId}
            FOR UPDATE
            """)
    ReconcileRequestRow lockReconciliationRequest(@Param("resourceId") String resourceId);

    @Update("""
            UPDATE t_projection_reconcile_request
            SET request_status = 'COMPLETED',
                result_json = CAST(#{resultJson} AS JSON),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE resource_id = #{resourceId}
              AND request_status = 'PENDING'
            """)
    int completeReconciliationRequest(@Param("resourceId") String resourceId,
                                      @Param("resultJson") String resultJson);
}
