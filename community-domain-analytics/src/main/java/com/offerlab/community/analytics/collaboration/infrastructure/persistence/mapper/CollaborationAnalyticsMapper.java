package com.offerlab.community.analytics.collaboration.infrastructure.persistence.mapper;

import com.offerlab.community.analytics.collaboration.infrastructure.persistence.CollaborationAnalyticsRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CollaborationAnalyticsMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_collab_content_need',
                't_collab_content_need_event',
                't_collab_content_need_follow',
                't_collab_series',
                't_collab_series_submission',
                't_collab_series_contribution',
                't_collab_activity',
                't_collab_activity_submission',
                't_collab_curation_suggestion',
                't_collab_topic_post',
                't_collab_content_maintenance_task',
                't_int_content_suggestion',
                't_post_main',
                't_post_extension'
              )
            """)
    int schemaReady();

    @Select("""
            <script>
            WITH need_fact_candidates AS (
                SELECT
                    e.need_id,
                    e.target_type,
                    e.target_id,
                    n.domain,
                    e.create_time AS occurred_at,
                    ROW_NUMBER() OVER (
                        PARTITION BY e.need_id, e.claimant_uid
                        ORDER BY e.create_time, e.id
                    ) AS fact_row
                FROM t_collab_content_need_event e
                INNER JOIN t_collab_content_need n ON n.id = e.need_id
                WHERE e.claimant_uid = #{uid}
                  AND e.event_type IN ('ACCEPTED', 'COMPLETED')
                  AND e.to_status = 'COMPLETED'
                  AND e.visibility_scope = 'PUBLIC'
                  AND n.need_status = 'COMPLETED'
                  AND n.moderation_hidden = 0
            ),
            contribution_facts AS (
                SELECT
                    'NEED_ACCEPTED' AS factType,
                    accepted.need_id AS sourceId,
                    accepted.target_type AS referenceType,
                    accepted.target_id AS referenceId,
                    accepted.domain AS domain,
                    accepted.occurred_at AS occurredAt
                FROM need_fact_candidates accepted
                WHERE accepted.fact_row = 1

                UNION ALL

                SELECT DISTINCT
                    'SERIES_SUBMISSION_ACCEPTED' AS factType,
                    c.source_submission_id AS sourceId,
                    'POST' AS referenceType,
                    c.post_id AS referenceId,
                    s.domain AS domain,
                    c.create_time AS occurredAt
                FROM t_collab_series_contribution c
                INNER JOIN t_collab_series_submission submission
                    ON submission.id = c.source_submission_id
                   AND submission.review_status = 'APPROVED'
                INNER JOIN t_collab_series s
                    ON s.id = c.series_id
                   AND s.moderation_hidden = 0
                INNER JOIN t_post_main p
                    ON p.id = c.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                WHERE c.contributor_uid = #{uid}

                UNION ALL

                SELECT DISTINCT
                    'ACTIVITY_SUBMISSION_ACCEPTED' AS factType,
                    submission.id AS sourceId,
                    'POST' AS referenceType,
                    submission.post_id AS referenceId,
                    activity.domain AS domain,
                    COALESCE(submission.reviewed_at, submission.update_time) AS occurredAt
                FROM t_collab_activity_submission submission
                INNER JOIN t_collab_activity activity
                    ON activity.id = submission.activity_id
                   AND activity.moderation_hidden = 0
                INNER JOIN t_post_main p
                    ON p.id = submission.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                WHERE submission.submitter_uid = #{uid}
                  AND submission.review_status = 'APPROVED'

                UNION ALL

                SELECT DISTINCT
                    'CURATION_SUGGESTION_ACCEPTED' AS factType,
                    suggestion.id AS sourceId,
                    'POST' AS referenceType,
                    suggestion.post_id AS referenceId,
                    suggestion.domain AS domain,
                    COALESCE(suggestion.reviewed_at, suggestion.update_time) AS occurredAt
                FROM t_collab_curation_suggestion suggestion
                INNER JOIN t_collab_topic_post relation
                    ON relation.source_type = 'CURATION'
                   AND relation.source_id = suggestion.id
                INNER JOIN t_post_main p
                    ON p.id = suggestion.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                WHERE suggestion.submitter_uid = #{uid}
                  AND suggestion.review_status = 'APPROVED'
                  AND suggestion.result_status = 'COMPLETED'

                UNION ALL

                SELECT DISTINCT
                    'CONTENT_MAINTENANCE_COMPLETED' AS factType,
                    task.id AS sourceId,
                    task.delivery_type AS referenceType,
                    CASE
                        WHEN task.delivery_type = 'SERIES' THEN task.delivery_ref_id
                        ELSE COALESCE(task.delivery_post_id, task.delivery_ref_id)
                    END AS referenceId,
                    task.domain AS domain,
                    COALESCE(task.reviewed_at, task.update_time) AS occurredAt
                FROM t_collab_content_maintenance_task task
                LEFT JOIN t_post_main delivery_post
                    ON delivery_post.id = COALESCE(task.delivery_post_id, task.delivery_ref_id)
                LEFT JOIN t_collab_series delivery_series
                    ON delivery_series.id = task.delivery_ref_id
                WHERE task.assignee_uid = #{uid}
                  AND task.task_status = 'COMPLETED'
                  AND (
                      (
                          task.delivery_type IN ('POST', 'QUESTION')
                          AND COALESCE(task.delivery_post_id, task.delivery_ref_id) IS NOT NULL
                          AND delivery_post.is_deleted = 0
                          AND delivery_post.post_status = 1
                          AND delivery_post.visibility = 1
                          AND (
                              task.delivery_type = 'POST'
                              OR (
                                  task.delivery_type = 'QUESTION'
                                  AND delivery_post.post_type = 13
                              )
                          )
                      )
                      OR (
                          task.delivery_type = 'SERIES'
                          AND task.delivery_post_id IS NULL
                          AND delivery_series.moderation_hidden = 0
                          AND delivery_series.series_status IN ('OPEN', 'CLOSED')
                          AND EXISTS (
                              SELECT 1
                              FROM t_collab_series_submission delivery_submission
                              INNER JOIN t_post_main series_post
                                  ON series_post.id = delivery_submission.post_id
                                 AND series_post.is_deleted = 0
                                 AND series_post.post_status = 1
                                 AND series_post.visibility = 1
                              WHERE delivery_submission.series_id = delivery_series.id
                                AND delivery_submission.review_status = 'APPROVED'
                          )
                      )
                  )

                UNION ALL

                SELECT DISTINCT
                    'TRUSTED_CONTENT_SUGGESTION_ACCEPTED' AS factType,
                    suggestion.id AS sourceId,
                    suggestion.suggestion_type AS referenceType,
                    suggestion.post_id AS referenceId,
                    extension.domain AS domain,
                    suggestion.decided_at AS occurredAt
                FROM t_int_content_suggestion suggestion
                INNER JOIN t_post_main p
                    ON p.id = suggestion.post_id
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                LEFT JOIN t_post_extension extension ON extension.post_id = p.id
                WHERE suggestion.submitter_uid = #{uid}
                  AND suggestion.decision IN ('ACCEPTED', 'PARTIAL_ACCEPTED', 'MERGED')
                  AND suggestion.allow_public_attribution = 1
                  AND suggestion.decided_at IS NOT NULL
            ),
            deduplicated_facts AS (
                SELECT
                    factType,
                    sourceId,
                    referenceType,
                    referenceId,
                    domain,
                    occurredAt,
                    ROW_NUMBER() OVER (
                        PARTITION BY factType, sourceId
                        ORDER BY occurredAt, referenceId
                    ) AS fact_row
                FROM contribution_facts
            )
            SELECT factType, sourceId, referenceType, referenceId, domain, occurredAt
            FROM deduplicated_facts
            WHERE fact_row = 1
            ORDER BY occurredAt DESC, sourceId DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationAnalyticsRows.ContributionFactRow> selectPublicContributionFacts(
            @Param("uid") Long uid,
            @Param("limit") int limit);

    @Select("""
            <script>
            WITH all_events AS (
                SELECT DISTINCT
                    e.id,
                    e.need_id,
                    e.event_type,
                    e.actor_uid,
                    e.claimant_uid,
                    e.from_status,
                    e.to_status,
                    e.create_time,
                    n.domain
                FROM t_collab_content_need_event e
                INNER JOIN t_collab_content_need n ON n.id = e.need_id
                WHERE e.create_time &lt; #{windowEnd}
                <if test="domain != null">
                  AND n.domain = #{domain}
                </if>
            ),
            window_events AS (
                SELECT *
                FROM all_events
                WHERE create_time &gt;= #{windowStart}
            ),
            created_needs AS (
                SELECT need_id, MIN(create_time) AS created_at
                FROM window_events
                WHERE event_type = 'CREATED'
                GROUP BY need_id
            ),
            claimed_needs AS (
                SELECT created.need_id, MIN(claimed.create_time) AS claimed_at
                FROM created_needs created
                INNER JOIN all_events claimed
                    ON claimed.need_id = created.need_id
                   AND claimed.event_type = 'CLAIMED'
                   AND claimed.create_time &gt;= created.created_at
                GROUP BY created.need_id
            ),
            submitted_needs AS (
                SELECT claimed.need_id, MIN(submitted.create_time) AS submitted_at
                FROM claimed_needs claimed
                INNER JOIN all_events submitted
                    ON submitted.need_id = claimed.need_id
                   AND submitted.event_type = 'SUBMITTED'
                   AND submitted.create_time &gt;= claimed.claimed_at
                GROUP BY claimed.need_id
            ),
            accepted_needs AS (
                SELECT submitted.need_id, MIN(accepted.create_time) AS accepted_at
                FROM submitted_needs submitted
                INNER JOIN all_events accepted
                    ON accepted.need_id = submitted.need_id
                   AND accepted.event_type IN ('ACCEPTED', 'COMPLETED')
                   AND accepted.to_status = 'COMPLETED'
                   AND accepted.create_time &gt;= submitted.submitted_at
                GROUP BY submitted.need_id
            ),
            rejected_needs AS (
                SELECT submitted.need_id, MIN(rejected.create_time) AS rejected_at
                FROM submitted_needs submitted
                INNER JOIN all_events rejected
                    ON rejected.need_id = submitted.need_id
                   AND rejected.event_type = 'REJECTED'
                   AND rejected.create_time &gt;= submitted.submitted_at
                GROUP BY submitted.need_id
            ),
            resubmitted_needs AS (
                SELECT rejected.need_id, MIN(resubmitted.create_time) AS resubmitted_at
                FROM rejected_needs rejected
                INNER JOIN all_events resubmitted
                    ON resubmitted.need_id = rejected.need_id
                   AND resubmitted.event_type = 'SUBMITTED'
                   AND resubmitted.create_time &gt; rejected.rejected_at
                GROUP BY rejected.need_id
            ),
            released_needs AS (
                SELECT claimed.need_id, MIN(released.create_time) AS released_at
                FROM claimed_needs claimed
                INNER JOIN all_events released
                    ON released.need_id = claimed.need_id
                   AND released.event_type = 'RELEASED'
                   AND released.create_time &gt;= claimed.claimed_at
                GROUP BY claimed.need_id
            ),
            reclaimed_needs AS (
                SELECT released.need_id, MIN(reclaimed.create_time) AS reclaimed_at
                FROM released_needs released
                INNER JOIN all_events reclaimed
                    ON reclaimed.need_id = released.need_id
                   AND reclaimed.event_type = 'CLAIMED'
                   AND reclaimed.create_time &gt; released.released_at
                GROUP BY released.need_id
            ),
            latest_need_events AS (
                SELECT
                    need_id,
                    event_type,
                    to_status,
                    create_time,
                    ROW_NUMBER() OVER (
                        PARTITION BY need_id
                        ORDER BY create_time DESC, id DESC
                    ) AS event_row
                FROM all_events
            ),
            active_claims AS (
                SELECT need_id, create_time
                FROM latest_need_events
                WHERE event_row = 1
                  AND to_status = 'CLAIMED'
            ),
            follow_rows AS (
                SELECT DISTINCT f.need_id, f.uid, f.create_time
                FROM t_collab_content_need_follow f
                INNER JOIN t_collab_content_need n ON n.id = f.need_id
                WHERE f.active = 1
                  AND f.create_time &gt;= #{windowStart}
                  AND f.create_time &lt; #{windowEnd}
                <if test="domain != null">
                  AND n.domain = #{domain}
                </if>
            ),
            followed_to_claimed AS (
                SELECT DISTINCT f.need_id, f.uid
                FROM follow_rows f
                INNER JOIN all_events claimed
                    ON claimed.need_id = f.need_id
                   AND claimed.event_type = 'CLAIMED'
                   AND claimed.actor_uid = f.uid
                   AND claimed.create_time &gt; f.create_time
            ),
            followed_cohort AS (
                SELECT COUNT(DISTINCT CONCAT(need_id, ':', uid)) AS followed_count
                FROM follow_rows
            ),
            maintenance_tasks AS (
                SELECT DISTINCT task.id, task.task_status
                FROM t_collab_content_maintenance_task task
                WHERE task.create_time &gt;= #{windowStart}
                  AND task.create_time &lt; #{windowEnd}
                <if test="domain != null">
                  AND task.domain = #{domain}
                </if>
            )
            SELECT
                (SELECT COUNT(*) FROM created_needs) AS createdNeedCount,
                (SELECT COUNT(*) FROM claimed_needs) AS claimedNeedCount,
                (SELECT COUNT(*) FROM submitted_needs) AS submittedNeedCount,
                (SELECT COUNT(*) FROM accepted_needs) AS acceptedNeedCount,
                (SELECT COUNT(*) FROM rejected_needs) AS rejectedNeedCount,
                (SELECT COUNT(*) FROM resubmitted_needs) AS resubmittedNeedCount,
                (SELECT COUNT(*) FROM released_needs) AS releasedNeedCount,
                (SELECT COUNT(*) FROM reclaimed_needs) AS reclaimedNeedCount,
                (SELECT COUNT(*) FROM active_claims) AS activeClaimedNeedCount,
                (SELECT COUNT(*)
                 FROM active_claims
                 WHERE create_time &lt; #{stalledBefore}) AS stalledNeedCount,
                (SELECT followed_count FROM followed_cohort) AS followedNeedCount,
                (SELECT COUNT(DISTINCT CONCAT(need_id, ':', uid))
                 FROM followed_to_claimed) AS followedToClaimedNeedCount,
                (SELECT COUNT(*) FROM maintenance_tasks) AS maintenanceTaskCount,
                (SELECT COUNT(*)
                 FROM maintenance_tasks
                 WHERE task_status = 'COMPLETED') AS completedMaintenanceTaskCount,
                (SELECT AVG(TIMESTAMPDIFF(SECOND, created_needs.created_at, claimed_needs.claimed_at))
                 FROM created_needs
                 INNER JOIN claimed_needs ON claimed_needs.need_id = created_needs.need_id) AS averageCreateToClaimSeconds,
                (SELECT AVG(TIMESTAMPDIFF(SECOND, claimed_needs.claimed_at, submitted_needs.submitted_at))
                 FROM claimed_needs
                 INNER JOIN submitted_needs ON submitted_needs.need_id = claimed_needs.need_id) AS averageClaimToSubmitSeconds,
                (SELECT AVG(TIMESTAMPDIFF(SECOND, submitted_needs.submitted_at, accepted_needs.accepted_at))
                 FROM submitted_needs
                 INNER JOIN accepted_needs ON accepted_needs.need_id = submitted_needs.need_id) AS averageSubmitToAcceptSeconds
            </script>
            """)
    CollaborationAnalyticsRows.FunnelRow selectNeedFunnel(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd,
            @Param("stalledBefore") LocalDateTime stalledBefore,
            @Param("domain") Integer domain);
}
