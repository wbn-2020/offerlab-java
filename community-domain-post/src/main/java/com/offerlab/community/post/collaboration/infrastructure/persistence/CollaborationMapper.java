package com.offerlab.community.post.collaboration.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CollaborationMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_collab_content_need',
                't_collab_content_need_follow',
                't_collab_content_need_event',
                't_collab_content_need_claim_cycle',
                't_collab_content_need_revision',
                't_collab_series',
                't_collab_series_member',
                't_collab_series_submission',
                't_collab_series_contribution',
                't_collab_activity',
                't_collab_activity_submission',
                't_collab_curation_suggestion',
                't_collab_topic_post',
                't_collab_office_hour',
                't_collab_office_hour_reservation',
                't_collab_office_hour_feedback',
                't_collab_discussion',
                't_collab_discussion_option',
                't_collab_discussion_vote',
                't_collab_governance_case'
              )
            """)
    int existingTableCount();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND (
                (table_name = 't_collab_content_need' AND column_name = 'moderation_hidden')
                OR (table_name = 't_collab_content_need' AND column_name = 'resolution_type')
                OR (table_name = 't_collab_content_need' AND column_name = 'resolution_id')
                OR (table_name = 't_collab_content_need' AND column_name = 'submitted_by_uid')
                OR (table_name = 't_collab_content_need' AND column_name = 'submitted_at')
                OR (table_name = 't_collab_content_need' AND column_name = 'submission_resolution_type')
                OR (table_name = 't_collab_content_need' AND column_name = 'submission_resolution_id')
                OR (table_name = 't_collab_content_need' AND column_name = 'submission_note')
                OR (table_name = 't_collab_content_need' AND column_name = 'reject_reason')
                OR (table_name = 't_collab_content_need' AND column_name = 'claimed_at')
                OR (table_name = 't_collab_content_need' AND column_name = 'last_progress_at')
                OR (table_name = 't_collab_content_need_event' AND column_name = 'claimant_uid')
                OR (table_name = 't_collab_content_need_event' AND column_name = 'visibility_scope')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'id')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'need_id')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'cycle_no')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'claimant_uid')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'cycle_status')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'cycle_origin')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'claimed_at')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'last_progress_at')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'ended_at')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'end_reason')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'create_time')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'update_time')
                OR (table_name = 't_collab_content_need_claim_cycle' AND column_name = 'active_need_guard')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'id')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'need_id')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'cycle_id')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'cycle_no')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'revision_no')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'submitter_uid')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'resolution_type')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'resolution_id')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'resolution_post_id')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'revision_note')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'revision_status')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'revision_origin')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'submitted_at')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'decided_by')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'decided_at')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'decision_note')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'visibility_scope')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'create_time')
                OR (table_name = 't_collab_content_need_revision' AND column_name = 'update_time')
                OR (table_name = 't_collab_series' AND column_name = 'moderation_hidden')
                OR (table_name = 't_collab_activity' AND column_name = 'moderation_hidden')
                OR (table_name = 't_collab_discussion' AND column_name = 'moderation_hidden')
                OR (table_name = 't_collab_curation_suggestion' AND column_name = 'pending_guard')
                OR (table_name = 't_collab_curation_suggestion' AND column_name = 'result_type')
                OR (table_name = 't_collab_curation_suggestion' AND column_name = 'result_id')
                OR (table_name = 't_collab_curation_suggestion' AND column_name = 'result_status')
                OR (table_name = 't_collab_governance_case' AND column_name = 'pending_guard')
                OR (table_name = 't_collab_governance_case' AND column_name = 'appeal_guard')
                OR (table_name = 't_collab_office_hour' AND column_name = 'moderation_hidden')
                OR (table_name = 't_collab_office_hour' AND column_name = 'reserved_count')
                OR (table_name = 't_collab_office_hour_reservation' AND column_name = 'moderation_hidden')
                OR (table_name = 't_collab_office_hour_feedback' AND column_name = 'moderation_hidden')
                OR (table_name = 't_community_topic' AND column_name = 'domain')
                OR (table_name = 't_community_topic' AND column_name = 'allowed_domains')
              )
            """)
    int existingCriticalColumnCount();

    @Select("""
            SELECT COUNT(*)
            FROM t_user_account
            WHERE id = #{uid} AND is_deleted = 0
            """)
    int userExists(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT n.id,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.closed_reason AS closedReason,
                   n.submitted_by_uid AS submittedByUid,
                   n.submitted_at AS submittedAt,
                   n.submission_resolution_type AS submissionResolutionType,
                   n.submission_resolution_id AS submissionResolutionId,
                   n.submission_note AS submissionNote,
                   n.reject_reason AS rejectReason,
                   n.follower_count AS followerCount,
                   n.moderation_hidden AS hidden,
                   CASE WHEN #{viewerUid} IS NULL THEN 0 ELSE EXISTS(
                       SELECT 1 FROM t_collab_content_need_follow f
                       WHERE f.need_id = n.id AND f.uid = #{viewerUid} AND f.active = 1
                   ) END AS followed,
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE (#{cursor} = 0 OR n.id &lt; #{cursor})
              AND n.moderation_hidden = 0
              <if test="domain != null">
              AND n.domain = #{domain}
              </if>
              <if test="status != null and status != ''">
              AND n.need_status = #{status}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.NeedRow> listNeeds(@Param("domain") Integer domain,
                                               @Param("status") String status,
                                               @Param("viewerUid") Long viewerUid,
                                               @Param("cursor") long cursor,
                                               @Param("limit") int limit);

    @Select("""
            <script>
            SELECT n.id,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.closed_reason AS closedReason,
                   n.submitted_by_uid AS submittedByUid,
                   n.submitted_at AS submittedAt,
                   n.submission_resolution_type AS submissionResolutionType,
                   n.submission_resolution_id AS submissionResolutionId,
                   n.submission_note AS submissionNote,
                   n.reject_reason AS rejectReason,
                   n.follower_count AS followerCount,
                   n.moderation_hidden AS hidden,
                   EXISTS(
                       SELECT 1 FROM t_collab_content_need_follow f
                       WHERE f.need_id = n.id AND f.uid = #{uid} AND f.active = 1
                   ) AS followed,
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE n.claimed_by_uid = #{uid}
              AND n.moderation_hidden = 0
              AND (#{cursor} = 0 OR n.id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND n.need_status = #{status}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.NeedRow> listNeedsByClaimant(@Param("uid") Long uid,
                                                         @Param("status") String status,
                                                         @Param("cursor") long cursor,
                                                         @Param("limit") int limit);

    @Select("""
            <script>
            SELECT n.id,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.closed_reason AS closedReason,
                   n.submitted_by_uid AS submittedByUid,
                   n.submitted_at AS submittedAt,
                   n.submission_resolution_type AS submissionResolutionType,
                   n.submission_resolution_id AS submissionResolutionId,
                   n.submission_note AS submissionNote,
                   n.reject_reason AS rejectReason,
                   n.follower_count AS followerCount,
                   n.moderation_hidden AS hidden,
                   EXISTS(
                       SELECT 1 FROM t_collab_content_need_follow f
                       WHERE f.need_id = n.id AND f.uid = #{uid} AND f.active = 1
                   ) AS followed,
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE n.creator_uid = #{uid}
              AND n.moderation_hidden = 0
              AND (#{cursor} = 0 OR n.id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND n.need_status = #{status}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.NeedRow> listNeedsByCreator(@Param("uid") Long uid,
                                                        @Param("status") String status,
                                                        @Param("cursor") long cursor,
                                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT n.id,
                   f.id AS followId,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.closed_reason AS closedReason,
                   n.submitted_by_uid AS submittedByUid,
                   n.submitted_at AS submittedAt,
                   n.submission_resolution_type AS submissionResolutionType,
                   n.submission_resolution_id AS submissionResolutionId,
                   n.submission_note AS submissionNote,
                   n.reject_reason AS rejectReason,
                   n.follower_count AS followerCount,
                   n.moderation_hidden AS hidden,
                   1 AS followed,
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need_follow f
            JOIN t_collab_content_need n ON n.id = f.need_id
            WHERE f.uid = #{uid}
              AND f.active = 1
              AND n.moderation_hidden = 0
              AND (#{cursor} = 0 OR f.id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND n.need_status = #{status}
              </if>
            ORDER BY f.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.NeedRow> listFollowedNeeds(@Param("uid") Long uid,
                                                       @Param("status") String status,
                                                       @Param("cursor") long cursor,
                                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT n.id,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.closed_reason AS closedReason,
                   n.submitted_by_uid AS submittedByUid,
                   n.submitted_at AS submittedAt,
                   n.submission_resolution_type AS submissionResolutionType,
                   n.submission_resolution_id AS submissionResolutionId,
                   n.submission_note AS submissionNote,
                   n.reject_reason AS rejectReason,
                   n.follower_count AS followerCount,
                   n.moderation_hidden AS hidden,
                   EXISTS(
                       SELECT 1 FROM t_collab_content_need_follow f
                       WHERE f.need_id = n.id AND f.uid = #{viewerUid} AND f.active = 1
                   ) AS followed,
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE n.moderation_hidden = 0
              AND n.need_status = 'SUBMITTED'
              AND (#{cursor} = 0 OR n.id &lt; #{cursor})
              <if test="domain != null">
              AND n.domain = #{domain}
              </if>
            ORDER BY n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.NeedRow> listNeedReviewQueue(@Param("domain") Integer domain,
                                                        @Param("viewerUid") Long viewerUid,
                                                        @Param("cursor") long cursor,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT n.id,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.closed_reason AS closedReason,
                   n.submitted_by_uid AS submittedByUid,
                   n.submitted_at AS submittedAt,
                   n.submission_resolution_type AS submissionResolutionType,
                   n.submission_resolution_id AS submissionResolutionId,
                   n.submission_note AS submissionNote,
                   n.reject_reason AS rejectReason,
                   n.follower_count AS followerCount,
                   n.moderation_hidden AS hidden,
                   CASE WHEN #{viewerUid} IS NULL THEN 0 ELSE EXISTS(
                       SELECT 1 FROM t_collab_content_need_follow f
                       WHERE f.need_id = n.id AND f.uid = #{viewerUid} AND f.active = 1
                   ) END AS followed,
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE n.id = #{id}
              AND n.moderation_hidden = 0
            LIMIT 1
            """)
    CollaborationRows.NeedRow selectNeed(@Param("id") Long id, @Param("viewerUid") Long viewerUid);

    @Select("""
            SELECT id,
                   creator_uid AS creatorUid,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   content_format AS contentFormat,
                   title,
                   description,
                   acceptance_criteria AS acceptanceCriteria,
                   need_status AS status,
                   claimed_by_uid AS claimedByUid,
                   claimed_at AS claimedAt,
                   last_progress_at AS lastProgressAt,
                   merged_into_need_id AS mergedIntoNeedId,
                   resolution_type AS resolutionType,
                   resolution_id AS resolutionId,
                   resolution_post_id AS resolutionPostId,
                   closed_reason AS closedReason,
                   submitted_by_uid AS submittedByUid,
                   submitted_at AS submittedAt,
                   submission_resolution_type AS submissionResolutionType,
                   submission_resolution_id AS submissionResolutionId,
                   submission_note AS submissionNote,
                   reject_reason AS rejectReason,
                   follower_count AS followerCount,
                   0 AS followed,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_need
            WHERE source_type = #{sourceType}
              AND source_ref_id = #{sourceRefId}
            ORDER BY id ASC
            LIMIT 1
            """)
    CollaborationRows.NeedRow selectNeedBySource(@Param("sourceType") String sourceType,
                                                  @Param("sourceRefId") Long sourceRefId);

    @Select("""
            SELECT id,
                   creator_uid AS creatorUid,
                   domain,
                   source_type AS sourceType,
                   source_ref_id AS sourceRefId,
                   content_format AS contentFormat,
                   title,
                   description,
                   acceptance_criteria AS acceptanceCriteria,
                   need_status AS status,
                   claimed_by_uid AS claimedByUid,
                   claimed_at AS claimedAt,
                   last_progress_at AS lastProgressAt,
                   merged_into_need_id AS mergedIntoNeedId,
                   resolution_type AS resolutionType,
                   resolution_id AS resolutionId,
                   resolution_post_id AS resolutionPostId,
                   closed_reason AS closedReason,
                   submitted_by_uid AS submittedByUid,
                   submitted_at AS submittedAt,
                   submission_resolution_type AS submissionResolutionType,
                   submission_resolution_id AS submissionResolutionId,
                   submission_note AS submissionNote,
                   reject_reason AS rejectReason,
                   follower_count AS followerCount,
                   0 AS followed,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_need
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.NeedRow lockNeed(@Param("id") Long id);

    @Select("""
            SELECT e.id,
                   e.need_id AS needId,
                   e.event_type AS eventType,
                   e.actor_uid AS actorUid,
                   e.from_status AS fromStatus,
                   e.to_status AS toStatus,
                   e.target_type AS targetType,
                   e.target_id AS targetId,
                   CASE
                       WHEN #{includeManagers} = 1
                            OR (
                                #{viewerUid} IS NOT NULL
                                AND n.claimed_by_uid = #{viewerUid}
                                AND e.claimant_uid = #{viewerUid}
                            )
                       THEN e.note
                       ELSE NULL
                   END AS note,
                   CASE
                       WHEN #{includeManagers} = 1
                            OR (
                                #{viewerUid} IS NOT NULL
                                AND n.claimed_by_uid = #{viewerUid}
                                AND e.claimant_uid = #{viewerUid}
                            )
                       THEN e.visibility_scope
                       ELSE NULL
                   END AS visibilityScope,
                   e.create_time AS createTime
            FROM t_collab_content_need_event e
            INNER JOIN t_collab_content_need n ON n.id = e.need_id
            WHERE e.need_id = #{needId}
              AND (#{cursor} = 0 OR e.id &lt; #{cursor})
              AND (
                    e.visibility_scope = 'PUBLIC'
                    OR (
                        e.visibility_scope = 'PARTICIPANTS'
                        AND (
                            #{includeManagers} = 1
                            OR (
                                #{viewerUid} IS NOT NULL
                                AND n.claimed_by_uid = #{viewerUid}
                                AND e.claimant_uid = #{viewerUid}
                            )
                        )
                    )
                    OR (#{includeManagers} = 1 AND e.visibility_scope = 'MANAGERS')
              )
            ORDER BY e.id DESC
            LIMIT #{limit}
            """)
    List<CollaborationRows.NeedEventRow> listNeedEvents(
            @Param("needId") Long needId,
            @Param("viewerUid") Long viewerUid,
            @Param("includeManagers") int includeManagers,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("""
            SELECT c.id,
                   c.need_id AS needId,
                   c.cycle_no AS cycleNo,
                   c.claimant_uid AS claimantUid,
                   c.cycle_status AS status,
                   c.cycle_origin AS cycleOrigin,
                   c.claimed_at AS claimedAt,
                   c.last_progress_at AS lastProgressAt,
                   c.ended_at AS endedAt,
                   c.end_reason AS endReason,
                   c.create_time AS createTime,
                   c.update_time AS updateTime
            FROM t_collab_content_need_claim_cycle c
            INNER JOIN t_collab_content_need n ON n.id = c.need_id
            WHERE c.need_id = #{needId}
              AND (
                    #{includeManagers} = 1
                    OR (
                        #{viewerUid} IS NOT NULL
                        AND n.claimed_by_uid = #{viewerUid}
                        AND c.claimant_uid = #{viewerUid}
                    )
              )
            ORDER BY c.cycle_no DESC
            """)
    List<CollaborationRows.NeedClaimCycleRow> listNeedClaimCycles(
            @Param("needId") Long needId,
            @Param("viewerUid") Long viewerUid,
            @Param("includeManagers") int includeManagers);

    @Select("""
            SELECT r.id,
                   r.need_id AS needId,
                   r.cycle_id AS cycleId,
                   r.cycle_no AS cycleNo,
                   r.revision_no AS revisionNo,
                   r.submitter_uid AS submitterUid,
                   r.resolution_type AS resolutionType,
                   r.resolution_id AS resolutionId,
                   r.resolution_post_id AS resolutionPostId,
                   r.revision_note AS note,
                   r.revision_status AS status,
                   r.revision_origin AS revisionOrigin,
                   r.submitted_at AS submittedAt,
                   r.decided_by AS decidedBy,
                   r.decided_at AS decidedAt,
                   r.decision_note AS decisionNote,
                   r.visibility_scope AS visibilityScope,
                   r.create_time AS createTime,
                   r.update_time AS updateTime
            FROM t_collab_content_need_revision r
            INNER JOIN t_collab_content_need_claim_cycle c ON c.id = r.cycle_id
            INNER JOIN t_collab_content_need n ON n.id = r.need_id
            WHERE r.need_id = #{needId}
              AND (
                    r.visibility_scope = 'PUBLIC'
                    OR #{includeManagers} = 1
                    OR (
                        #{viewerUid} IS NOT NULL
                        AND n.claimed_by_uid = #{viewerUid}
                        AND c.claimant_uid = #{viewerUid}
                    )
              )
            ORDER BY r.cycle_no DESC, r.revision_no DESC
            """)
    List<CollaborationRows.NeedRevisionRow> listNeedRevisions(
            @Param("needId") Long needId,
            @Param("viewerUid") Long viewerUid,
            @Param("includeManagers") int includeManagers);

    @Select("""
            SELECT id,
                   need_id AS needId,
                   cycle_no AS cycleNo,
                   claimant_uid AS claimantUid,
                   cycle_status AS status,
                   cycle_origin AS cycleOrigin,
                   claimed_at AS claimedAt,
                   last_progress_at AS lastProgressAt,
                   ended_at AS endedAt,
                   end_reason AS endReason,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_need_claim_cycle
            WHERE need_id = #{needId}
              AND cycle_status = 'ACTIVE'
            ORDER BY cycle_no DESC
            LIMIT 1
            """)
    CollaborationRows.NeedClaimCycleRow selectCurrentNeedClaimCycle(@Param("needId") Long needId);

    @Select("""
            SELECT id,
                   need_id AS needId,
                   cycle_id AS cycleId,
                   cycle_no AS cycleNo,
                   revision_no AS revisionNo,
                   submitter_uid AS submitterUid,
                   resolution_type AS resolutionType,
                   resolution_id AS resolutionId,
                   resolution_post_id AS resolutionPostId,
                   revision_note AS note,
                   revision_status AS status,
                   revision_origin AS revisionOrigin,
                   submitted_at AS submittedAt,
                   decided_by AS decidedBy,
                   decided_at AS decidedAt,
                   decision_note AS decisionNote,
                   visibility_scope AS visibilityScope,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_content_need_revision
            WHERE need_id = #{needId}
              AND cycle_id = #{cycleId}
              AND revision_status = 'SUBMITTED'
            ORDER BY revision_no DESC
            LIMIT 1
            """)
    CollaborationRows.NeedRevisionRow selectCurrentNeedRevision(
            @Param("needId") Long needId,
            @Param("cycleId") Long cycleId);

    @Select("""
            SELECT id, uid
            FROM t_collab_content_need_follow
            WHERE need_id = #{needId}
              AND active = 1
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<CollaborationRows.NeedFollowRow> listActiveNeedFollowers(
            @Param("needId") Long needId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_collab_content_need_event(
                id, need_id, event_type, actor_uid, claimant_uid, from_status, to_status,
                target_type, target_id, note, visibility_scope, create_time
            ) VALUES (
                #{id}, #{needId}, #{eventType}, #{actorUid}, #{claimantUid}, #{fromStatus}, #{toStatus},
                #{targetType}, #{targetId}, #{note}, #{visibilityScope}, #{createTime}
            )
            """)
    int insertNeedEvent(@Param("id") Long id,
                        @Param("needId") Long needId,
                        @Param("eventType") String eventType,
                        @Param("actorUid") Long actorUid,
                        @Param("claimantUid") Long claimantUid,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("targetType") String targetType,
                        @Param("targetId") Long targetId,
                        @Param("note") String note,
                        @Param("visibilityScope") String visibilityScope,
                        @Param("createTime") LocalDateTime createTime);

    @Insert("""
            INSERT INTO t_collab_content_need(
                id, creator_uid, domain, source_type, source_ref_id, content_format,
                title, description, acceptance_criteria,
                need_status, follower_count, risk_acknowledged
            ) VALUES (
                #{id}, #{creatorUid}, #{domain}, #{sourceType}, #{sourceRefId}, #{contentFormat},
                #{title}, #{description}, #{acceptanceCriteria},
                'OPEN', 0, #{riskAcknowledged}
            )
            """)
    int insertNeed(@Param("id") Long id,
                   @Param("creatorUid") Long creatorUid,
                   @Param("domain") Integer domain,
                   @Param("sourceType") String sourceType,
                   @Param("sourceRefId") Long sourceRefId,
                   @Param("contentFormat") String contentFormat,
                   @Param("title") String title,
                   @Param("description") String description,
                   @Param("acceptanceCriteria") String acceptanceCriteria,
                   @Param("riskAcknowledged") int riskAcknowledged);

    @Update("""
            UPDATE t_collab_content_need_follow
            SET id = #{id},
                active = 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE need_id = #{needId}
              AND uid = #{uid}
              AND active = 0
            """)
    int reactivateNeedFollow(@Param("id") Long id,
                             @Param("needId") Long needId,
                             @Param("uid") Long uid);

    @Insert("""
            INSERT IGNORE INTO t_collab_content_need_follow(id, need_id, uid, active)
            VALUES (#{id}, #{needId}, #{uid}, 1)
            """)
    int insertNeedFollow(@Param("id") Long id,
                         @Param("needId") Long needId,
                         @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_content_need_follow
            SET active = 0, update_time = CURRENT_TIMESTAMP(3)
            WHERE need_id = #{needId} AND uid = #{uid} AND active = 1
            """)
    int unfollowNeed(@Param("needId") Long needId, @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_content_need
            SET follower_count = (
                SELECT COUNT(*)
                FROM t_collab_content_need_follow f
                WHERE f.need_id = t_collab_content_need.id AND f.active = 1
            )
            WHERE t_collab_content_need.id = #{needId}
            """)
    int refreshNeedFollowerCount(@Param("needId") Long needId);

    @Update("""
            UPDATE t_collab_content_need
            SET follower_count = follower_count + #{delta},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND follower_count + #{delta} >= 0
            """)
    int incrementNeedFollowerCount(@Param("needId") Long needId, @Param("delta") int delta);

    @Update("""
            UPDATE t_collab_content_need
            SET need_status = 'CLAIMED',
                claimed_by_uid = #{uid},
                claimed_at = CURRENT_TIMESTAMP(3),
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status = 'OPEN'
              AND claimed_by_uid IS NULL
            """)
    int claimNeed(@Param("needId") Long needId, @Param("uid") Long uid);

    @Insert("""
            INSERT INTO t_collab_content_need_claim_cycle(
                id, need_id, cycle_no, claimant_uid, cycle_status, cycle_origin,
                claimed_at, last_progress_at
            )
            SELECT #{id}, #{needId}, COALESCE(MAX(cycle_no), 0) + 1, #{claimantUid}, 'ACTIVE', 'CLAIM',
                   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            FROM t_collab_content_need_claim_cycle
            WHERE need_id = #{needId}
            """)
    int insertNeedClaimCycle(@Param("id") Long id,
                             @Param("needId") Long needId,
                             @Param("claimantUid") Long claimantUid);

    @Insert("""
            INSERT INTO t_collab_content_need_claim_cycle(
                id, need_id, cycle_no, claimant_uid, cycle_status, cycle_origin,
                claimed_at, last_progress_at
            )
            SELECT #{id}, #{needId}, COALESCE(MAX(cycle_no), 0) + 1, #{claimantUid},
                   'ACTIVE', 'LEGACY_CURRENT',
                   COALESCE(#{claimedAt}, CURRENT_TIMESTAMP(3)),
                   COALESCE(#{lastProgressAt}, #{claimedAt}, CURRENT_TIMESTAMP(3))
            FROM t_collab_content_need_claim_cycle
            WHERE need_id = #{needId}
            """)
    int insertLegacyCurrentNeedClaimCycle(@Param("id") Long id,
                                          @Param("needId") Long needId,
                                          @Param("claimantUid") Long claimantUid,
                                          @Param("claimedAt") LocalDateTime claimedAt,
                                          @Param("lastProgressAt") LocalDateTime lastProgressAt);

    @Update("""
            UPDATE t_collab_content_need_claim_cycle
            SET cycle_status = #{status},
                ended_at = CURRENT_TIMESTAMP(3),
                end_reason = #{reason},
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{cycleId}
              AND cycle_status = 'ACTIVE'
            """)
    int endNeedClaimCycle(@Param("cycleId") Long cycleId,
                          @Param("status") String status,
                          @Param("reason") String reason);

    @Update("""
            UPDATE t_collab_content_need_claim_cycle
            SET last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{cycleId}
              AND cycle_status = 'ACTIVE'
            """)
    int touchNeedClaimCycle(@Param("cycleId") Long cycleId);

    @Insert("""
            INSERT INTO t_collab_content_need_revision(
                id, need_id, cycle_id, cycle_no, revision_no, submitter_uid,
                resolution_type, resolution_id, resolution_post_id, revision_note,
                revision_status, revision_origin, submitted_at, visibility_scope
            )
            SELECT #{id}, #{needId}, #{cycleId}, #{cycleNo},
                   COALESCE(MAX(revision_no), 0) + 1, #{submitterUid},
                   #{resolutionType}, #{resolutionId}, #{resolutionPostId}, #{note},
                   'SUBMITTED', #{revisionOrigin},
                   COALESCE(#{submittedAt}, CURRENT_TIMESTAMP(3)), #{visibilityScope}
            FROM t_collab_content_need_revision
            WHERE cycle_id = #{cycleId}
            """)
    int insertNeedRevision(@Param("id") Long id,
                           @Param("needId") Long needId,
                           @Param("cycleId") Long cycleId,
                           @Param("cycleNo") Integer cycleNo,
                           @Param("submitterUid") Long submitterUid,
                           @Param("resolutionType") String resolutionType,
                           @Param("resolutionId") Long resolutionId,
                           @Param("resolutionPostId") Long resolutionPostId,
                           @Param("note") String note,
                           @Param("visibilityScope") String visibilityScope,
                           @Param("revisionOrigin") String revisionOrigin,
                           @Param("submittedAt") LocalDateTime submittedAt);

    @Update("""
            UPDATE t_collab_content_need_revision
            SET revision_status = #{status},
                decided_by = #{decidedBy},
                decided_at = CURRENT_TIMESTAMP(3),
                decision_note = #{decisionNote},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{revisionId}
              AND revision_status = 'SUBMITTED'
            """)
    int decideNeedRevision(@Param("revisionId") Long revisionId,
                           @Param("status") String status,
                           @Param("decidedBy") Long decidedBy,
                           @Param("decisionNote") String decisionNote);

    @Update("""
            UPDATE t_collab_content_need
            SET need_status = 'MERGED',
                merged_into_need_id = #{targetNeedId},
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status = 'OPEN'
            """)
    int mergeNeed(@Param("needId") Long needId, @Param("targetNeedId") Long targetNeedId);

    @Update("""
            UPDATE t_collab_content_need
            SET need_status = 'COMPLETED',
                resolution_type = #{resolutionType},
                resolution_id = #{resolutionId},
                resolution_post_id = #{resolutionPostId},
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
            """)
    int completeNeed(@Param("needId") Long needId,
                     @Param("resolutionType") String resolutionType,
                     @Param("resolutionId") Long resolutionId,
                     @Param("resolutionPostId") Long resolutionPostId);

    @Update("""
            UPDATE t_collab_content_need
            SET need_status = 'CLOSED',
                closed_reason = #{reason},
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status IN ('OPEN', 'CLAIMED', 'SUBMITTED')
            """)
    int closeNeed(@Param("needId") Long needId, @Param("reason") String reason);

    @Update("""
            UPDATE t_collab_content_need
            SET need_status = 'SUBMITTED',
                submitted_by_uid = #{uid},
                submitted_at = CURRENT_TIMESTAMP(3),
                submission_resolution_type = #{resolutionType},
                submission_resolution_id = #{resolutionId},
                submission_note = #{note},
                reject_reason = NULL,
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status = 'CLAIMED'
              AND claimed_by_uid = #{uid}
            """)
    int submitNeed(@Param("needId") Long needId,
                   @Param("uid") Long uid,
                   @Param("resolutionType") String resolutionType,
                   @Param("resolutionId") Long resolutionId,
                   @Param("note") String note);

    @Update("""
            <script>
            UPDATE t_collab_content_need
            SET need_status = 'CLAIMED',
                <if test="rejectReason != null">
                reject_reason = #{rejectReason},
                </if>
                last_progress_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status = 'SUBMITTED'
              <if test="submittedByUid != null">
              AND submitted_by_uid = #{submittedByUid}
              </if>
            </script>
            """)
    int revertSubmittedToClaimed(@Param("needId") Long needId,
                                 @Param("submittedByUid") Long submittedByUid,
                                 @Param("rejectReason") String rejectReason);

    @Update("""
            UPDATE t_collab_content_need
            SET need_status = 'OPEN',
                claimed_by_uid = NULL,
                claimed_at = NULL,
                last_progress_at = NULL,
                submitted_by_uid = NULL,
                submitted_at = NULL,
                submission_resolution_type = NULL,
                submission_resolution_id = NULL,
                submission_note = NULL,
                reject_reason = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{needId}
              AND need_status = 'CLAIMED'
              AND claimed_by_uid = #{uid}
            """)
    int releaseNeed(@Param("needId") Long needId, @Param("uid") Long uid);

    @Select("""
            <script>
            SELECT s.id,
                   s.owner_uid AS ownerUid,
                   s.domain,
                   s.title,
                   s.description,
                   s.submission_instructions AS submissionInstructions,
                   s.series_status AS status,
                   s.member_count AS memberCount,
                   (
                     SELECT COUNT(*)
                     FROM t_collab_series_submission x
                     JOIN t_post_main p ON p.id = x.post_id
                     WHERE x.series_id = s.id
                       AND x.review_status = 'APPROVED'
                       AND p.is_deleted = 0
                       AND p.post_status = 1
                       AND p.visibility = 1
                   ) AS postCount,
                   s.moderation_hidden AS hidden,
                   (
                     SELECT m.member_role
                     FROM t_collab_series_member m
                     WHERE m.series_id = s.id
                       AND m.uid = #{viewerUid}
                       AND m.member_status = 'ACTIVE'
                     LIMIT 1
                   ) AS currentUserRole,
                   s.create_time AS createTime,
                   s.update_time AS updateTime
            FROM t_collab_series s
            WHERE (#{cursor} = 0 OR s.id &lt; #{cursor})
              AND s.moderation_hidden = 0
              <if test="domain != null">
              AND s.domain = #{domain}
              </if>
              <if test="status != null and status != ''">
              AND s.series_status = #{status}
              </if>
            ORDER BY s.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.SeriesRow> listSeries(@Param("domain") Integer domain,
                                                  @Param("status") String status,
                                                  @Param("viewerUid") Long viewerUid,
                                                  @Param("cursor") long cursor,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT s.id,
                   s.owner_uid AS ownerUid,
                   s.domain,
                   s.title,
                   s.description,
                   s.submission_instructions AS submissionInstructions,
                   s.series_status AS status,
                   s.member_count AS memberCount,
                   (
                     SELECT COUNT(*)
                     FROM t_collab_series_submission x
                     JOIN t_post_main p ON p.id = x.post_id
                     WHERE x.series_id = s.id
                       AND x.review_status = 'APPROVED'
                       AND p.is_deleted = 0
                       AND p.post_status = 1
                       AND p.visibility = 1
                   ) AS postCount,
                   s.moderation_hidden AS hidden,
                   (
                     SELECT m.member_role
                     FROM t_collab_series_member m
                     WHERE m.series_id = s.id
                       AND m.uid = #{viewerUid}
                       AND m.member_status = 'ACTIVE'
                     LIMIT 1
                   ) AS currentUserRole,
                   s.create_time AS createTime,
                   s.update_time AS updateTime
            FROM t_collab_series s
            WHERE s.id = #{id}
              AND s.moderation_hidden = 0
            LIMIT 1
            """)
    CollaborationRows.SeriesRow selectSeries(@Param("id") Long id, @Param("viewerUid") Long viewerUid);

    @Select("""
            SELECT id,
                   owner_uid AS ownerUid,
                   domain,
                   title,
                   description,
                   submission_instructions AS submissionInstructions,
                   series_status AS status,
                   member_count AS memberCount,
                   post_count AS postCount,
                   NULL AS currentUserRole,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_series
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.SeriesRow lockSeries(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_collab_series(
                id, owner_uid, domain, title, description, submission_instructions, series_status,
                member_count, post_count, risk_acknowledged
            ) VALUES (
                #{id}, #{ownerUid}, #{domain}, #{title}, #{description}, #{submissionInstructions}, 'OPEN',
                1, 0, #{riskAcknowledged}
            )
            """)
    int insertSeries(@Param("id") Long id,
                     @Param("ownerUid") Long ownerUid,
                     @Param("domain") Integer domain,
                     @Param("title") String title,
                     @Param("description") String description,
                     @Param("submissionInstructions") String submissionInstructions,
                     @Param("riskAcknowledged") int riskAcknowledged);

    @Insert("""
            INSERT INTO t_collab_series_member(
                id, series_id, uid, member_role, member_status, added_by
            ) VALUES (
                #{id}, #{seriesId}, #{uid}, #{role}, 'ACTIVE', #{addedBy}
            )
            ON DUPLICATE KEY UPDATE
                member_role = CASE WHEN member_role = 'OWNER' THEN member_role ELSE VALUES(member_role) END,
                member_status = CASE WHEN member_role = 'OWNER' THEN member_status ELSE 'ACTIVE' END,
                added_by = VALUES(added_by),
                exited_at = NULL,
                revoked_at = NULL,
                revoked_by = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsertSeriesMember(@Param("id") Long id,
                           @Param("seriesId") Long seriesId,
                           @Param("uid") Long uid,
                           @Param("role") String role,
                           @Param("addedBy") Long addedBy);

    @Update("""
            UPDATE t_collab_series_member
            SET member_status = 'REMOVED',
                revoked_at = CURRENT_TIMESTAMP(3),
                revoked_by = #{operatorUid},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE series_id = #{seriesId}
              AND uid = #{uid}
              AND member_status = 'ACTIVE'
              AND member_role &lt;&gt; 'OWNER'
            """)
    int removeSeriesMember(@Param("seriesId") Long seriesId,
                           @Param("uid") Long uid,
                           @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_collab_series_member
            SET member_status = 'EXITED',
                exited_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE series_id = #{seriesId}
              AND uid = #{uid}
              AND member_status = 'ACTIVE'
              AND member_role &lt;&gt; 'OWNER'
            """)
    int exitSeries(@Param("seriesId") Long seriesId, @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_series
            SET member_count = (
                SELECT COUNT(*)
                FROM t_collab_series_member m
                WHERE m.series_id = t_collab_series.id AND m.member_status = 'ACTIVE'
            )
            WHERE t_collab_series.id = #{seriesId}
            """)
    int refreshSeriesMemberCount(@Param("seriesId") Long seriesId);

    @Select("""
            SELECT member_role
            FROM t_collab_series_member
            WHERE series_id = #{seriesId}
              AND uid = #{uid}
              AND member_status = 'ACTIVE'
            LIMIT 1
            """)
    String selectSeriesRole(@Param("seriesId") Long seriesId, @Param("uid") Long uid);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_series s
            WHERE s.id = #{seriesId}
              AND s.moderation_hidden = 0
              AND (
                    s.owner_uid = #{uid}
                    OR EXISTS (
                        SELECT 1
                        FROM t_collab_series_member m
                        WHERE m.series_id = s.id
                          AND m.uid = #{uid}
                          AND m.member_status = 'ACTIVE'
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM t_collab_series_contribution c
                        WHERE c.series_id = s.id
                          AND c.contributor_uid = #{uid}
                    )
                  )
            """)
    int seriesHasContributor(@Param("seriesId") Long seriesId, @Param("uid") Long uid);

    @Select("""
            SELECT id,
                   series_id AS seriesId,
                   uid,
                   member_role AS role,
                   member_status AS status,
                   added_by AS addedBy,
                   exited_at AS exitedAt,
                   revoked_at AS revokedAt,
                   revoked_by AS revokedBy,
                   create_time AS createTime
            FROM t_collab_series_member
            WHERE series_id = #{seriesId}
              AND member_status = 'ACTIVE'
              AND (#{cursor} = 0 OR id &lt; #{cursor})
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<CollaborationRows.SeriesMemberRow> listSeriesMembers(@Param("seriesId") Long seriesId,
                                                               @Param("cursor") long cursor,
                                                               @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_collab_series_submission(
                id, series_id, post_id, submitter_uid, submission_note, review_status
            ) VALUES (
                #{id}, #{seriesId}, #{postId}, #{submitterUid}, #{note}, 'PENDING'
            )
            """)
    int insertSeriesSubmission(@Param("id") Long id,
                               @Param("seriesId") Long seriesId,
                               @Param("postId") Long postId,
                               @Param("submitterUid") Long submitterUid,
                               @Param("note") String note);

    @Select("""
            <script>
            SELECT x.id,
                   x.series_id AS parentId,
                   x.post_id AS postId,
                   x.submitter_uid AS submitterUid,
                   x.submission_note AS note,
                   x.review_status AS reviewStatus,
                   x.reviewer_uid AS reviewerUid,
                   x.review_note AS reviewNote,
                   x.reviewed_at AS reviewedAt,
                   x.create_time AS createTime,
                   x.update_time AS updateTime
            FROM t_collab_series_submission x
            WHERE x.series_id = #{seriesId}
              AND (#{cursor} = 0 OR x.id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND x.review_status = #{status}
              </if>
              <if test="publicOnly == 1">
              AND EXISTS (
                  SELECT 1
                  FROM t_post_main p
                  WHERE p.id = x.post_id
                    AND p.is_deleted = 0
                    AND p.post_status = 1
                    AND p.visibility = 1
              )
              </if>
            ORDER BY x.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.SubmissionRow> listSeriesSubmissions(@Param("seriesId") Long seriesId,
                                                                @Param("status") String status,
                                                                @Param("publicOnly") int publicOnly,
                                                                @Param("cursor") long cursor,
                                                                @Param("limit") int limit);

    @Select("""
            SELECT id,
                   series_id AS parentId,
                   post_id AS postId,
                   submitter_uid AS submitterUid,
                   submission_note AS note,
                   review_status AS reviewStatus,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   reviewed_at AS reviewedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_series_submission
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.SubmissionRow lockSeriesSubmission(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_series_submission
            SET review_status = #{decision},
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND review_status = 'PENDING'
            """)
    int reviewSeriesSubmission(@Param("id") Long id,
                               @Param("decision") String decision,
                               @Param("reviewerUid") Long reviewerUid,
                               @Param("note") String note);

    @Update("""
            UPDATE t_collab_series
            SET post_count = (
                SELECT COUNT(*)
                FROM t_collab_series_submission x
                JOIN t_post_main p ON p.id = x.post_id
                WHERE x.series_id = t_collab_series.id
                  AND x.review_status = 'APPROVED'
                  AND p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
            )
            WHERE t_collab_series.id = #{seriesId}
            """)
    int refreshSeriesPostCount(@Param("seriesId") Long seriesId);

    @Insert("""
            INSERT INTO t_collab_series_contribution(
                id, series_id, post_id, contributor_uid, contribution_type,
                source_submission_id, recorded_by
            ) VALUES (
                #{id}, #{seriesId}, #{postId}, #{contributorUid}, 'POST',
                #{submissionId}, #{recordedBy}
            )
            """)
    int insertSeriesContribution(@Param("id") Long id,
                                 @Param("seriesId") Long seriesId,
                                 @Param("postId") Long postId,
                                 @Param("contributorUid") Long contributorUid,
                                 @Param("submissionId") Long submissionId,
                                 @Param("recordedBy") Long recordedBy);

    @Update("""
            UPDATE t_collab_series
            SET series_status = 'CLOSED', update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{seriesId} AND series_status = 'OPEN'
            """)
    int closeSeries(@Param("seriesId") Long seriesId);

    @Update("""
            UPDATE t_collab_series_submission
            SET review_status = 'REJECTED',
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE series_id = #{seriesId}
              AND review_status = 'PENDING'
            """)
    int rejectPendingSeriesSubmissions(@Param("seriesId") Long seriesId,
                                       @Param("reviewerUid") Long reviewerUid,
                                       @Param("note") String note);

    @Select("""
            <script>
            SELECT id,
                   owner_uid AS ownerUid,
                   domain,
                   activity_type AS activityType,
                   title,
                   description,
                   submission_rule AS submissionRule,
                   activity_status AS status,
                   result_summary AS resultSummary,
                   starts_at AS startsAt,
                   ends_at AS endsAt,
                   (
                     SELECT COUNT(*)
                     FROM t_collab_activity_submission x
                     JOIN t_post_main p ON p.id = x.post_id
                     WHERE x.activity_id = t_collab_activity.id
                       AND x.review_status = 'APPROVED'
                       AND p.is_deleted = 0
                       AND p.post_status = 1
                       AND p.visibility = 1
                   ) AS submissionCount,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_activity
            WHERE (#{cursor} = 0 OR id &lt; #{cursor})
              AND moderation_hidden = 0
              AND (activity_status &lt;&gt; 'DRAFT' OR owner_uid = #{viewerUid})
              <if test="domain != null">
              AND domain = #{domain}
              </if>
              <if test="status != null and status != ''">
              AND activity_status = #{status}
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.ActivityRow> listActivities(@Param("domain") Integer domain,
                                                        @Param("status") String status,
                                                        @Param("viewerUid") Long viewerUid,
                                                        @Param("cursor") long cursor,
                                                        @Param("limit") int limit);

    @Select("""
            SELECT id,
                   owner_uid AS ownerUid,
                   domain,
                   activity_type AS activityType,
                   title,
                   description,
                   submission_rule AS submissionRule,
                   activity_status AS status,
                   result_summary AS resultSummary,
                   starts_at AS startsAt,
                   ends_at AS endsAt,
                   (
                     SELECT COUNT(*)
                     FROM t_collab_activity_submission x
                     JOIN t_post_main p ON p.id = x.post_id
                     WHERE x.activity_id = t_collab_activity.id
                       AND x.review_status = 'APPROVED'
                       AND p.is_deleted = 0
                       AND p.post_status = 1
                       AND p.visibility = 1
                   ) AS submissionCount,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_activity
            WHERE id = #{id}
              AND moderation_hidden = 0
            LIMIT 1
            """)
    CollaborationRows.ActivityRow selectActivity(@Param("id") Long id);

    @Select("""
            SELECT id,
                   owner_uid AS ownerUid,
                   domain,
                   activity_type AS activityType,
                   title,
                   description,
                   submission_rule AS submissionRule,
                   activity_status AS status,
                   result_summary AS resultSummary,
                   starts_at AS startsAt,
                   ends_at AS endsAt,
                   submission_count AS submissionCount,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_activity
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.ActivityRow lockActivity(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_collab_activity(
                id, owner_uid, domain, activity_type, title, description, submission_rule,
                activity_status, starts_at, ends_at, submission_count, risk_acknowledged
            ) VALUES (
                #{id}, #{ownerUid}, #{domain}, #{activityType}, #{title}, #{description}, #{submissionRule},
                'DRAFT', #{startsAt}, #{endsAt}, 0, #{riskAcknowledged}
            )
            """)
    int insertActivity(@Param("id") Long id,
                       @Param("ownerUid") Long ownerUid,
                       @Param("domain") Integer domain,
                       @Param("activityType") String activityType,
                       @Param("title") String title,
                       @Param("description") String description,
                       @Param("submissionRule") String submissionRule,
                       @Param("startsAt") LocalDateTime startsAt,
                       @Param("endsAt") LocalDateTime endsAt,
                       @Param("riskAcknowledged") int riskAcknowledged);

    @Update("""
            UPDATE t_collab_activity
            SET activity_status = #{status}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND activity_status &lt;&gt; #{status}
            """)
    int updateActivityStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            UPDATE t_collab_activity
            SET result_summary = #{summary},
                activity_status = 'SUMMARIZED',
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND activity_status IN ('REVIEWING', 'SUMMARIZED')
            """)
    int summarizeActivity(@Param("id") Long id, @Param("summary") String summary);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_activity_submission
            WHERE activity_id = #{activityId}
              AND review_status = 'PENDING'
            """)
    int countPendingActivitySubmissions(@Param("activityId") Long activityId);

    @Update("""
            UPDATE t_collab_activity_submission
            SET review_status = 'REJECTED',
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE activity_id = #{activityId}
              AND review_status = 'PENDING'
            """)
    int rejectPendingActivitySubmissions(@Param("activityId") Long activityId,
                                         @Param("reviewerUid") Long reviewerUid,
                                         @Param("note") String note);

    @Insert("""
            INSERT INTO t_collab_activity_submission(
                id, activity_id, post_id, submitter_uid, submission_note, review_status
            ) VALUES (
                #{id}, #{activityId}, #{postId}, #{submitterUid}, #{note}, 'PENDING'
            )
            """)
    int insertActivitySubmission(@Param("id") Long id,
                                 @Param("activityId") Long activityId,
                                 @Param("postId") Long postId,
                                 @Param("submitterUid") Long submitterUid,
                                 @Param("note") String note);

    @Select("""
            <script>
            SELECT x.id,
                   x.activity_id AS parentId,
                   x.post_id AS postId,
                   x.submitter_uid AS submitterUid,
                   x.submission_note AS note,
                   x.review_status AS reviewStatus,
                   x.reviewer_uid AS reviewerUid,
                   x.review_note AS reviewNote,
                   x.reviewed_at AS reviewedAt,
                   x.create_time AS createTime,
                   x.update_time AS updateTime
            FROM t_collab_activity_submission x
            WHERE x.activity_id = #{activityId}
              AND (#{cursor} = 0 OR x.id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND x.review_status = #{status}
              </if>
              <if test="publicOnly == 1">
              AND EXISTS (
                  SELECT 1
                  FROM t_post_main p
                  WHERE p.id = x.post_id
                    AND p.is_deleted = 0
                    AND p.post_status = 1
                    AND p.visibility = 1
              )
              </if>
            ORDER BY x.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.SubmissionRow> listActivitySubmissions(@Param("activityId") Long activityId,
                                                                  @Param("status") String status,
                                                                  @Param("publicOnly") int publicOnly,
                                                                  @Param("cursor") long cursor,
                                                                  @Param("limit") int limit);

    @Select("""
            SELECT id,
                   activity_id AS parentId,
                   post_id AS postId,
                   submitter_uid AS submitterUid,
                   submission_note AS note,
                   review_status AS reviewStatus,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   reviewed_at AS reviewedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_activity_submission
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.SubmissionRow lockActivitySubmission(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_activity_submission
            SET review_status = #{decision},
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND review_status = 'PENDING'
            """)
    int reviewActivitySubmission(@Param("id") Long id,
                                 @Param("decision") String decision,
                                 @Param("reviewerUid") Long reviewerUid,
                                 @Param("note") String note);

    @Update("""
            UPDATE t_collab_activity
            SET submission_count = (
                SELECT COUNT(*)
                FROM t_collab_activity_submission x
                JOIN t_post_main p ON p.id = x.post_id
                WHERE x.activity_id = t_collab_activity.id
                  AND x.review_status = 'APPROVED'
                  AND p.is_deleted = 0
                  AND p.post_status = 1
                  AND p.visibility = 1
            )
            WHERE t_collab_activity.id = #{activityId}
            """)
    int refreshActivitySubmissionCount(@Param("activityId") Long activityId);

    @Select("""
            SELECT p.id,
                   p.author_id AS authorUid,
                   p.post_type AS postType,
                   e.domain,
                   p.visibility,
                   p.post_status AS status,
                   p.is_deleted AS deleted
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.id = #{postId}
            LIMIT 1
            """)
    CollaborationRows.PostRefRow selectPostRef(@Param("postId") Long postId);

    @Select("""
            SELECT id,
                   topic_name AS topicName,
                   domain,
                   allowed_domains AS allowedDomains,
                   topic_status AS status,
                   is_deleted AS deleted
            FROM t_community_topic
            WHERE id = #{topicId}
            LIMIT 1
            FOR UPDATE
            """)
    CollaborationRows.TopicRefRow lockTopicRef(@Param("topicId") Long topicId);

    @Select("""
            SELECT COUNT(*)
            FROM t_community_topic
            WHERE id = #{topicId}
              AND topic_status = 1
              AND is_deleted = 0
              AND (
                    (domain IS NULL AND (allowed_domains IS NULL OR allowed_domains = ''))
                    OR domain = #{domain}
                    OR FIND_IN_SET(CAST(#{domain} AS CHAR), allowed_domains) > 0
                  )
            """)
    int topicAllowsDomain(@Param("topicId") Long topicId, @Param("domain") Integer domain);

    @Insert("""
            INSERT INTO t_collab_curation_suggestion(
                id, topic_id, post_id, submitter_uid, domain, rationale, suggestion_type, review_status
            ) VALUES (
                #{id}, #{topicId}, #{postId}, #{submitterUid}, #{domain}, #{rationale}, #{suggestionType}, 'PENDING'
            )
            """)
    int insertCurationSuggestion(@Param("id") Long id,
                                 @Param("topicId") Long topicId,
                                 @Param("postId") Long postId,
                                 @Param("submitterUid") Long submitterUid,
                                 @Param("domain") Integer domain,
                                 @Param("rationale") String rationale,
                                 @Param("suggestionType") String suggestionType);

    @Select("""
            <script>
            SELECT s.id,
                   s.topic_id AS topicId,
                   t.topic_name AS topicName,
                   s.post_id AS postId,
                   s.submitter_uid AS submitterUid,
                   s.domain,
                   s.rationale,
                   s.suggestion_type AS suggestionType,
                   s.review_status AS reviewStatus,
                   s.reviewer_uid AS reviewerUid,
                   s.review_note AS reviewNote,
                   s.reviewed_at AS reviewedAt,
                   s.result_type AS resultType,
                   s.result_id AS resultId,
                   s.result_status AS resultStatus,
                   s.create_time AS createTime,
                   s.update_time AS updateTime
            FROM t_collab_curation_suggestion s
            JOIN t_community_topic t ON t.id = s.topic_id
            WHERE (#{cursor} = 0 OR s.id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND s.review_status = #{status}
              </if>
              <if test="domain != null">
              AND s.domain = #{domain}
              </if>
              <if test="submitterUid != null">
              AND s.submitter_uid = #{submitterUid}
              </if>
            ORDER BY s.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.CurationRow> listCurationSuggestions(@Param("status") String status,
                                                                 @Param("domain") Integer domain,
                                                                 @Param("submitterUid") Long submitterUid,
                                                                 @Param("cursor") long cursor,
                                                                 @Param("limit") int limit);

    @Select("""
            SELECT s.id,
                   s.topic_id AS topicId,
                   t.topic_name AS topicName,
                   s.post_id AS postId,
                   s.submitter_uid AS submitterUid,
                   s.domain,
                   s.rationale,
                   s.suggestion_type AS suggestionType,
                   s.review_status AS reviewStatus,
                   s.reviewer_uid AS reviewerUid,
                   s.review_note AS reviewNote,
                   s.reviewed_at AS reviewedAt,
                   s.result_type AS resultType,
                   s.result_id AS resultId,
                   s.result_status AS resultStatus,
                   s.create_time AS createTime,
                   s.update_time AS updateTime
            FROM t_collab_curation_suggestion s
            JOIN t_community_topic t ON t.id = s.topic_id
            WHERE s.id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.CurationRow lockCurationSuggestion(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_curation_suggestion
            SET review_status = #{decision},
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                result_type = #{resultType},
                result_id = #{resultId},
                result_status = #{resultStatus},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND review_status = 'PENDING'
            """)
    int reviewCurationSuggestion(@Param("id") Long id,
                                 @Param("decision") String decision,
                                 @Param("reviewerUid") Long reviewerUid,
                                 @Param("note") String note,
                                 @Param("resultType") String resultType,
                                 @Param("resultId") Long resultId,
                                 @Param("resultStatus") String resultStatus);

    @Insert("""
            INSERT INTO t_collab_topic_post(
                id, topic_id, post_id, source_type, source_id, recorded_by
            ) VALUES (
                #{id}, #{topicId}, #{postId}, 'CURATION', #{sourceId}, #{recordedBy}
            )
            """)
    int insertTopicPostResult(@Param("id") Long id,
                              @Param("topicId") Long topicId,
                              @Param("postId") Long postId,
                              @Param("sourceId") Long sourceId,
                              @Param("recordedBy") Long recordedBy);

    @Select("""
            SELECT id
            FROM t_collab_topic_post
            WHERE topic_id = #{topicId} AND post_id = #{postId}
            LIMIT 1
            """)
    Long selectTopicPostResultId(@Param("topicId") Long topicId, @Param("postId") Long postId);

    @Select("""
            <script>
            SELECT d.id,
                   d.creator_uid AS creatorUid,
                   d.source_post_id AS sourcePostId,
                   d.domain,
                   d.title,
                   d.prompt,
                   d.discussion_status AS status,
                   d.summary,
                   d.consensus_state AS consensusState,
                   d.author_follow_up AS authorFollowUp,
                   d.followed_up_by AS followedUpBy,
                   d.followed_up_at AS followedUpAt,
                   d.summarized_by AS summarizedBy,
                   d.summarized_at AS summarizedAt,
                   d.vote_count AS voteCount,
                   d.moderation_hidden AS hidden,
                   d.create_time AS createTime,
                   d.update_time AS updateTime
            FROM t_collab_discussion d
            JOIN t_post_main p ON p.id = d.source_post_id
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            WHERE (#{cursor} = 0 OR d.id &lt; #{cursor})
              AND d.moderation_hidden = 0
              <if test="domain != null">
              AND d.domain = #{domain}
              </if>
              <if test="status != null and status != ''">
              AND d.discussion_status = #{status}
              </if>
              <if test="postId != null">
              AND d.source_post_id = #{postId}
              </if>
            ORDER BY d.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.DiscussionRow> listDiscussions(@Param("domain") Integer domain,
                                                           @Param("status") String status,
                                                           @Param("postId") Long postId,
                                                           @Param("cursor") long cursor,
                                                           @Param("limit") int limit);

    @Select("""
            SELECT d.id,
                   d.creator_uid AS creatorUid,
                   d.source_post_id AS sourcePostId,
                   d.domain,
                   d.title,
                   d.prompt,
                   d.discussion_status AS status,
                   d.summary,
                   d.consensus_state AS consensusState,
                   d.author_follow_up AS authorFollowUp,
                   d.followed_up_by AS followedUpBy,
                   d.followed_up_at AS followedUpAt,
                   d.summarized_by AS summarizedBy,
                   d.summarized_at AS summarizedAt,
                   d.vote_count AS voteCount,
                   d.moderation_hidden AS hidden,
                   d.create_time AS createTime,
                   d.update_time AS updateTime
            FROM t_collab_discussion d
            JOIN t_post_main p ON p.id = d.source_post_id
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            WHERE d.id = #{id}
              AND d.moderation_hidden = 0
            LIMIT 1
            """)
    CollaborationRows.DiscussionRow selectDiscussion(@Param("id") Long id);

    @Select("""
            SELECT id,
                   creator_uid AS creatorUid,
                   source_post_id AS sourcePostId,
                   domain,
                   title,
                   prompt,
                   discussion_status AS status,
                   summary,
                   consensus_state AS consensusState,
                   author_follow_up AS authorFollowUp,
                   followed_up_by AS followedUpBy,
                   followed_up_at AS followedUpAt,
                   summarized_by AS summarizedBy,
                   summarized_at AS summarizedAt,
                   vote_count AS voteCount,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_discussion
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.DiscussionRow lockDiscussion(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_collab_discussion(
                id, creator_uid, source_post_id, domain, title, prompt,
                discussion_status, vote_count, risk_acknowledged
            ) VALUES (
                #{id}, #{creatorUid}, #{postId}, #{domain}, #{title}, #{prompt},
                'OPEN', 0, #{riskAcknowledged}
            )
            """)
    int insertDiscussion(@Param("id") Long id,
                         @Param("creatorUid") Long creatorUid,
                         @Param("postId") Long postId,
                         @Param("domain") Integer domain,
                         @Param("title") String title,
                         @Param("prompt") String prompt,
                         @Param("riskAcknowledged") int riskAcknowledged);

    @Insert("""
            INSERT INTO t_collab_discussion_option(
                id, discussion_id, option_text, sort_order, vote_count
            ) VALUES (
                #{id}, #{discussionId}, #{text}, #{sortOrder}, 0
            )
            """)
    int insertDiscussionOption(@Param("id") Long id,
                               @Param("discussionId") Long discussionId,
                               @Param("text") String text,
                               @Param("sortOrder") int sortOrder);

    @Select("""
            SELECT id,
                   discussion_id AS discussionId,
                   option_text AS text,
                   sort_order AS sortOrder,
                   vote_count AS voteCount
            FROM t_collab_discussion_option
            WHERE discussion_id = #{discussionId}
            ORDER BY sort_order ASC, id ASC
            LIMIT 10
            """)
    List<CollaborationRows.DiscussionOptionRow> listDiscussionOptions(@Param("discussionId") Long discussionId);

    @Select("""
            <script>
            SELECT id,
                   discussion_id AS discussionId,
                   option_text AS text,
                   sort_order AS sortOrder,
                   vote_count AS voteCount
            FROM t_collab_discussion_option
            WHERE discussion_id IN
            <foreach collection="discussionIds" item="discussionId" open="(" separator="," close=")">
                #{discussionId}
            </foreach>
            ORDER BY discussion_id ASC, sort_order ASC, id ASC
            </script>
            """)
    List<CollaborationRows.DiscussionOptionRow> listDiscussionOptionsByDiscussionIds(
            @Param("discussionIds") List<Long> discussionIds);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_discussion_option
            WHERE discussion_id = #{discussionId} AND id = #{optionId}
            """)
    int optionBelongsToDiscussion(@Param("discussionId") Long discussionId, @Param("optionId") Long optionId);

    @Select("""
            SELECT option_id
            FROM t_collab_discussion_vote
            WHERE discussion_id = #{discussionId} AND uid = #{uid}
            LIMIT 1
            """)
    Long selectVoteOption(@Param("discussionId") Long discussionId, @Param("uid") Long uid);

    @Select("""
            <script>
            SELECT discussion_id AS discussionId,
                   option_id AS optionId
            FROM t_collab_discussion_vote
            WHERE uid = #{uid}
              AND discussion_id IN
            <foreach collection="discussionIds" item="discussionId" open="(" separator="," close=")">
                #{discussionId}
            </foreach>
            </script>
            """)
    List<CollaborationRows.DiscussionVoteRow> listDiscussionVotes(
            @Param("discussionIds") List<Long> discussionIds,
            @Param("uid") Long uid);

    @Insert("""
            INSERT INTO t_collab_discussion_vote(id, discussion_id, option_id, uid)
            VALUES (#{id}, #{discussionId}, #{optionId}, #{uid})
            """)
    int insertDiscussionVote(@Param("id") Long id,
                             @Param("discussionId") Long discussionId,
                             @Param("optionId") Long optionId,
                             @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_discussion_vote
            SET option_id = #{optionId}, update_time = CURRENT_TIMESTAMP(3)
            WHERE discussion_id = #{discussionId} AND uid = #{uid}
            """)
    int updateDiscussionVote(@Param("discussionId") Long discussionId,
                             @Param("optionId") Long optionId,
                             @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_discussion_option
            SET vote_count = (
                SELECT COUNT(*)
                FROM t_collab_discussion_vote v
                WHERE v.option_id = t_collab_discussion_option.id
            )
            WHERE t_collab_discussion_option.discussion_id = #{discussionId}
            """)
    int refreshOptionVoteCounts(@Param("discussionId") Long discussionId);

    @Update("""
            UPDATE t_collab_discussion
            SET vote_count = (
                SELECT COUNT(*)
                FROM t_collab_discussion_vote v
                WHERE v.discussion_id = t_collab_discussion.id
            ),
            update_time = CURRENT_TIMESTAMP(3)
            WHERE t_collab_discussion.id = #{discussionId}
            """)
    int refreshDiscussionVoteCount(@Param("discussionId") Long discussionId);

    @Update("""
            UPDATE t_collab_discussion_option
            SET vote_count = vote_count + #{delta}
            WHERE id = #{optionId}
              AND vote_count + #{delta} >= 0
            """)
    int incrementDiscussionOptionVoteCount(@Param("optionId") Long optionId, @Param("delta") int delta);

    @Update("""
            UPDATE t_collab_discussion
            SET vote_count = vote_count + #{delta},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{discussionId}
              AND vote_count + #{delta} >= 0
            """)
    int incrementDiscussionVoteCount(@Param("discussionId") Long discussionId, @Param("delta") int delta);

    @Update("""
            UPDATE t_collab_discussion
            SET summary = #{summary},
                consensus_state = #{consensusState},
                author_follow_up = #{authorFollowUp},
                followed_up_by = CASE WHEN #{authorFollowUp} IS NULL THEN followed_up_by ELSE #{uid} END,
                followed_up_at = CASE WHEN #{authorFollowUp} IS NULL THEN followed_up_at ELSE CURRENT_TIMESTAMP(3) END,
                discussion_status = #{status},
                summarized_by = #{uid},
                summarized_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{discussionId}
              AND discussion_status IN ('OPEN', 'SUMMARIZED')
            """)
    int summarizeDiscussion(@Param("discussionId") Long discussionId,
                            @Param("summary") String summary,
                            @Param("consensusState") String consensusState,
                            @Param("authorFollowUp") String authorFollowUp,
                            @Param("status") String status,
                            @Param("uid") Long uid);

    @Select("""
            <script>
            SELECT id,
                   host_uid AS hostUid,
                   domain,
                   title,
                   description,
                   topic_guidance AS topicGuidance,
                   starts_at AS startsAt,
                   ends_at AS endsAt,
                   capacity,
                   reserved_count AS reservedCount,
                   CASE
                     WHEN office_hour_status = 'OPEN' AND ends_at &lt;= CURRENT_TIMESTAMP(3) THEN 'CLOSED'
                     ELSE office_hour_status
                   END AS status,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_office_hour
            WHERE (#{cursor} = 0 OR id &lt; #{cursor})
              AND moderation_hidden = 0
              AND (office_hour_status &lt;&gt; 'DRAFT' OR host_uid = #{viewerUid})
              <if test="domain != null">
              AND domain = #{domain}
              </if>
              <if test="status != null and status != ''">
              AND (
                    (#{status} = 'OPEN' AND office_hour_status = 'OPEN'
                        AND ends_at > CURRENT_TIMESTAMP(3))
                    OR (#{status} = 'CLOSED' AND (
                        office_hour_status = 'CLOSED'
                        OR (office_hour_status = 'OPEN' AND ends_at &lt;= CURRENT_TIMESTAMP(3))
                    ))
                    OR (#{status} IN ('DRAFT', 'CANCELLED') AND office_hour_status = #{status})
                  )
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.OfficeHourRow> listOfficeHours(@Param("domain") Integer domain,
                                                           @Param("status") String status,
                                                           @Param("viewerUid") Long viewerUid,
                                                           @Param("cursor") long cursor,
                                                           @Param("limit") int limit);

    @Select("""
            SELECT id,
                   host_uid AS hostUid,
                   domain,
                   title,
                   description,
                   topic_guidance AS topicGuidance,
                   starts_at AS startsAt,
                   ends_at AS endsAt,
                   capacity,
                   reserved_count AS reservedCount,
                   CASE
                     WHEN office_hour_status = 'OPEN' AND ends_at &lt;= CURRENT_TIMESTAMP(3) THEN 'CLOSED'
                     ELSE office_hour_status
                   END AS status,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_office_hour
            WHERE id = #{id}
              AND moderation_hidden = 0
            LIMIT 1
            """)
    CollaborationRows.OfficeHourRow selectOfficeHour(@Param("id") Long id);

    @Select("""
            SELECT id,
                   host_uid AS hostUid,
                   domain,
                   title,
                   description,
                   topic_guidance AS topicGuidance,
                   starts_at AS startsAt,
                   ends_at AS endsAt,
                   capacity,
                   reserved_count AS reservedCount,
                   office_hour_status AS status,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_office_hour
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.OfficeHourRow lockOfficeHour(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_collab_office_hour(
                id, host_uid, domain, title, description, topic_guidance,
                starts_at, ends_at, capacity, reserved_count,
                office_hour_status, risk_acknowledged
            ) VALUES (
                #{id}, #{hostUid}, #{domain}, #{title}, #{description}, #{topicGuidance},
                #{startsAt}, #{endsAt}, #{capacity}, 0, 'DRAFT', #{riskAcknowledged}
            )
            """)
    int insertOfficeHour(@Param("id") Long id,
                         @Param("hostUid") Long hostUid,
                         @Param("domain") Integer domain,
                         @Param("title") String title,
                         @Param("description") String description,
                         @Param("topicGuidance") String topicGuidance,
                         @Param("startsAt") LocalDateTime startsAt,
                         @Param("endsAt") LocalDateTime endsAt,
                         @Param("capacity") Integer capacity,
                         @Param("riskAcknowledged") int riskAcknowledged);

    @Update("""
            UPDATE t_collab_office_hour
            SET office_hour_status = #{status},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND office_hour_status = #{expectedStatus}
            """)
    int updateOfficeHourStatus(@Param("id") Long id,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("status") String status);

    @Update("""
            UPDATE t_collab_office_hour
            SET reserved_count = reserved_count + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND office_hour_status = 'OPEN'
              AND moderation_hidden = 0
              AND ends_at > CURRENT_TIMESTAMP(3)
              AND reserved_count &lt; capacity
            """)
    int reserveOfficeHourCapacity(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_office_hour
            SET reserved_count = reserved_count - 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reserved_count > 0
            """)
    int releaseOfficeHourCapacity(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_office_hour
            SET reserved_count = (
                SELECT COUNT(*)
                FROM t_collab_office_hour_reservation r
                WHERE r.office_hour_id = t_collab_office_hour.id
                  AND r.reservation_status IN ('PENDING', 'ACCEPTED', 'COMPLETED')
            ),
            update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int refreshOfficeHourReservedCount(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_collab_office_hour_reservation(
                id, office_hour_id, attendee_uid, topic, context_detail,
                reservation_status, risk_acknowledged
            ) VALUES (
                #{id}, #{officeHourId}, #{attendeeUid}, #{topic}, #{contextDetail},
                'PENDING', #{riskAcknowledged}
            )
            """)
    int insertOfficeHourReservation(@Param("id") Long id,
                                    @Param("officeHourId") Long officeHourId,
                                    @Param("attendeeUid") Long attendeeUid,
                                    @Param("topic") String topic,
                                    @Param("contextDetail") String contextDetail,
                                    @Param("riskAcknowledged") int riskAcknowledged);

    @Select("""
            SELECT r.id,
                   r.office_hour_id AS officeHourId,
                   h.host_uid AS hostUid,
                   r.attendee_uid AS attendeeUid,
                   r.topic,
                   r.context_detail AS contextDetail,
                   r.reservation_status AS status,
                   r.response_note AS responseNote,
                   r.decided_by AS decidedBy,
                   r.decided_at AS decidedAt,
                   r.host_confirmed_at AS hostConfirmedAt,
                   r.attendee_confirmed_at AS attendeeConfirmedAt,
                   r.completed_at AS completedAt,
                   r.cancelled_by AS cancelledBy,
                   r.cancelled_at AS cancelledAt,
                   r.moderation_hidden AS hidden,
                   r.create_time AS createTime,
                   r.update_time AS updateTime
            FROM t_collab_office_hour_reservation r
            JOIN t_collab_office_hour h ON h.id = r.office_hour_id
            WHERE r.id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.OfficeHourReservationRow lockOfficeHourReservation(@Param("id") Long id);

    @Select("""
            SELECT office_hour_id
            FROM t_collab_office_hour_reservation
            WHERE id = #{id}
            LIMIT 1
            """)
    Long selectReservationOfficeHourId(@Param("id") Long id);

    @Select("""
            <script>
            SELECT r.id,
                   r.office_hour_id AS officeHourId,
                   h.host_uid AS hostUid,
                   r.attendee_uid AS attendeeUid,
                   r.topic,
                   r.context_detail AS contextDetail,
                   r.reservation_status AS status,
                   r.response_note AS responseNote,
                   r.decided_by AS decidedBy,
                   r.decided_at AS decidedAt,
                   r.host_confirmed_at AS hostConfirmedAt,
                   r.attendee_confirmed_at AS attendeeConfirmedAt,
                   r.completed_at AS completedAt,
                   r.cancelled_by AS cancelledBy,
                   r.cancelled_at AS cancelledAt,
                   r.moderation_hidden AS hidden,
                   r.create_time AS createTime,
                   r.update_time AS updateTime
            FROM t_collab_office_hour_reservation r
            JOIN t_collab_office_hour h ON h.id = r.office_hour_id
            WHERE (#{cursor} = 0 OR r.id &lt; #{cursor})
              AND r.moderation_hidden = 0
              <if test="officeHourId != null">
              AND r.office_hour_id = #{officeHourId}
              </if>
              <if test="attendeeUid != null">
              AND r.attendee_uid = #{attendeeUid}
              </if>
              <if test="status != null and status != ''">
              AND r.reservation_status = #{status}
              </if>
            ORDER BY r.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.OfficeHourReservationRow> listOfficeHourReservations(
            @Param("officeHourId") Long officeHourId,
            @Param("attendeeUid") Long attendeeUid,
            @Param("status") String status,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET reservation_status = #{decision},
                response_note = #{note},
                decided_by = #{uid},
                decided_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reservation_status = 'PENDING'
              AND moderation_hidden = 0
            """)
    int decideOfficeHourReservation(@Param("id") Long id,
                                    @Param("decision") String decision,
                                    @Param("note") String note,
                                    @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET reservation_status = 'CANCELLED',
                cancelled_by = #{uid},
                cancelled_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reservation_status IN ('PENDING', 'ACCEPTED')
              AND moderation_hidden = 0
            """)
    int cancelOfficeHourReservation(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET host_confirmed_at = COALESCE(host_confirmed_at, CURRENT_TIMESTAMP(3)),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reservation_status = 'ACCEPTED'
              AND moderation_hidden = 0
            """)
    int confirmOfficeHourReservationByHost(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET attendee_confirmed_at = COALESCE(attendee_confirmed_at, CURRENT_TIMESTAMP(3)),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reservation_status = 'ACCEPTED'
              AND moderation_hidden = 0
            """)
    int confirmOfficeHourReservationByAttendee(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET reservation_status = 'COMPLETED',
                completed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reservation_status = 'ACCEPTED'
              AND host_confirmed_at IS NOT NULL
              AND attendee_confirmed_at IS NOT NULL
              AND moderation_hidden = 0
            """)
    int completeOfficeHourReservationIfConfirmed(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_office_hour_reservation r
            JOIN t_collab_office_hour h ON h.id = r.office_hour_id
            SET r.reservation_status = 'EXPIRED',
                r.update_time = CURRENT_TIMESTAMP(3)
            WHERE r.office_hour_id = #{officeHourId}
              AND r.reservation_status = 'PENDING'
              AND r.moderation_hidden = 0
              AND h.ends_at &lt;= CURRENT_TIMESTAMP(3)
            """)
    int expirePendingOfficeHourReservations(@Param("officeHourId") Long officeHourId);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET reservation_status = 'EXPIRED',
                update_time = CURRENT_TIMESTAMP(3)
            WHERE office_hour_id = #{officeHourId}
              AND reservation_status = 'ACCEPTED'
              AND moderation_hidden = 0
              AND #{confirmationDeadline} &lt;= CURRENT_TIMESTAMP(3)
            """)
    int expireAcceptedOfficeHourReservationsAfterGrace(
            @Param("officeHourId") Long officeHourId,
            @Param("confirmationDeadline") LocalDateTime confirmationDeadline);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET reservation_status = 'CANCELLED',
                cancelled_by = #{uid},
                cancelled_at = CURRENT_TIMESTAMP(3),
                response_note = COALESCE(#{note}, response_note),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE office_hour_id = #{officeHourId}
              AND reservation_status IN ('PENDING', 'ACCEPTED')
              AND moderation_hidden = 0
            """)
    int cancelActiveOfficeHourReservations(@Param("officeHourId") Long officeHourId,
                                           @Param("uid") Long uid,
                                           @Param("note") String note);

    @Insert("""
            INSERT INTO t_collab_office_hour_feedback(
                id, reservation_id, office_hour_id, author_uid, target_uid, rating, feedback_text
            ) VALUES (
                #{id}, #{reservationId}, #{officeHourId}, #{authorUid}, #{targetUid}, #{rating}, #{feedback}
            )
            """)
    int insertOfficeHourFeedback(@Param("id") Long id,
                                 @Param("reservationId") Long reservationId,
                                 @Param("officeHourId") Long officeHourId,
                                 @Param("authorUid") Long authorUid,
                                 @Param("targetUid") Long targetUid,
                                 @Param("rating") Integer rating,
                                 @Param("feedback") String feedback);

    @Select("""
            SELECT id,
                   reservation_id AS reservationId,
                   office_hour_id AS officeHourId,
                   author_uid AS authorUid,
                   target_uid AS targetUid,
                   rating,
                   feedback_text AS feedback,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_office_hour_feedback
            WHERE reservation_id = #{reservationId}
              AND moderation_hidden = 0
            ORDER BY id ASC
            LIMIT 2
            """)
    List<CollaborationRows.OfficeHourFeedbackRow> listOfficeHourFeedback(
            @Param("reservationId") Long reservationId);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_office_hour_feedback
            WHERE reservation_id = #{reservationId}
              AND moderation_hidden = 0
            """)
    int countOfficeHourFeedback(@Param("reservationId") Long reservationId);

    @Select("""
            SELECT id,
                   reservation_id AS reservationId,
                   office_hour_id AS officeHourId,
                   author_uid AS authorUid,
                   target_uid AS targetUid,
                   rating,
                   feedback_text AS feedback,
                   moderation_hidden AS hidden,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_office_hour_feedback
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.OfficeHourFeedbackRow lockOfficeHourFeedback(@Param("id") Long id);

    @Insert("""
            INSERT INTO t_collab_governance_case(
                id, case_type, target_type, target_id, submitter_uid,
                parent_case_id, reason_code, detail, case_status
            ) VALUES (
                #{id}, #{caseType}, #{targetType}, #{targetId}, #{submitterUid},
                #{parentCaseId}, #{reasonCode}, #{detail}, 'PENDING'
            )
            """)
    int insertGovernanceCase(@Param("id") Long id,
                             @Param("caseType") String caseType,
                             @Param("targetType") String targetType,
                             @Param("targetId") Long targetId,
                             @Param("submitterUid") Long submitterUid,
                             @Param("parentCaseId") Long parentCaseId,
                             @Param("reasonCode") String reasonCode,
                             @Param("detail") String detail);

    @Select("""
            <script>
            SELECT id,
                   case_type AS caseType,
                   target_type AS targetType,
                   target_id AS targetId,
                   submitter_uid AS submitterUid,
                   parent_case_id AS parentCaseId,
                   reason_code AS reasonCode,
                   detail,
                   case_status AS status,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   reviewed_at AS reviewedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_governance_case
            WHERE (#{cursor} = 0 OR id &lt; #{cursor})
              <if test="status != null and status != ''">
              AND case_status = #{status}
              </if>
              <if test="submitterUid != null">
              AND submitter_uid = #{submitterUid}
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CollaborationRows.GovernanceCaseRow> listGovernanceCases(@Param("status") String status,
                                                                  @Param("submitterUid") Long submitterUid,
                                                                  @Param("cursor") long cursor,
                                                                  @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_governance_case
            WHERE submitter_uid = #{submitterUid}
              AND case_type = #{caseType}
              AND create_time >= #{since}
            """)
    int countRecentGovernanceCases(@Param("submitterUid") Long submitterUid,
                                   @Param("caseType") String caseType,
                                   @Param("since") LocalDateTime since);

    @Update("""
            UPDATE t_collab_governance_case
            SET case_status = 'OVERTURNED',
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND case_type = 'REPORT'
              AND case_status = 'UPHELD'
            """)
    int overturnGovernanceReport(@Param("id") Long id);

    @Select("""
            SELECT COUNT(*)
            FROM t_collab_governance_case
            WHERE case_type = 'REPORT'
              AND target_type = #{targetType}
              AND target_id = #{targetId}
              AND case_status = 'UPHELD'
            """)
    int countActiveUpheldReports(@Param("targetType") String targetType,
                                 @Param("targetId") Long targetId);

    @Select("""
            SELECT id,
                   case_type AS caseType,
                   target_type AS targetType,
                   target_id AS targetId,
                   submitter_uid AS submitterUid,
                   parent_case_id AS parentCaseId,
                   reason_code AS reasonCode,
                   detail,
                   case_status AS status,
                   reviewer_uid AS reviewerUid,
                   review_note AS reviewNote,
                   reviewed_at AS reviewedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_collab_governance_case
            WHERE id = #{id}
            FOR UPDATE
            """)
    CollaborationRows.GovernanceCaseRow lockGovernanceCase(@Param("id") Long id);

    @Update("""
            UPDATE t_collab_governance_case
            SET case_status = #{decision},
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND case_status = 'PENDING'
            """)
    int decideGovernanceCase(@Param("id") Long id,
                             @Param("decision") String decision,
                             @Param("reviewerUid") Long reviewerUid,
                             @Param("note") String note);

    @Update("""
            UPDATE t_collab_content_need
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setNeedHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_series
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setSeriesHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_activity
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setActivityHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_discussion
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setDiscussionHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_office_hour
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setOfficeHourHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_office_hour_reservation
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setOfficeHourReservationHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_office_hour_feedback
            SET moderation_hidden = #{hidden}, update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setOfficeHourFeedbackHidden(@Param("id") Long id, @Param("hidden") int hidden);

    @Update("""
            UPDATE t_collab_curation_suggestion
            SET review_status = #{status},
                reviewer_uid = #{reviewerUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int setCurationGovernanceStatus(@Param("id") Long id,
                                    @Param("status") String status,
                                    @Param("reviewerUid") Long reviewerUid,
                                    @Param("note") String note);
}
