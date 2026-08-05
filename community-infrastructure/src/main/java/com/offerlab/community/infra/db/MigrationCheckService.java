package com.offerlab.community.infra.db;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

@Service
@RequiredArgsConstructor
public class MigrationCheckService {
    private static final String CORE_MIGRATION_PATTERN = "classpath*:db/flyway/core/V*.sql";
    private static final Pattern FLYWAY_RESOURCE_NAME =
            Pattern.compile("^V(?<version>[0-9.]+)__(?<description>[a-z0-9_]+)\\.sql$");

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.flyway.table:flyway_schema_history}")
    private String flywayHistoryTable = "flyway_schema_history";

    @Value("${spring.flyway.enabled:true}")
    private boolean flywayEnabled = true;

    public Map<String, Object> governanceStatus() {
        Map<String, Boolean> tables = new LinkedHashMap<>();
        for (String table : List.of(
                "t_admin_audit_log",
                "t_search_index_rebuild_task",
                "t_post_reference",
                "t_post_knowledge_relation",
                "t_int_post_outcome",
                "t_projection_reconcile_request",
                "t_moderation_keyword",
                "t_moderation_keyword_hit",
                "t_user_moderation_state",
                "t_user_prep_target",
                "t_interview_question",
                "t_post_report",
                "t_comment_report",
                "t_tag",
                "t_community_topic",
                "t_community_topic_tag",
                "t_community_topic_follow",
                "t_review_queue",
                "t_mock_interview_answer",
                "t_ai_extract_task",
                "t_domain_moderator",
                "t_domain_config",
                "t_growth_event",
                "t_post_version_history",
                "t_post_extension",
                "t_user_task_state",
                "t_content_assist_record",
                "t_content_series",
                "t_content_series_post",
                "t_feed_recommend_support_stat",
                "t_feed_feedback_preference",
                "t_expert_cert_application",
                "t_operation_curation_item",
                "t_operation_slot",
                "t_operation_slot_item",
                "t_operation_topic",
                "t_operation_topic_section",
                "t_int_contact_request",
                "t_user_privacy_setting",
                "t_user_subscription_preference",
                "t_int_discussion_follow",
                "t_int_favorite",
                "t_int_favorite_folder",
                "t_int_comment_quality_signal",
                "t_int_comment_helpful",
                "t_int_post_trust_state",
                "t_int_post_useful_feedback",
                "t_int_content_suggestion",
                "t_int_content_trust_profile",
                "t_int_user_revisit_item",
                "t_search_content_gap",
                "t_collab_content_need",
                "t_collab_content_need_follow",
                "t_collab_content_need_event",
                "t_collab_content_need_claim_cycle",
                "t_collab_content_need_revision",
                "t_collab_series",
                "t_collab_series_member",
                "t_collab_series_submission",
                "t_collab_series_contribution",
                "t_collab_activity",
                "t_collab_activity_submission",
                "t_collab_curation_suggestion",
                "t_collab_topic_post",
                "t_collab_office_hour",
                "t_collab_office_hour_reservation",
                "t_collab_office_hour_feedback",
                "t_collab_discussion",
                "t_collab_discussion_option",
                "t_collab_discussion_vote",
                "t_collab_governance_case",
                "t_incentive_account",
                "t_incentive_ledger",
                "t_incentive_recovery_debt",
                "t_incentive_reward_rule",
                "t_incentive_reward_batch",
                "t_incentive_reward_inbox",
                "t_incentive_invalidation_job",
                "t_incentive_reward_guard",
                "t_incentive_freeze_record",
                "t_incentive_reconciliation_run",
                "t_incentive_reconciliation_cursor",
                "t_incentive_reconciliation_item",
                "t_incentive_appeal",
                "t_incentive_risk_scan_cursor",
                "t_incentive_risk_scan_run",
                "t_incentive_risk_finding",
                "t_virtual_benefit_catalog",
                "t_virtual_benefit_order",
                "t_virtual_benefit_order_history",
                "t_virtual_benefit_entitlement",
                "t_virtual_benefit_entitlement_usage",
                "t_thank_ticket_daily",
                "t_thank_action",
                "t_incentive_domain_policy",
                "t_bounty_platform_budget_guard",
                "t_bounty_user_budget_guard",
                "t_quota_bounty",
                "t_quota_bounty_submission",
                "t_quota_bounty_appeal",
                "t_community_role_definition",
                "t_community_role_metric",
                "t_community_role_application",
                "t_community_role_grant",
                "t_community_role_grant_history"
        )) {
            tables.put(table, tableExists(table));
        }
        Map<String, Boolean> columns = new LinkedHashMap<>();
        for (String column : List.of(
                "tag_status",
                "recommended",
                "synonyms",
                "merge_target_id",
                "update_time"
        )) {
            columns.put("t_tag." + column, columnExists("t_tag", column));
        }
        putColumns(columns, "t_community_topic", List.of(
                "domain",
                "allowed_domains"
        ));
        for (String column : List.of(
                "ai_review_task_id",
                "ai_review_fallback_used",
                "ai_review_duration_ms",
                "ai_review_prompt_tokens",
                "ai_review_completion_tokens",
                "ai_review_estimated_cost_micros",
                "ai_review_error_code"
        )) {
            columns.put("t_mock_interview_answer." + column, columnExists("t_mock_interview_answer", column));
        }
        for (String column : List.of(
                "provider",
                "fallback_used",
                "duration_ms",
                "prompt_tokens",
                "completion_tokens",
                "estimated_cost_micros",
                "error_code"
        )) {
            columns.put("t_ai_extract_task." + column, columnExists("t_ai_extract_task", column));
        }
        for (String column : List.of(
                "id",
                "uid",
                "domain",
                "enabled",
                "created_by",
                "create_time",
                "update_time"
        )) {
            columns.put("t_domain_moderator." + column, columnExists("t_domain_moderator", column));
        }
        for (String column : List.of(
                "domain",
                "domain_name",
                "domain_slug",
                "description",
                "sort_order",
                "enabled",
                "risk_level",
                "posting_notice",
                "browse_notice",
                "interaction_notice",
                "created_by",
                "updated_by",
                "create_time",
                "update_time"
        )) {
            columns.put("t_domain_config." + column, columnExists("t_domain_config", column));
        }
        for (String column : List.of(
                "id",
                "event_key",
                "event_type",
                "uid",
                "domain",
                "content_id",
                "target_type",
                "target_value",
                "source_page",
                "ext_json",
                "create_time"
        )) {
            columns.put("t_growth_event." + column, columnExists("t_growth_event", column));
        }
        putColumns(columns, "t_post_version_history", List.of(
                "result_version",
                "public_update_summary",
                "impact_scope"
        ));
        columns.put("t_post_extension.domain", columnExists("t_post_extension", "domain"));
        for (String column : List.of(
                "id",
                "uid",
                "task_type",
                "task_code",
                "task_date",
                "completed",
                "complete_source",
                "complete_ref_id",
                "first_completed_time",
                "create_time",
                "update_time"
        )) {
            columns.put("t_user_task_state." + column, columnExists("t_user_task_state", column));
        }
        for (String column : List.of(
                "id",
                "uid",
                "scene",
                "provider",
                "assist_status",
                "domain",
                "content_length",
                "content_hash",
                "prompt_tokens",
                "completion_tokens",
                "estimated_cost_micros",
                "error_code",
                "create_time",
                "update_time"
        )) {
            columns.put("t_content_assist_record." + column, columnExists("t_content_assist_record", column));
        }
        for (String column : List.of(
                "id",
                "creator_uid",
                "title",
                "description",
                "domain",
                "cover_url",
                "visibility",
                "create_time",
                "update_time",
                "is_deleted"
        )) {
            columns.put("t_content_series." + column, columnExists("t_content_series", column));
        }
        for (String column : List.of(
                "id",
                "series_id",
                "post_id",
                "sort_order",
                "create_time",
                "update_time",
                "is_deleted"
        )) {
            columns.put("t_content_series_post." + column, columnExists("t_content_series_post", column));
        }
        for (String column : List.of(
                "id",
                "viewer_uid",
                "domain",
                "delivered_item_count",
                "support_hit_item_count",
                "create_time",
                "update_time"
        )) {
            columns.put("t_feed_recommend_support_stat." + column, columnExists("t_feed_recommend_support_stat", column));
        }
        putColumns(columns, "t_feed_feedback_preference", List.of(
                "id",
                "uid",
                "post_id",
                "action",
                "target_type",
                "target_id",
                "reason",
                "expires_at",
                "create_time",
                "update_time"
        ));
        for (String column : List.of(
                "id",
                "applicant_uid",
                "domain",
                "status",
                "evidence_summary",
                "evidence_links_json",
                "eligibility_passed",
                "eligibility_summary",
                "eligibility_snapshot_json",
                "risk_acknowledged",
                "risk_warning",
                "reviewer_uid",
                "review_note",
                "review_time",
                "revoked_by",
                "revoke_note",
                "revoked_time",
                "create_time",
                "update_time",
                "is_deleted",
                "active_guard"
        )) {
            columns.put("t_expert_cert_application." + column, columnExists("t_expert_cert_application", column));
        }
        putColumns(columns, "t_operation_curation_item", List.of(
                "id",
                "source_type",
                "source_id",
                "item_status",
                "sort_order",
                "note",
                "created_by",
                "updated_by",
                "create_time",
                "update_time",
                "is_deleted",
                "active_guard"
        ));
        putColumns(columns, "t_operation_slot", List.of(
                "id",
                "slot_code",
                "slot_name",
                "description",
                "slot_status",
                "sort_order",
                "default_limit",
                "starts_at",
                "ends_at",
                "preview_token",
                "current_version",
                "published_snapshot_json",
                "rollback_snapshot_json",
                "created_by",
                "updated_by",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_operation_slot_item", List.of(
                "id",
                "slot_id",
                "source_type",
                "source_id",
                "item_status",
                "sort_order",
                "note",
                "created_by",
                "updated_by",
                "create_time",
                "update_time",
                "is_deleted",
                "active_guard"
        ));
        putColumns(columns, "t_operation_topic", List.of(
                "id",
                "slug",
                "topic_name",
                "description",
                "operation_type",
                "cover_url",
                "domain",
                "topic_status",
                "sort_order",
                "starts_at",
                "ends_at",
                "preview_token",
                "current_version",
                "draft_revision",
                "published_snapshot_json",
                "rollback_snapshot_json",
                "note",
                "created_by",
                "updated_by",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_operation_topic_section", List.of(
                "id",
                "topic_id",
                "section_title",
                "section_key",
                "source_type",
                "source_id",
                "section_status",
                "sort_order",
                "note",
                "created_by",
                "updated_by",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_int_contact_request", List.of(
                "id",
                "requester_uid",
                "receiver_uid",
                "source_type",
                "source_id",
                "scene",
                "message_preview",
                "request_status",
                "receiver_action_time",
                "expire_time",
                "report_id",
                "dedup_key",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_user_privacy_setting", List.of(
                "accept_contact_request",
                "contact_request_policy",
                "contact_request_daily_limit"
        ));
        putColumns(columns, "t_user_subscription_preference", List.of(
                "id",
                "uid",
                "source_type",
                "source_id",
                "delivery_mode",
                "expires_at",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_projection_reconcile_request", List.of(
                "id",
                "resource_id",
                "operator_uid",
                "projection_type",
                "idempotency_key",
                "request_fingerprint",
                "request_status",
                "result_json",
                "create_time",
                "update_time"
        ));
        putColumns(columns, "t_search_index_rebuild_task", List.of(
                "task_id",
                "task_type",
                "task_status",
                "operator_uid",
                "checkpoint_id",
                "indexed_count",
                "failed_count",
                "total_count",
                "index_name",
                "last_error",
                "lock_owner",
                "lock_until",
                "heartbeat_time",
                "started_at",
                "finished_at",
                "active_key",
                "create_time",
                "update_time"
        ));
        putColumns(columns, "t_int_content_suggestion", List.of(
                "base_version",
                "target_scope",
                "target_locator",
                "expected_change",
                "resolution",
                "delivery_status"
        ));
        putColumns(columns, "t_post_reference", List.of(
                "id", "post_id", "owner_uid", "reference_type", "title", "url",
                "normalized_url", "source_domain", "note", "broken_reason",
                "reference_status", "sort_order", "revision", "last_confirmed_at",
                "create_time", "update_time", "is_deleted", "active_guard"
        ));
        putColumns(columns, "t_post_knowledge_relation", List.of(
                "id", "source_post_id", "target_post_id", "relation_type", "reason_text",
                "proposer_uid", "review_status", "visibility_status", "reviewer_uid",
                "review_note", "reviewed_at", "risk_level", "create_time", "update_time",
                "is_deleted", "effective_guard"
        ));
        putColumns(columns, "t_int_post_outcome", List.of(
                "id", "post_id", "uid", "outcome_type", "context_note", "result_note",
                "visibility", "publication_status", "consented_at", "reviewer_uid",
                "review_note", "reviewed_at", "follow_up_at", "outcome_status", "revision",
                "create_time", "update_time", "is_deleted", "effective_guard"
        ));
        putColumns(columns, "t_int_discussion_follow", List.of(
                "id",
                "uid",
                "post_id",
                "follow_status",
                "last_read_comment_id",
                "last_notified_comment_id",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_int_favorite", List.of(
                "folder_id",
                "sort_order",
                "update_time"
        ));
        putColumns(columns, "t_int_favorite_folder", List.of(
                "id",
                "user_id",
                "name",
                "description",
                "visibility",
                "sort_order",
                "post_count",
                "is_default",
                "is_deleted",
                "default_active_key",
                "name_active_key",
                "create_time",
                "update_time"
        ));
        columns.put("t_int_comment.helpful_count", columnExists("t_int_comment", "helpful_count"));
        putColumns(columns, "t_int_comment_quality_signal", List.of(
                "id",
                "post_id",
                "comment_id",
                "root_id",
                "signal_type",
                "signal_status",
                "operator_uid",
                "operator_role",
                "reason",
                "source",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_int_comment_helpful", List.of(
                "id",
                "uid",
                "post_id",
                "comment_id",
                "helpful_status",
                "create_time",
                "update_time",
                "is_deleted"
        ));
        putColumns(columns, "t_int_post_trust_state", List.of(
                "post_id",
                "question_status",
                "accepted_comment_id",
                "duplicate_post_id",
                "freshness_status",
                "successor_post_id",
                "last_confirmed_at",
                "suggestions_open",
                "create_time",
                "update_time"
        ));
        putColumns(columns, "t_int_post_useful_feedback", List.of(
                "id",
                "user_id",
                "post_id",
                "post_author_id",
                "reason",
                "create_time",
                "update_time"
        ));
        putColumns(columns, "t_int_content_suggestion", List.of(
                "id",
                "post_id",
                "post_author_id",
                "submitter_uid",
                "suggestion_type",
                "detail",
                "normalized_content_hash",
                "source_url",
                "allow_public_attribution",
                "decision",
                "author_reply",
                "public_note",
                "result_version",
                "pending_dedup_key",
                "pending_guard",
                "decided_at",
                "create_time",
                "update_time"
        ));
        putColumns(columns, "t_collab_content_need", List.of(
                "submitted_by_uid",
                "submitted_at",
                "submission_resolution_type",
                "submission_resolution_id",
                "submission_note",
                "reject_reason",
                "claimed_at",
                "last_progress_at"
        ));
        putColumns(columns, "t_collab_content_need_event", List.of(
                "id",
                "need_id",
                "event_type",
                "actor_uid",
                "claimant_uid",
                "from_status",
                "to_status",
                "target_type",
                "target_id",
                "note",
                "visibility_scope",
                "create_time"
        ));
        putColumns(columns, "t_collab_content_need_claim_cycle", List.of(
                "id",
                "need_id",
                "cycle_no",
                "claimant_uid",
                "cycle_status",
                "cycle_origin",
                "claimed_at",
                "last_progress_at",
                "ended_at",
                "end_reason",
                "create_time",
                "update_time",
                "active_need_guard"
        ));
        putColumns(columns, "t_collab_content_need_revision", List.of(
                "id",
                "need_id",
                "cycle_id",
                "cycle_no",
                "revision_no",
                "submitter_uid",
                "resolution_type",
                "resolution_id",
                "resolution_post_id",
                "revision_note",
                "revision_status",
                "revision_origin",
                "submitted_at",
                "decided_by",
                "decided_at",
                "decision_note",
                "visibility_scope",
                "create_time",
                "update_time"
        ));
        Map<String, Boolean> indexes = new LinkedHashMap<>();
        indexes.put("t_post_report.idx_post_reporter_status", indexExists("t_post_report", "idx_post_reporter_status"));
        indexes.put("t_comment_report.idx_comment_reporter_status", indexExists("t_comment_report", "idx_comment_reporter_status"));
        indexes.put("t_interview_question.idx_status_time", indexExists("t_interview_question", "idx_status_time"));
        indexes.put("t_tag.idx_tag_status_recommend", indexExists("t_tag", "idx_tag_status_recommend"));
        indexes.put("t_tag.idx_tag_merge_target", indexExists("t_tag", "idx_tag_merge_target"));
        indexes.put("t_community_topic.uk_topic_slug", indexExists("t_community_topic", "uk_topic_slug"));
        indexes.put("t_community_topic.idx_topic_status_sort", indexExists("t_community_topic", "idx_topic_status_sort"));
        indexes.put("t_community_topic.idx_topic_featured_sort", indexExists("t_community_topic", "idx_topic_featured_sort"));
        indexes.put("t_community_topic.idx_community_topic_domain",
                indexExists("t_community_topic", "idx_community_topic_domain"));
        indexes.put("t_community_topic_tag.uk_topic_tag", indexExists("t_community_topic_tag", "uk_topic_tag"));
        indexes.put("t_community_topic_tag.idx_topic_tag_topic", indexExists("t_community_topic_tag", "idx_topic_tag_topic"));
        indexes.put("t_community_topic_tag.idx_topic_tag_tag", indexExists("t_community_topic_tag", "idx_topic_tag_tag"));
        indexes.put("t_community_topic_follow.uk_topic_follow_user", indexExists("t_community_topic_follow", "uk_topic_follow_user"));
        indexes.put("t_community_topic_follow.idx_topic_follow_uid", indexExists("t_community_topic_follow", "idx_topic_follow_uid"));
        indexes.put("t_community_topic_follow.idx_topic_follow_topic", indexExists("t_community_topic_follow", "idx_topic_follow_topic"));
        indexes.put("t_review_queue.uk_review_queue_source", indexExists("t_review_queue", "uk_review_queue_source"));
        indexes.put("t_review_queue.idx_review_queue_status_priority", indexExists("t_review_queue", "idx_review_queue_status_priority"));
        indexes.put("t_review_queue.idx_review_queue_source_status", indexExists("t_review_queue", "idx_review_queue_source_status"));
        indexes.put("t_review_queue.idx_review_queue_assignee_status", indexExists("t_review_queue", "idx_review_queue_assignee_status"));
        indexes.put("t_review_queue.idx_review_queue_risk_status", indexExists("t_review_queue", "idx_review_queue_risk_status"));
        indexes.put("t_domain_moderator.uk_domain_moderator_uid_domain", indexExists("t_domain_moderator", "uk_domain_moderator_uid_domain"));
        indexes.put("t_domain_moderator.idx_domain_moderator_domain_enabled", indexExists("t_domain_moderator", "idx_domain_moderator_domain_enabled"));
        indexes.put("t_domain_moderator.idx_domain_moderator_uid_enabled", indexExists("t_domain_moderator", "idx_domain_moderator_uid_enabled"));
        indexes.put("t_domain_config.uk_domain_config_slug", indexExists("t_domain_config", "uk_domain_config_slug"));
        indexes.put("t_domain_config.idx_domain_config_enabled_sort", indexExists("t_domain_config", "idx_domain_config_enabled_sort"));
        indexes.put("t_domain_config.idx_domain_config_risk_enabled", indexExists("t_domain_config", "idx_domain_config_risk_enabled"));
        indexes.put("t_growth_event.idx_growth_event_type_time", indexExists("t_growth_event", "idx_growth_event_type_time"));
        indexes.put("t_growth_event.idx_growth_event_domain_time", indexExists("t_growth_event", "idx_growth_event_domain_time"));
        indexes.put("t_growth_event.idx_growth_event_uid_time", indexExists("t_growth_event", "idx_growth_event_uid_time"));
        indexes.put("t_growth_event.idx_growth_event_content_time", indexExists("t_growth_event", "idx_growth_event_content_time"));
        indexes.put("t_growth_event.uk_growth_event_key", indexExists("t_growth_event", "uk_growth_event_key"));
        putIndexes(indexes, "t_post_version_history", List.of(
                "uk_post_result_version",
                "idx_post_public_update"
        ));
        indexes.put("t_post_extension.idx_post_extension_domain_post", indexExists("t_post_extension", "idx_post_extension_domain_post"));
        indexes.put("t_user_task_state.uk_user_task_scope_code_day", indexExists("t_user_task_state", "uk_user_task_scope_code_day"));
        indexes.put("t_user_task_state.idx_user_task_scope_date", indexExists("t_user_task_state", "idx_user_task_scope_date"));
        indexes.put("t_content_assist_record.idx_content_assist_scene_time", indexExists("t_content_assist_record", "idx_content_assist_scene_time"));
        indexes.put("t_content_assist_record.idx_content_assist_status_time", indexExists("t_content_assist_record", "idx_content_assist_status_time"));
        indexes.put("t_content_assist_record.idx_content_assist_provider_time", indexExists("t_content_assist_record", "idx_content_assist_provider_time"));
        indexes.put("t_content_assist_record.idx_content_assist_uid_time", indexExists("t_content_assist_record", "idx_content_assist_uid_time"));
        indexes.put("t_content_series.idx_content_series_creator_update", indexExists("t_content_series", "idx_content_series_creator_update"));
        indexes.put("t_content_series.idx_content_series_domain_update", indexExists("t_content_series", "idx_content_series_domain_update"));
        indexes.put("t_content_series.idx_content_series_public_creator", indexExists("t_content_series", "idx_content_series_public_creator"));
        indexes.put("t_content_series_post.uk_content_series_post", indexExists("t_content_series_post", "uk_content_series_post"));
        indexes.put("t_content_series_post.idx_content_series_post_series_sort", indexExists("t_content_series_post", "idx_content_series_post_series_sort"));
        indexes.put("t_content_series_post.idx_content_series_post_post", indexExists("t_content_series_post", "idx_content_series_post_post"));
        indexes.put("t_feed_recommend_support_stat.idx_feed_recommend_support_stat_create_time", indexExists("t_feed_recommend_support_stat", "idx_feed_recommend_support_stat_create_time"));
        indexes.put("t_feed_recommend_support_stat.idx_feed_recommend_support_stat_domain_create_time", indexExists("t_feed_recommend_support_stat", "idx_feed_recommend_support_stat_domain_create_time"));
        indexes.put("t_feed_recommend_support_stat.idx_feed_recommend_support_stat_viewer_create_time", indexExists("t_feed_recommend_support_stat", "idx_feed_recommend_support_stat_viewer_create_time"));
        putIndexes(indexes, "t_feed_feedback_preference", List.of(
                "uk_feed_feedback_uid_post",
                "idx_feed_feedback_uid_action_active",
                "idx_feed_feedback_uid_target_active",
                "idx_feed_feedback_uid_cursor"
        ));
        indexes.put("t_expert_cert_application.uk_expert_cert_active_guard", indexExists("t_expert_cert_application", "uk_expert_cert_active_guard"));
        indexes.put("t_expert_cert_application.idx_expert_cert_applicant_domain", indexExists("t_expert_cert_application", "idx_expert_cert_applicant_domain"));
        indexes.put("t_expert_cert_application.idx_expert_cert_review_queue", indexExists("t_expert_cert_application", "idx_expert_cert_review_queue"));
        putIndexes(indexes, "t_operation_curation_item", List.of(
                "uk_operation_curation_source",
                "idx_operation_curation_status_sort"
        ));
        putIndexes(indexes, "t_operation_slot", List.of(
                "uk_operation_slot_code",
                "idx_operation_slot_status_sort"
        ));
        putIndexes(indexes, "t_operation_slot_item", List.of(
                "uk_operation_slot_source",
                "idx_operation_slot_item_slot_sort"
        ));
        putIndexes(indexes, "t_operation_topic", List.of(
                "uk_operation_topic_slug",
                "idx_operation_topic_status_sort",
                "idx_operation_topic_type_status"
        ));
        putIndexes(indexes, "t_operation_topic_section", List.of(
                "idx_operation_topic_section_topic",
                "idx_operation_topic_section_source"
        ));
        putIndexes(indexes, "t_user_follow", List.of(
                "idx_following_page",
                "idx_follower_page"
        ));
        putIndexes(indexes, "t_notif_message", List.of(
                "idx_receiver_list",
                "idx_receiver_unread_latest"
        ));
        putIndexes(indexes, "t_int_contact_request", List.of(
                "uk_contact_request_dedup",
                "idx_contact_request_receiver_status",
                "idx_contact_request_requester_status",
                "idx_contact_request_pair_status",
                "idx_contact_request_requester_day",
                "idx_contact_request_receiver_page",
                "idx_contact_request_requester_page"
        ));
        indexes.put("t_user_privacy_setting.idx_contact_request_policy", indexExists("t_user_privacy_setting", "idx_contact_request_policy"));
        putIndexes(indexes, "t_user_subscription_preference", List.of(
                "uk_user_subscription_preference_source",
                "idx_user_subscription_preference_mode",
                "idx_user_subscription_preference_expiry"
        ));
        putIndexes(indexes, "t_projection_reconcile_request", List.of(
                "uk_projection_reconcile_resource",
                "idx_projection_reconcile_operator_time",
                "idx_projection_reconcile_type_time"
        ));
        putIndexes(indexes, "t_search_index_rebuild_task", List.of(
                "uk_search_index_rebuild_active",
                "idx_search_index_rebuild_status_time",
                "idx_search_index_rebuild_lease"
        ));
        putIndexes(indexes, "t_post_reference", List.of(
                "uk_post_reference_active_url",
                "idx_post_reference_public",
                "idx_post_reference_owner"
        ));
        putIndexes(indexes, "t_post_knowledge_relation", List.of(
                "uk_post_knowledge_relation_effective",
                "idx_post_knowledge_relation_source",
                "idx_post_knowledge_relation_target",
                "idx_post_knowledge_relation_review",
                "idx_post_knowledge_relation_proposer"
        ));
        putIndexes(indexes, "t_int_post_outcome", List.of(
                "uk_int_post_outcome_current",
                "idx_int_post_outcome_public",
                "idx_int_post_outcome_follow_up"
        ));
        putIndexes(indexes, "t_int_favorite", List.of(
                "uk_user_post",
                "idx_user_folder_sort",
                "idx_folder_time",
                "idx_favorite_user_page",
                "idx_favorite_user_folder_page"
        ));
        putIndexes(indexes, "t_int_favorite_folder", List.of(
                "uk_favorite_folder_active_default",
                "uk_favorite_folder_active_name",
                "idx_favorite_folder_user_sort",
                "idx_favorite_folder_user_default"
        ));
        putIndexes(indexes, "t_int_discussion_follow", List.of(
                "uk_discussion_follow_user_post",
                "idx_discussion_follow_post_status",
                "idx_discussion_follow_uid_status",
                "idx_discussion_follow_notify_page",
                "idx_discussion_follow_uid_page"
        ));
        indexes.put("t_int_like.idx_like_user_page", indexExists("t_int_like", "idx_like_user_page"));
        indexes.put("t_int_comment.idx_comment_quality_roots", indexExists("t_int_comment", "idx_comment_quality_roots"));
        putIndexes(indexes, "t_int_comment_quality_signal", List.of(
                "uk_comment_quality_signal_comment_type",
                "idx_comment_quality_signal_post_type_status",
                "idx_comment_quality_signal_root_type_status",
                "idx_comment_quality_signal_operator_time",
                "idx_comment_quality_post_comment"
        ));
        putIndexes(indexes, "t_int_comment_helpful", List.of(
                "uk_comment_helpful_uid_comment",
                "idx_comment_helpful_comment_status",
                "idx_comment_helpful_user_status",
                "idx_comment_helpful_post_comment"
        ));
        putIndexes(indexes, "t_int_post_trust_state", List.of(
                "idx_trust_state_question_status",
                "idx_trust_state_freshness_status",
                "idx_trust_state_accepted_comment",
                "idx_trust_state_duplicate_post",
                "idx_trust_state_successor_post"
        ));
        putIndexes(indexes, "t_int_post_useful_feedback", List.of(
                "uk_user_post",
                "idx_useful_feedback_post_reason",
                "idx_useful_feedback_author_time"
        ));
        putIndexes(indexes, "t_int_content_suggestion", List.of(
                "uk_pending_suggestion",
                "idx_content_suggestion_mine_time",
                "idx_content_suggestion_mine_decided",
                "idx_content_suggestion_author_pending",
                "idx_content_suggestion_author_time",
                "idx_content_suggestion_public",
                "idx_content_suggestion_type_status"
        ));
        putIndexes(indexes, "t_collab_content_need_follow", List.of(
                "idx_collab_need_follow_uid_active_id",
                "idx_collab_need_follow_need_active_id"
        ));
        putIndexes(indexes, "t_collab_content_need_event", List.of(
                "idx_collab_need_event_need",
                "idx_collab_need_event_visibility"
        ));
        putIndexes(indexes, "t_collab_content_need_claim_cycle", List.of(
                "uk_collab_need_claim_cycle_no",
                "uk_collab_need_claim_cycle_active",
                "idx_collab_need_claim_cycle_need",
                "idx_collab_need_claim_cycle_status",
                "idx_collab_need_claim_cycle_claimant"
        ));
        putIndexes(indexes, "t_collab_content_need_revision", List.of(
                "uk_collab_need_revision_no",
                "idx_collab_need_revision_need",
                "idx_collab_need_revision_cycle_status",
                "idx_collab_need_revision_submitter",
                "idx_collab_need_revision_visibility"
        ));
        Map<String, Boolean> constraints = new LinkedHashMap<>();
        constraints.put("t_domain_moderator.PRIMARY(id)", primaryKeyExists("t_domain_moderator", "id"));
        constraints.put("t_domain_config.PRIMARY(domain)", primaryKeyExists("t_domain_config", "domain"));
        constraints.put("t_growth_event.PRIMARY(id)", primaryKeyExists("t_growth_event", "id"));
        constraints.put("t_post_version_history.PRIMARY(id)", primaryKeyExists("t_post_version_history", "id"));
        constraints.put("t_user_task_state.PRIMARY(id)", primaryKeyExists("t_user_task_state", "id"));
        constraints.put("t_content_assist_record.PRIMARY(id)", primaryKeyExists("t_content_assist_record", "id"));
        constraints.put("t_content_series.PRIMARY(id)", primaryKeyExists("t_content_series", "id"));
        constraints.put("t_content_series_post.PRIMARY(id)", primaryKeyExists("t_content_series_post", "id"));
        constraints.put("t_feed_recommend_support_stat.PRIMARY(id)", primaryKeyExists("t_feed_recommend_support_stat", "id"));
        constraints.put("t_feed_feedback_preference.PRIMARY(id)",
                primaryKeyExists("t_feed_feedback_preference", "id"));
        constraints.put("t_feed_feedback_preference.chk_feed_feedback_action",
                checkConstraintExists("t_feed_feedback_preference", "chk_feed_feedback_action"));
        constraints.put("t_feed_feedback_preference.chk_feed_feedback_target_type",
                checkConstraintExists("t_feed_feedback_preference", "chk_feed_feedback_target_type"));
        constraints.put("t_expert_cert_application.PRIMARY(id)", primaryKeyExists("t_expert_cert_application", "id"));
        constraints.put("t_operation_curation_item.PRIMARY(id)", primaryKeyExists("t_operation_curation_item", "id"));
        constraints.put("t_operation_slot.PRIMARY(id)", primaryKeyExists("t_operation_slot", "id"));
        constraints.put("t_operation_slot_item.PRIMARY(id)", primaryKeyExists("t_operation_slot_item", "id"));
        constraints.put("t_operation_topic.PRIMARY(id)", primaryKeyExists("t_operation_topic", "id"));
        constraints.put("t_operation_topic_section.PRIMARY(id)", primaryKeyExists("t_operation_topic_section", "id"));
        constraints.put("t_int_contact_request.PRIMARY(id)", primaryKeyExists("t_int_contact_request", "id"));
        constraints.put("t_user_privacy_setting.PRIMARY(user_id)", primaryKeyExists("t_user_privacy_setting", "user_id"));
        constraints.put("t_user_subscription_preference.PRIMARY(id)",
                primaryKeyExists("t_user_subscription_preference", "id"));
        constraints.put("t_user_subscription_preference.chk_user_subscription_preference_source_type",
                checkConstraintExists("t_user_subscription_preference",
                        "chk_user_subscription_preference_source_type"));
        constraints.put("t_user_subscription_preference.chk_user_subscription_preference_delivery_mode",
                checkConstraintExists("t_user_subscription_preference",
                        "chk_user_subscription_preference_delivery_mode"));
        constraints.put("t_user_subscription_preference.chk_user_subscription_preference_deleted",
                checkConstraintExists("t_user_subscription_preference",
                        "chk_user_subscription_preference_deleted"));
        constraints.put("t_projection_reconcile_request.PRIMARY(id)",
                primaryKeyExists("t_projection_reconcile_request", "id"));
        constraints.put("t_projection_reconcile_request.chk_projection_reconcile_status",
                checkConstraintExists("t_projection_reconcile_request",
                        "chk_projection_reconcile_status"));
        constraints.put("t_search_index_rebuild_task.PRIMARY(task_id)",
                primaryKeyExists("t_search_index_rebuild_task", "task_id"));
        constraints.put("t_search_index_rebuild_task.chk_search_index_rebuild_status",
                checkConstraintExists("t_search_index_rebuild_task",
                        "chk_search_index_rebuild_status"));
        constraints.put("t_int_discussion_follow.PRIMARY(id)", primaryKeyExists("t_int_discussion_follow", "id"));
        constraints.put("t_int_favorite.PRIMARY(id)", primaryKeyExists("t_int_favorite", "id"));
        constraints.put("t_int_favorite_folder.PRIMARY(id)", primaryKeyExists("t_int_favorite_folder", "id"));
        constraints.put("t_int_comment_quality_signal.PRIMARY(id)", primaryKeyExists("t_int_comment_quality_signal", "id"));
        constraints.put("t_int_comment_helpful.PRIMARY(id)", primaryKeyExists("t_int_comment_helpful", "id"));
        constraints.put("t_int_post_trust_state.PRIMARY(post_id)", primaryKeyExists("t_int_post_trust_state", "post_id"));
        constraints.put("t_int_post_useful_feedback.PRIMARY(id)", primaryKeyExists("t_int_post_useful_feedback", "id"));
        constraints.put("t_int_content_suggestion.PRIMARY(id)", primaryKeyExists("t_int_content_suggestion", "id"));
        constraints.put("t_collab_content_need_event.PRIMARY(id)",
                primaryKeyExists("t_collab_content_need_event", "id"));
        constraints.put("t_collab_content_need_claim_cycle.PRIMARY(id)",
                primaryKeyExists("t_collab_content_need_claim_cycle", "id"));
        constraints.put("t_collab_content_need_revision.PRIMARY(id)",
                primaryKeyExists("t_collab_content_need_revision", "id"));
        for (String foreignKey : List.of(
                "fk_trust_state_post",
                "fk_trust_state_accepted_comment",
                "fk_trust_state_duplicate_post",
                "fk_trust_state_successor_post"
        )) {
            constraints.put("t_int_post_trust_state." + foreignKey,
                    foreignKeyExists("t_int_post_trust_state", foreignKey));
        }
        for (String foreignKey : List.of(
                "fk_useful_feedback_post",
                "fk_useful_feedback_user"
        )) {
            constraints.put("t_int_post_useful_feedback." + foreignKey,
                    foreignKeyExists("t_int_post_useful_feedback", foreignKey));
        }
        for (String foreignKey : List.of(
                "fk_content_suggestion_post",
                "fk_content_suggestion_submitter",
                "fk_content_suggestion_decider",
                "fk_content_suggestion_result_version"
        )) {
            constraints.put("t_int_content_suggestion." + foreignKey,
                    foreignKeyExists("t_int_content_suggestion", foreignKey));
        }
        Map<String, Object> migrationLifecycle = flywayLifecycleStatus();
        boolean migrationLifecycleReady = Boolean.TRUE.equals(migrationLifecycle.get("ready"));
        boolean migrationLifecycleRequired = flywayEnabled;
        boolean trustedContentDefinitionsReady = trustedContentReady();
        boolean stageTwoToFiveDefinitionsReady = stageTwoToFiveReady();
        boolean trustedDistributionDefinitionsReady = trustedDistributionReady();
        boolean collaborationLifecycleDefinitionsReady = collaborationLifecycleReady();
        boolean collaborationClaimCycleRevisionDefinitionsReady =
                collaborationClaimCycleRevisionReady();
        boolean userSubscriptionPreferenceDefinitionsReady =
                userSubscriptionPreferenceReady();
        boolean feedFeedbackControlDefinitionsReady =
                feedFeedbackControlReady();
        boolean projectionReconcileRequestDefinitionsReady =
                projectionReconcileRequestReady();
        boolean searchIndexRebuildTaskDefinitionsReady =
                searchIndexRebuildTaskReady();
        boolean ready = tables.values().stream().allMatch(Boolean::booleanValue)
                && columns.values().stream().allMatch(Boolean::booleanValue)
                && indexes.values().stream().allMatch(Boolean::booleanValue)
                && constraints.values().stream().allMatch(Boolean::booleanValue)
                && trustedContentDefinitionsReady
                && stageTwoToFiveDefinitionsReady
                && trustedDistributionDefinitionsReady
                && collaborationLifecycleDefinitionsReady
                && collaborationClaimCycleRevisionDefinitionsReady
                && userSubscriptionPreferenceDefinitionsReady
                && feedFeedbackControlDefinitionsReady
                && projectionReconcileRequestDefinitionsReady
                && searchIndexRebuildTaskDefinitionsReady
                && (!migrationLifecycleRequired || migrationLifecycleReady);
        List<String> missing = missingItems(tables, columns, indexes, constraints);
        if (!trustedContentDefinitionsReady) {
            missing.add("schema:trusted-content-definitions");
        }
        if (!stageTwoToFiveDefinitionsReady) {
            missing.add("schema:stage-2-5-definitions");
        }
        if (!trustedDistributionDefinitionsReady) {
            missing.add("schema:trusted-distribution-definitions");
        }
        if (!collaborationLifecycleDefinitionsReady) {
            missing.add("schema:collaboration-lifecycle-definitions");
        }
        if (!collaborationClaimCycleRevisionDefinitionsReady) {
            missing.add("schema:collaboration-claim-cycle-revision-definitions");
        }
        if (!userSubscriptionPreferenceDefinitionsReady) {
            missing.add("schema:user-subscription-preference-definitions");
        }
        if (!feedFeedbackControlDefinitionsReady) {
            missing.add("schema:feed-feedback-control-definitions");
        }
        if (!projectionReconcileRequestDefinitionsReady) {
            missing.add("schema:projection-reconcile-request-definitions");
        }
        if (!searchIndexRebuildTaskDefinitionsReady) {
            missing.add("schema:search-index-rebuild-task-definitions");
        }
        if (migrationLifecycleRequired && !migrationLifecycleReady) {
            missing.add("flyway:migration-lifecycle");
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", ready);
        status.put("status", ready ? "UP" : "BLOCKED_BY_SCHEMA");
        status.put("tables", tables);
        status.put("columns", columns);
        status.put("indexes", indexes);
        status.put("constraints", constraints);
        status.put("trustedContentDefinitionsReady", trustedContentDefinitionsReady);
        status.put("stageTwoToFiveDefinitionsReady", stageTwoToFiveDefinitionsReady);
        status.put("trustedDistributionDefinitionsReady", trustedDistributionDefinitionsReady);
        status.put("collaborationLifecycleDefinitionsReady", collaborationLifecycleDefinitionsReady);
        status.put("collaborationClaimCycleRevisionDefinitionsReady",
                collaborationClaimCycleRevisionDefinitionsReady);
        status.put("userSubscriptionPreferenceDefinitionsReady",
                userSubscriptionPreferenceDefinitionsReady);
        status.put("feedFeedbackControlDefinitionsReady",
                feedFeedbackControlDefinitionsReady);
        status.put("projectionReconcileRequestDefinitionsReady",
                projectionReconcileRequestDefinitionsReady);
        status.put("searchIndexRebuildTaskDefinitionsReady",
                searchIndexRebuildTaskDefinitionsReady);
        status.put("migrationLifecycleRequired", migrationLifecycleRequired);
        status.put("migrationLifecycle", migrationLifecycle);
        status.put("missing", missing);
        status.put("migration", "Flyway manages the ordered files listed in status.migrations.");
        status.put("migrations", List.of(
                "db/migration/20260608_tag_governance.sql",
                "db/migration/20260605_ai_extract_task_metrics.sql",
                "db/migration/20260608_community_topics.sql",
                "db/migration/20260608_review_queue.sql",
                "db/migration/20260608_mock_interview_ai_review_transparency.sql",
                "db/migration/20260617_domain_moderators.sql",
                "db/migration/20260623_domain_config.sql",
                "db/migration/20260623_growth_event.sql",
                "db/migration/20260623_user_task_state.sql",
                "db/migration/20260618_post_extension_domain_index.sql",
                "db/migration/20260624_content_assist_ai.sql",
                "db/migration/20260624_content_series.sql",
                "db/migration/20260624_new_creator_support_stats.sql",
                "db/migration/20260624_expert_certification.sql",
                "db/migration/20260703_operation_curation.sql",
                "db/migration/20260705_v3_home_featured_slot.sql",
                "db/migration/20260707_contact_request.sql",
                "db/migration/20260707_contact_request_settings.sql",
                "db/migration/20260707_discussion_follow.sql",
                "db/migration/20260707_favorite_folder.sql",
                "db/migration/20260707_comment_quality_schema.sql",
                "db/migration/20260708_operation_soft_delete_unique_guard.sql",
                "db/migration/20260708_public_read_indexes.sql",
                "db/migration/20260709_retry_task_claim_indexes.sql",
                "db/migration/20260712_unclassified_domain.sql",
                "db/migration/20260713_trusted_content_stage1.sql",
                "db/migration/20260714_collaboration_stage2.sql",
                "db/migration/20260714_incentive_stage3_stage5.sql",
                "db/migration/20260714_database_integrity_hardening.sql",
                "db/migration/20260715_trusted_distribution_revisit.sql",
                "db/migration/20260715_content_maintenance_stage8.sql",
                "db/migration/20260717_collab_need_submission.sql",
                "db/migration/20260718_collab_need_lifecycle.sql",
                "db/migration/20260719_collab_need_claim_cycle_revision.sql",
                "db/migration/20260719_feed_feedback_control.sql",
                "db/migration/20260719_user_subscription_preference.sql",
                "db/migration/20260719_projection_reconcile_request.sql",
                "db/migration/20260720_search_index_rebuild_task.sql"
        ));
        if (!ready) {
            status.put("message", "数据库结构或 Flyway 执行历史未补齐，相关功能会降级或被阻断。");
        }
        return status;
    }

    public boolean tagGovernanceReady() {
        return tableExists("t_tag")
                && columnExists("t_tag", "tag_status")
                && columnExists("t_tag", "recommended")
                && columnExists("t_tag", "synonyms")
                && columnExists("t_tag", "merge_target_id")
                && columnExists("t_tag", "update_time")
                && indexExists("t_tag", "idx_tag_status_recommend");
    }

    public boolean mockInterviewAiReviewReady() {
        return tableExists("t_mock_interview_answer")
                && columnExists("t_mock_interview_answer", "ai_review_task_id")
                && columnExists("t_mock_interview_answer", "ai_review_fallback_used")
                && columnExists("t_mock_interview_answer", "ai_review_duration_ms")
                && columnExists("t_mock_interview_answer", "ai_review_prompt_tokens")
                && columnExists("t_mock_interview_answer", "ai_review_completion_tokens")
                && columnExists("t_mock_interview_answer", "ai_review_estimated_cost_micros")
                && columnExists("t_mock_interview_answer", "ai_review_error_code");
    }

    public boolean aiExtractTaskMetricsReady() {
        return tableExists("t_ai_extract_task")
                && columnExists("t_ai_extract_task", "provider")
                && columnExists("t_ai_extract_task", "fallback_used")
                && columnExists("t_ai_extract_task", "duration_ms")
                && columnExists("t_ai_extract_task", "prompt_tokens")
                && columnExists("t_ai_extract_task", "completion_tokens")
                && columnExists("t_ai_extract_task", "estimated_cost_micros")
                && columnExists("t_ai_extract_task", "error_code");
    }

    public boolean communityTopicReady() {
        return tableExists("t_community_topic")
                && tableExists("t_community_topic_tag")
                && tableExists("t_community_topic_follow")
                && indexExists("t_community_topic", "uk_topic_slug")
                && indexExists("t_community_topic", "idx_topic_status_sort")
                && indexExists("t_community_topic", "idx_topic_featured_sort")
                && columnExists("t_community_topic", "domain")
                && columnExists("t_community_topic", "allowed_domains")
                && indexExists("t_community_topic", "idx_community_topic_domain")
                && indexExists("t_community_topic_tag", "uk_topic_tag")
                && indexExists("t_community_topic_tag", "idx_topic_tag_topic")
                && indexExists("t_community_topic_tag", "idx_topic_tag_tag")
                && indexExists("t_community_topic_follow", "uk_topic_follow_user")
                && indexExists("t_community_topic_follow", "idx_topic_follow_uid")
                && indexExists("t_community_topic_follow", "idx_topic_follow_topic");
    }

    public boolean reviewQueueReady() {
        return tableExists("t_review_queue")
                && indexExists("t_review_queue", "uk_review_queue_source")
                && indexExists("t_review_queue", "idx_review_queue_status_priority")
                && indexExists("t_review_queue", "idx_review_queue_source_status")
                && indexExists("t_review_queue", "idx_review_queue_assignee_status")
                && indexExists("t_review_queue", "idx_review_queue_risk_status");
    }

    public boolean domainModeratorReady() {
        return tableExists("t_domain_moderator")
                && columnExists("t_domain_moderator", "id")
                && columnExists("t_domain_moderator", "uid")
                && columnExists("t_domain_moderator", "domain")
                && columnExists("t_domain_moderator", "enabled")
                && columnExists("t_domain_moderator", "created_by")
                && columnExists("t_domain_moderator", "create_time")
                && columnExists("t_domain_moderator", "update_time")
                && primaryKeyExists("t_domain_moderator", "id")
                && indexExists("t_domain_moderator", "uk_domain_moderator_uid_domain")
                && indexExists("t_domain_moderator", "idx_domain_moderator_domain_enabled")
                && indexExists("t_domain_moderator", "idx_domain_moderator_uid_enabled");
    }

    public boolean domainConfigReady() {
        return tableExists("t_domain_config")
                && columnExists("t_domain_config", "domain")
                && columnExists("t_domain_config", "domain_name")
                && columnExists("t_domain_config", "domain_slug")
                && columnExists("t_domain_config", "description")
                && columnExists("t_domain_config", "sort_order")
                && columnExists("t_domain_config", "enabled")
                && columnExists("t_domain_config", "risk_level")
                && columnExists("t_domain_config", "posting_notice")
                && columnExists("t_domain_config", "browse_notice")
                && columnExists("t_domain_config", "interaction_notice")
                && columnExists("t_domain_config", "created_by")
                && columnExists("t_domain_config", "updated_by")
                && columnExists("t_domain_config", "create_time")
                && columnExists("t_domain_config", "update_time")
                && primaryKeyExists("t_domain_config", "domain")
                && indexExists("t_domain_config", "uk_domain_config_slug")
                && indexExists("t_domain_config", "idx_domain_config_enabled_sort")
                && indexExists("t_domain_config", "idx_domain_config_risk_enabled");
    }

    public boolean growthEventReady() {
        return tableExists("t_growth_event")
                && columnExists("t_growth_event", "id")
                && columnExists("t_growth_event", "event_key")
                && columnExists("t_growth_event", "event_type")
                && columnExists("t_growth_event", "uid")
                && columnExists("t_growth_event", "domain")
                && columnExists("t_growth_event", "content_id")
                && columnExists("t_growth_event", "target_type")
                && columnExists("t_growth_event", "target_value")
                && columnExists("t_growth_event", "source_page")
                && columnExists("t_growth_event", "ext_json")
                && columnExists("t_growth_event", "create_time")
                && primaryKeyExists("t_growth_event", "id")
                && indexExists("t_growth_event", "idx_growth_event_type_time")
                && indexExists("t_growth_event", "idx_growth_event_domain_time")
                && indexExists("t_growth_event", "idx_growth_event_uid_time")
                && indexExists("t_growth_event", "idx_growth_event_content_time")
                && indexExists("t_growth_event", "uk_growth_event_key");
    }

    public boolean trustedContentReady() {
        return tableExists("t_int_post_trust_state")
                && tableExists("t_int_post_useful_feedback")
                && tableExists("t_int_content_suggestion")
                && tableExists("t_post_version_history")
                && tableExists("t_growth_event")
                && columnDefinitionMatches("t_int_post_trust_state", "post_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "question_status",
                "varchar(32)", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "accepted_comment_id",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "duplicate_post_id",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "freshness_status",
                "varchar(32)", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "successor_post_id",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "last_confirmed_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_int_post_trust_state", "suggestions_open",
                "tinyint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_useful_feedback", "id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_useful_feedback", "user_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_useful_feedback", "post_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_useful_feedback", "post_author_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_post_useful_feedback", "reason",
                "varchar(32)", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "post_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "post_author_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "submitter_uid",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "suggestion_type",
                "varchar(32)", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "detail",
                "varchar(2000)", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "normalized_content_hash",
                "char(64)", false, "ascii", "ascii_bin", null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "source_url",
                "varchar(1000)", true, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "allow_public_attribution",
                "tinyint", false, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "decision",
                "varchar(32)", true, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "author_reply",
                "varchar(1000)", true, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "public_note",
                "varchar(500)", true, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "result_version",
                "int", true, null, null, null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "pending_dedup_key",
                "char(64)", true, "ascii", "ascii_bin", null, null)
                && columnDefinitionMatches("t_int_content_suggestion", "pending_guard",
                "tinyint", true, null, null, "STORED GENERATED", "decisionisnull")
                && columnDefinitionMatches("t_int_content_suggestion", "decided_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_post_version_history", "result_version",
                "int", true, null, null, null, null)
                && columnDefinitionMatches("t_post_version_history", "public_update_summary",
                "varchar(500)", true, null, null, null, null)
                && columnDefinitionMatches("t_post_version_history", "impact_scope",
                "varchar(255)", true, null, null, null, null)
                && columnDefinitionMatches("t_growth_event", "event_key",
                "varchar(128)", true, "ascii", "ascii_bin", null, null)
                && primaryKeyExists("t_int_post_trust_state", "post_id")
                && primaryKeyExists("t_int_post_useful_feedback", "id")
                && primaryKeyExists("t_int_content_suggestion", "id")
                && indexDefinitionMatches("t_int_post_trust_state",
                "idx_trust_state_question_status", false,
                "question_status", "update_time", "post_id")
                && indexDefinitionMatches("t_int_post_trust_state",
                "idx_trust_state_freshness_status", false,
                "freshness_status", "update_time", "post_id")
                && indexDefinitionMatches("t_int_post_trust_state",
                "idx_trust_state_accepted_comment", false, "accepted_comment_id")
                && indexDefinitionMatches("t_int_post_trust_state",
                "idx_trust_state_duplicate_post", false, "duplicate_post_id")
                && indexDefinitionMatches("t_int_post_trust_state",
                "idx_trust_state_successor_post", false, "successor_post_id")
                && indexDefinitionMatches("t_int_post_useful_feedback",
                "uk_user_post", true, "user_id", "post_id")
                && indexDefinitionMatches("t_int_post_useful_feedback",
                "idx_useful_feedback_post_reason", false, "post_id", "reason")
                && indexDefinitionMatches("t_int_post_useful_feedback",
                "idx_useful_feedback_author_time", false,
                "post_author_id", "create_time", "post_id")
                && indexDefinitionMatches("t_int_content_suggestion",
                "uk_pending_suggestion", true,
                "post_id", "submitter_uid", "suggestion_type",
                "normalized_content_hash", "pending_guard")
                && indexDefinitionMatches("t_int_content_suggestion",
                "idx_content_suggestion_mine_time", false,
                "post_id", "submitter_uid", "update_time", "id")
                && indexDefinitionMatches("t_int_content_suggestion",
                "idx_content_suggestion_mine_decided", false,
                "post_id", "submitter_uid", "decided_at", "id")
                && indexDefinitionMatches("t_int_content_suggestion",
                "idx_content_suggestion_author_pending", false,
                "post_author_id", "decision", "update_time", "id")
                && indexDefinitionMatches("t_int_content_suggestion",
                "idx_content_suggestion_author_time", false,
                "post_id", "update_time", "id")
                && indexDefinitionMatches("t_int_content_suggestion",
                "idx_content_suggestion_public", false,
                "post_id", "decided_at", "id")
                && indexDefinitionMatches("t_int_content_suggestion",
                "idx_content_suggestion_type_status", false,
                "post_id", "suggestion_type", "decision")
                && indexDefinitionMatches("t_post_version_history",
                "uk_post_result_version", true, "post_id", "result_version")
                && indexDefinitionMatches("t_post_version_history",
                "idx_post_public_update", false,
                "post_id", "result_version", "create_time", "id")
                && indexDefinitionMatches("t_growth_event",
                "uk_growth_event_key", true, "event_key")
                && foreignKeyDefinitionMatches("t_int_post_trust_state",
                "fk_trust_state_post", "post_id",
                "t_post_main", "id", "CASCADE", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_post_trust_state",
                "fk_trust_state_accepted_comment", "accepted_comment_id",
                "t_int_comment", "id", "SET NULL", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_post_trust_state",
                "fk_trust_state_duplicate_post", "duplicate_post_id",
                "t_post_main", "id", "SET NULL", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_post_trust_state",
                "fk_trust_state_successor_post", "successor_post_id",
                "t_post_main", "id", "SET NULL", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_post_useful_feedback",
                "fk_useful_feedback_post", "post_id",
                "t_post_main", "id", "CASCADE", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_post_useful_feedback",
                "fk_useful_feedback_user", "user_id",
                "t_user_account", "id", "CASCADE", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_content_suggestion",
                "fk_content_suggestion_post", "post_id",
                "t_post_main", "id", "CASCADE", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_content_suggestion",
                "fk_content_suggestion_submitter", "submitter_uid",
                "t_user_account", "id", "RESTRICT", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_content_suggestion",
                "fk_content_suggestion_decider", "post_author_id",
                "t_user_account", "id", "RESTRICT", "RESTRICT")
                && foreignKeyDefinitionMatches("t_int_content_suggestion",
                "fk_content_suggestion_result_version", "post_id,result_version",
                "t_post_version_history", "post_id,result_version",
                "RESTRICT", "RESTRICT");
    }

    public boolean trustedDistributionReady() {
        return tableExists("t_int_content_trust_profile")
                && tableExists("t_int_user_revisit_item")
                && tableExists("t_search_content_gap")
                && columnExists("t_int_content_trust_profile", "post_id")
                && columnExists("t_int_content_trust_profile", "author_uid")
                && columnExists("t_int_content_trust_profile", "completeness_score")
                && columnExists("t_int_content_trust_profile", "last_confirmed_at")
                && indexExists("t_int_content_trust_profile", "idx_trust_profile_author")
                && indexExists("t_int_user_revisit_item", "uk_revisit_item_dedup")
                && indexExists("t_int_user_revisit_item", "idx_revisit_user_status_due")
                && indexExists("t_search_content_gap", "uk_search_content_gap_key")
                && indexExists("t_search_content_gap", "idx_search_content_gap_queue");
    }

    public boolean collaborationLifecycleReady() {
        return tableExists("t_collab_content_need_event")
                && tableExists("t_collab_content_need_claim_cycle")
                && tableExists("t_collab_content_need_revision")
                && columnDefinitionMatches("t_collab_content_need", "submitted_by_uid",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "submitted_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "submission_resolution_type",
                "varchar(24)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "submission_resolution_id",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "submission_note",
                "varchar(1000)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "reject_reason",
                "varchar(500)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "claimed_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need", "last_progress_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "need_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "event_type",
                "varchar(24)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "actor_uid",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "claimant_uid",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "from_status",
                "varchar(24)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "to_status",
                "varchar(24)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "target_type",
                "varchar(32)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "target_id",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "note",
                "varchar(1000)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "visibility_scope",
                "varchar(24)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_event", "create_time",
                "datetime(3)", false, null, null, null, null)
                && primaryKeyExists("t_collab_content_need_event", "id")
                && indexDefinitionMatches("t_collab_content_need_follow",
                "idx_collab_need_follow_uid_active_id", false, "uid", "active", "id")
                && indexDefinitionMatches("t_collab_content_need_follow",
                "idx_collab_need_follow_need_active_id", false, "need_id", "active", "id")
                && indexDefinitionMatches("t_collab_content_need_event",
                "idx_collab_need_event_need", false, "need_id", "id")
                && indexDefinitionMatches("t_collab_content_need_event",
                "idx_collab_need_event_visibility", false,
                "need_id", "visibility_scope", "id");
    }

    public boolean collaborationClaimCycleRevisionReady() {
        return columnDefinitionMatches("t_collab_content_need_claim_cycle", "id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "need_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "cycle_no",
                "int", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "claimant_uid",
                "bigint", false, null, null, null, null)
                 && columnDefinitionMatches("t_collab_content_need_claim_cycle", "cycle_status",
                 "varchar(24)", false, null, null, null, null)
                 && columnDefinitionMatches("t_collab_content_need_claim_cycle", "cycle_origin",
                 "varchar(32)", false, null, null, null, null)
                 && columnDefinitionMatches("t_collab_content_need_claim_cycle", "claimed_at",
                "datetime(3)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "last_progress_at",
                "datetime(3)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "ended_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "end_reason",
                "varchar(500)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "create_time",
                "datetime(3)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "update_time",
                "datetime(3)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_claim_cycle", "active_need_guard",
                "bigint", true, null, null, "STORED GENERATED", "cycle_status=active")
                && columnDefinitionMatches("t_collab_content_need_revision", "id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "need_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "cycle_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "cycle_no",
                "int", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "revision_no",
                "int", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "submitter_uid",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "resolution_type",
                "varchar(24)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "resolution_id",
                "bigint", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "resolution_post_id",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "revision_note",
                "varchar(1000)", true, null, null, null, null)
                 && columnDefinitionMatches("t_collab_content_need_revision", "revision_status",
                 "varchar(24)", false, null, null, null, null)
                 && columnDefinitionMatches("t_collab_content_need_revision", "revision_origin",
                 "varchar(32)", false, null, null, null, null)
                 && columnDefinitionMatches("t_collab_content_need_revision", "submitted_at",
                "datetime(3)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "decided_by",
                "bigint", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "decided_at",
                "datetime(3)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "decision_note",
                "varchar(1000)", true, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "visibility_scope",
                "varchar(24)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "create_time",
                "datetime(3)", false, null, null, null, null)
                && columnDefinitionMatches("t_collab_content_need_revision", "update_time",
                "datetime(3)", false, null, null, null, null)
                && primaryKeyExists("t_collab_content_need_claim_cycle", "id")
                && primaryKeyExists("t_collab_content_need_revision", "id")
                && indexDefinitionMatches("t_collab_content_need_claim_cycle",
                "uk_collab_need_claim_cycle_no", true, "need_id", "cycle_no")
                && indexDefinitionMatches("t_collab_content_need_claim_cycle",
                "uk_collab_need_claim_cycle_active", true, "active_need_guard")
                && indexDefinitionMatches("t_collab_content_need_claim_cycle",
                "idx_collab_need_claim_cycle_need", false, "need_id", "cycle_no", "id")
                && indexDefinitionMatches("t_collab_content_need_claim_cycle",
                "idx_collab_need_claim_cycle_status", false, "need_id", "cycle_status", "id")
                && indexDefinitionMatches("t_collab_content_need_claim_cycle",
                "idx_collab_need_claim_cycle_claimant", false,
                "claimant_uid", "cycle_status", "update_time", "id")
                && indexDefinitionMatches("t_collab_content_need_revision",
                "uk_collab_need_revision_no", true, "cycle_id", "revision_no")
                && indexDefinitionMatches("t_collab_content_need_revision",
                "idx_collab_need_revision_need", false,
                "need_id", "cycle_no", "revision_no", "id")
                && indexDefinitionMatches("t_collab_content_need_revision",
                "idx_collab_need_revision_cycle_status", false,
                "cycle_id", "revision_status", "id")
                && indexDefinitionMatches("t_collab_content_need_revision",
                "idx_collab_need_revision_submitter", false,
                "submitter_uid", "create_time", "id")
                && indexDefinitionMatches("t_collab_content_need_revision",
                "idx_collab_need_revision_visibility", false,
                "need_id", "visibility_scope", "id");
    }

    public boolean stageTwoToFiveReady() {
        return columnDefinitionMatches("t_post_extension", "domain",
                "tinyint", true, null, null, "VIRTUAL GENERATED", "json_extractext_json$.domain")
                && columnDefinitionMatches("t_collab_curation_suggestion", "pending_guard",
                "tinyint", true, null, null, "STORED GENERATED", "review_status=pending")
                && columnDefinitionMatches("t_collab_governance_case", "pending_guard",
                "tinyint", true, null, null, "STORED GENERATED", "case_status=pending")
                && columnDefinitionMatches("t_collab_governance_case", "appeal_guard",
                "bigint", true, null, null, "STORED GENERATED", "case_type=appeal")
                && columnDefinitionMatches("t_community_role_application", "active_guard",
                "tinyint", true, null, null, "STORED GENERATED", "application_status=submitted")
                && columnDefinitionMatches("t_community_role_grant", "active_guard",
                "tinyint", true, null, null, "STORED GENERATED", "grant_statusinactive")
                && indexDefinitionMatches("t_collab_curation_suggestion",
                "uk_collab_curation_pending", true,
                "topic_id", "post_id", "submitter_uid", "pending_guard")
                && indexDefinitionMatches("t_collab_governance_case",
                "uk_collab_case_pending", true,
                "case_type", "target_type", "target_id", "submitter_uid", "pending_guard")
                && indexDefinitionMatches("t_collab_governance_case",
                "uk_collab_case_single_appeal", true, "appeal_guard")
                && indexDefinitionMatches("t_incentive_account",
                "uk_incentive_account_scope", true,
                "user_id", "account_type", "domain_code")
                && indexDefinitionMatches("t_incentive_ledger",
                "uk_incentive_ledger_idempotency", true, "idempotency_key")
                && indexDefinitionMatches("t_incentive_ledger",
                "uk_incentive_ledger_reversal", true, "reversed_entry_id")
                && indexDefinitionMatches("t_incentive_freeze_record",
                "idx_incentive_freeze_account_status", false,
                "account_id", "freeze_status", "blocks_spending", "freeze_amount")
                && indexDefinitionMatches("t_incentive_invalidation_job",
                "idx_incentive_invalidation_reference", false,
                "reference_type", "reference_id")
                && indexDefinitionMatches("t_virtual_benefit_order",
                "idx_virtual_benefit_order_benefit", false, "benefit_id")
                && indexDefinitionMatches("t_quota_bounty_submission",
                "idx_quota_submission_applicant", false,
                "applicant_uid", "create_time", "id")
                && indexDefinitionMatches("t_quota_bounty_appeal",
                "idx_quota_bounty_appeal_applicant", false,
                "applicant_uid", "create_time", "id")
                && indexDefinitionMatches("t_community_role_application",
                "uk_community_role_active_application", true,
                "applicant_uid", "role_code", "domain_code", "active_guard")
                && indexDefinitionMatches("t_community_role_grant",
                "uk_community_role_active_grant", true,
                "user_id", "role_code", "domain_code", "active_guard")
                && checkConstraintExists("t_incentive_account", "chk_incentive_account_balances")
                && checkConstraintExists("t_virtual_benefit_catalog", "chk_virtual_benefit_stock_null_pair")
                && checkConstraintExists("t_virtual_benefit_order", "chk_virtual_benefit_order_cost")
                && triggerDefinitionMatches("t_incentive_ledger",
                "trg_incentive_ledger_block_update", "BEFORE", "UPDATE", "append-only")
                && triggerDefinitionMatches("t_incentive_ledger",
                "trg_incentive_ledger_block_delete", "BEFORE", "DELETE", "append-only");
    }

    public boolean contentAssistReady() {
        return tableExists("t_content_assist_record")
                && columnExists("t_content_assist_record", "scene")
                && columnExists("t_content_assist_record", "provider")
                && columnExists("t_content_assist_record", "assist_status")
                && columnExists("t_content_assist_record", "content_hash")
                && primaryKeyExists("t_content_assist_record", "id")
                && indexExists("t_content_assist_record", "idx_content_assist_scene_time")
                && indexExists("t_content_assist_record", "idx_content_assist_status_time")
                && indexExists("t_content_assist_record", "idx_content_assist_provider_time")
                && indexExists("t_content_assist_record", "idx_content_assist_uid_time");
    }

    public boolean contentSeriesReady() {
        return tableExists("t_content_series")
                && tableExists("t_content_series_post")
                && columnExists("t_content_series", "creator_uid")
                && columnExists("t_content_series", "title")
                && columnExists("t_content_series", "visibility")
                && columnExists("t_content_series_post", "series_id")
                && columnExists("t_content_series_post", "post_id")
                && primaryKeyExists("t_content_series", "id")
                && primaryKeyExists("t_content_series_post", "id")
                && indexExists("t_content_series", "idx_content_series_creator_update")
                && indexExists("t_content_series", "idx_content_series_domain_update")
                && indexExists("t_content_series", "idx_content_series_public_creator")
                && indexExists("t_content_series_post", "uk_content_series_post")
                && indexExists("t_content_series_post", "idx_content_series_post_series_sort")
                && indexExists("t_content_series_post", "idx_content_series_post_post");
    }

    public boolean newCreatorSupportStatsReady() {
        return tableExists("t_feed_recommend_support_stat")
                && columnExists("t_feed_recommend_support_stat", "viewer_uid")
                && columnExists("t_feed_recommend_support_stat", "domain")
                && columnExists("t_feed_recommend_support_stat", "delivered_item_count")
                && columnExists("t_feed_recommend_support_stat", "support_hit_item_count")
                && primaryKeyExists("t_feed_recommend_support_stat", "id")
                && indexExists("t_feed_recommend_support_stat", "idx_feed_recommend_support_stat_create_time")
                && indexExists("t_feed_recommend_support_stat", "idx_feed_recommend_support_stat_domain_create_time")
                && indexExists("t_feed_recommend_support_stat", "idx_feed_recommend_support_stat_viewer_create_time");
    }

    public boolean feedFeedbackControlReady() {
        return tableExists("t_feed_feedback_preference")
                && columnExists("t_feed_feedback_preference", "uid")
                && columnExists("t_feed_feedback_preference", "post_id")
                && columnExists("t_feed_feedback_preference", "action")
                && columnExists("t_feed_feedback_preference", "target_type")
                && columnExists("t_feed_feedback_preference", "target_id")
                && columnExists("t_feed_feedback_preference", "expires_at")
                && primaryKeyExists("t_feed_feedback_preference", "id")
                && indexExists("t_feed_feedback_preference", "uk_feed_feedback_uid_post")
                && indexExists("t_feed_feedback_preference", "idx_feed_feedback_uid_action_active")
                && indexExists("t_feed_feedback_preference", "idx_feed_feedback_uid_target_active")
                && indexExists("t_feed_feedback_preference", "idx_feed_feedback_uid_cursor")
                && checkConstraintExists("t_feed_feedback_preference", "chk_feed_feedback_action")
                && checkConstraintExists("t_feed_feedback_preference", "chk_feed_feedback_target_type");
    }

    public boolean creatorContentRevisionBoundaryReady() {
        return tableExists("t_post_main")
                && columnExists("t_post_main", "latest_effective_content_revision_at")
                && columnExists("t_post_main", "latest_effective_content_revision_token")
                && tableExists("t_post_version_history")
                && columnExists("t_post_version_history", "quality_signal_revision")
                && columnExists("t_post_version_history", "quality_signal_revision_state")
                && columnExists("t_post_version_history", "quality_signal_effective_at")
                && columnExists("t_post_version_history", "quality_signal_revision_token")
                && indexExists("t_post_version_history", "idx_post_quality_signal_revision")
                && tableExists("t_feed_feedback_preference")
                && indexExists("t_feed_feedback_preference", "idx_feed_feedback_quality_signal_v32");
    }

    public boolean creatorQualityProjectionReady() {
        return creatorContentRevisionBoundaryReady()
                && postExtensionDomainReady();
    }

    public boolean creatorQualityProjectionReleaseReady() {
        return creatorQualityProjectionReady()
                && Boolean.TRUE.equals(flywayLifecycleStatus().get("ready"));
    }

    public boolean expertCertificationReady() {
        return tableExists("t_expert_cert_application")
                && columnExists("t_expert_cert_application", "applicant_uid")
                && columnExists("t_expert_cert_application", "domain")
                && columnExists("t_expert_cert_application", "status")
                && columnExists("t_expert_cert_application", "evidence_summary")
                && columnExists("t_expert_cert_application", "risk_acknowledged")
                && columnExists("t_expert_cert_application", "active_guard")
                && primaryKeyExists("t_expert_cert_application", "id")
                && indexExists("t_expert_cert_application", "uk_expert_cert_active_guard")
                && indexExists("t_expert_cert_application", "idx_expert_cert_applicant_domain")
                && indexExists("t_expert_cert_application", "idx_expert_cert_review_queue");
    }

    public boolean postExtensionDomainReady() {
        return tableExists("t_post_extension")
                && columnExists("t_post_extension", "domain")
                && indexExists("t_post_extension", "idx_post_extension_domain_post");
    }

    public boolean operationCurationReady() {
        return tableExists("t_operation_curation_item")
                && tableExists("t_operation_slot")
                && tableExists("t_operation_slot_item")
                && tableExists("t_operation_topic")
                && tableExists("t_operation_topic_section")
                && columnExists("t_operation_curation_item", "active_guard")
                && columnExists("t_operation_slot_item", "active_guard")
                && columnExists("t_operation_slot", "slot_status")
                && columnExists("t_operation_topic", "topic_status")
                && columnExists("t_operation_topic", "draft_revision")
                && columnExists("t_operation_topic_section", "section_key")
                && primaryKeyExists("t_operation_curation_item", "id")
                && primaryKeyExists("t_operation_slot", "id")
                && primaryKeyExists("t_operation_slot_item", "id")
                && primaryKeyExists("t_operation_topic", "id")
                && primaryKeyExists("t_operation_topic_section", "id")
                && indexExists("t_operation_curation_item", "uk_operation_curation_source")
                && indexExists("t_operation_curation_item", "idx_operation_curation_status_sort")
                && indexExists("t_operation_slot", "uk_operation_slot_code")
                && indexExists("t_operation_slot", "idx_operation_slot_status_sort")
                && indexExists("t_operation_slot_item", "uk_operation_slot_source")
                && indexExists("t_operation_slot_item", "idx_operation_slot_item_slot_sort")
                && indexExists("t_operation_topic", "uk_operation_topic_slug")
                && indexExists("t_operation_topic", "idx_operation_topic_status_sort")
                && indexExists("t_operation_topic", "idx_operation_topic_type_status")
                && indexExists("t_operation_topic_section", "idx_operation_topic_section_topic")
                && indexExists("t_operation_topic_section", "idx_operation_topic_section_source");
    }

    public boolean v3HomeFeaturedSlotReady() {
        return tableExists("t_operation_slot")
                && columnExists("t_operation_slot", "current_version")
                && columnExists("t_operation_slot", "published_snapshot_json")
                && columnExists("t_operation_slot", "rollback_snapshot_json")
                && indexExists("t_operation_slot", "uk_operation_slot_code")
                && indexExists("t_operation_slot", "idx_operation_slot_status_sort");
    }

    public boolean contactRequestReady() {
        return tableExists("t_int_contact_request")
                && columnExists("t_int_contact_request", "requester_uid")
                && columnExists("t_int_contact_request", "receiver_uid")
                && columnExists("t_int_contact_request", "request_status")
                && columnExists("t_int_contact_request", "dedup_key")
                && columnExists("t_int_contact_request", "create_time")
                && columnExists("t_int_contact_request", "is_deleted")
                && primaryKeyExists("t_int_contact_request", "id")
                && indexExists("t_int_contact_request", "uk_contact_request_dedup")
                && indexExists("t_int_contact_request", "idx_contact_request_receiver_status")
                && indexExists("t_int_contact_request", "idx_contact_request_requester_status")
                && indexExists("t_int_contact_request", "idx_contact_request_pair_status")
                && indexExists("t_int_contact_request", "idx_contact_request_requester_day");
    }

    public boolean contactRequestSettingsReady() {
        return tableExists("t_user_privacy_setting")
                && columnExists("t_user_privacy_setting", "accept_contact_request")
                && columnExists("t_user_privacy_setting", "contact_request_policy")
                && columnExists("t_user_privacy_setting", "contact_request_daily_limit")
                && primaryKeyExists("t_user_privacy_setting", "user_id")
                && indexExists("t_user_privacy_setting", "idx_contact_request_policy");
    }

    public boolean userSubscriptionPreferenceReady() {
        return tableExists("t_user_subscription_preference")
                && columnExists("t_user_subscription_preference", "uid")
                && columnExists("t_user_subscription_preference", "source_type")
                && columnExists("t_user_subscription_preference", "source_id")
                && columnExists("t_user_subscription_preference", "delivery_mode")
                && columnExists("t_user_subscription_preference", "expires_at")
                && columnExists("t_user_subscription_preference", "is_deleted")
                && primaryKeyExists("t_user_subscription_preference", "id")
                && indexExists("t_user_subscription_preference",
                "uk_user_subscription_preference_source")
                && indexExists("t_user_subscription_preference",
                "idx_user_subscription_preference_mode")
                && indexExists("t_user_subscription_preference",
                "idx_user_subscription_preference_expiry")
                && checkConstraintExists("t_user_subscription_preference",
                "chk_user_subscription_preference_source_type")
                && checkConstraintExists("t_user_subscription_preference",
                "chk_user_subscription_preference_delivery_mode")
                && checkConstraintExists("t_user_subscription_preference",
                "chk_user_subscription_preference_deleted");
    }

    public boolean projectionReconcileRequestReady() {
        return tableExists("t_projection_reconcile_request")
                && columnExists("t_projection_reconcile_request", "resource_id")
                && columnExists("t_projection_reconcile_request", "operator_uid")
                && columnExists("t_projection_reconcile_request", "projection_type")
                && columnExists("t_projection_reconcile_request", "idempotency_key")
                && columnExists("t_projection_reconcile_request", "request_fingerprint")
                && columnExists("t_projection_reconcile_request", "request_status")
                && columnExists("t_projection_reconcile_request", "result_json")
                && primaryKeyExists("t_projection_reconcile_request", "id")
                && indexExists("t_projection_reconcile_request",
                "uk_projection_reconcile_resource")
                && indexExists("t_projection_reconcile_request",
                "idx_projection_reconcile_operator_time")
                && indexExists("t_projection_reconcile_request",
                "idx_projection_reconcile_type_time")
                && checkConstraintExists("t_projection_reconcile_request",
                "chk_projection_reconcile_status");
    }

    public boolean searchIndexRebuildTaskReady() {
        return tableExists("t_search_index_rebuild_task")
                && columnExists("t_search_index_rebuild_task", "task_status")
                && columnExists("t_search_index_rebuild_task", "checkpoint_id")
                && columnExists("t_search_index_rebuild_task", "heartbeat_time")
                && columnExists("t_search_index_rebuild_task", "active_key")
                && primaryKeyExists("t_search_index_rebuild_task", "task_id")
                && indexExists("t_search_index_rebuild_task",
                "uk_search_index_rebuild_active")
                && indexExists("t_search_index_rebuild_task",
                "idx_search_index_rebuild_status_time")
                && indexExists("t_search_index_rebuild_task",
                "idx_search_index_rebuild_lease")
                && checkConstraintExists("t_search_index_rebuild_task",
                "chk_search_index_rebuild_status");
    }

    public boolean knowledgeLifecycleReady() {
        return tableExists("t_post_reference")
                && tableExists("t_post_knowledge_relation")
                && tableExists("t_int_post_outcome")
                && columnExists("t_int_content_suggestion", "base_version")
                && columnExists("t_int_content_suggestion", "target_scope")
                && columnExists("t_int_content_suggestion", "resolution")
                && columnExists("t_int_content_suggestion", "delivery_status")
                && columnExists("t_post_reference", "active_guard")
                && columnExists("t_post_knowledge_relation", "effective_guard")
                && columnExists("t_int_post_outcome", "effective_guard")
                && primaryKeyExists("t_post_reference", "id")
                && primaryKeyExists("t_post_knowledge_relation", "id")
                && primaryKeyExists("t_int_post_outcome", "id")
                && indexExists("t_post_reference", "uk_post_reference_active_url")
                && indexExists("t_post_knowledge_relation",
                "uk_post_knowledge_relation_effective")
                && indexExists("t_int_post_outcome", "uk_int_post_outcome_current")
                && checkConstraintExists("t_post_reference", "chk_post_reference_status")
                && checkConstraintExists("t_post_knowledge_relation",
                "chk_post_knowledge_relation_review")
                && checkConstraintExists("t_int_post_outcome",
                "chk_int_post_outcome_publication");
    }

    public boolean favoriteFolderReady() {
        return tableExists("t_int_favorite")
                && tableExists("t_int_favorite_folder")
                && columnExists("t_int_favorite", "folder_id")
                && columnExists("t_int_favorite", "sort_order")
                && columnExists("t_int_favorite", "update_time")
                && columnExists("t_int_favorite_folder", "default_active_key")
                && columnExists("t_int_favorite_folder", "name_active_key")
                && primaryKeyExists("t_int_favorite", "id")
                && primaryKeyExists("t_int_favorite_folder", "id")
                && indexExists("t_int_favorite", "uk_user_post")
                && indexExists("t_int_favorite", "idx_user_folder_sort")
                && indexExists("t_int_favorite", "idx_folder_time")
                && indexExists("t_int_favorite_folder", "uk_favorite_folder_active_default")
                && indexExists("t_int_favorite_folder", "uk_favorite_folder_active_name")
                && indexExists("t_int_favorite_folder", "idx_favorite_folder_user_sort")
                && indexExists("t_int_favorite_folder", "idx_favorite_folder_user_default");
    }

    public boolean discussionFollowReady() {
        return tableExists("t_int_discussion_follow")
                && columnExists("t_int_discussion_follow", "uid")
                && columnExists("t_int_discussion_follow", "post_id")
                && columnExists("t_int_discussion_follow", "follow_status")
                && columnExists("t_int_discussion_follow", "update_time")
                && primaryKeyExists("t_int_discussion_follow", "id")
                && indexExists("t_int_discussion_follow", "uk_discussion_follow_user_post")
                && indexExists("t_int_discussion_follow", "idx_discussion_follow_post_status")
                && indexExists("t_int_discussion_follow", "idx_discussion_follow_uid_status");
    }

    public boolean commentQualityReady() {
        return columnExists("t_int_comment", "helpful_count")
                && tableExists("t_int_comment_quality_signal")
                && tableExists("t_int_comment_helpful")
                && primaryKeyExists("t_int_comment_quality_signal", "id")
                && primaryKeyExists("t_int_comment_helpful", "id")
                && indexExists("t_int_comment_quality_signal", "uk_comment_quality_signal_comment_type")
                && indexExists("t_int_comment_quality_signal", "idx_comment_quality_signal_post_type_status")
                && indexExists("t_int_comment_quality_signal", "idx_comment_quality_signal_root_type_status")
                && indexExists("t_int_comment_quality_signal", "idx_comment_quality_signal_operator_time")
                && indexExists("t_int_comment_helpful", "uk_comment_helpful_uid_comment")
                && indexExists("t_int_comment_helpful", "idx_comment_helpful_comment_status")
                && indexExists("t_int_comment_helpful", "idx_comment_helpful_user_status")
                && indexExists("t_int_comment_helpful", "idx_comment_helpful_post_comment");
    }

    private void putColumns(Map<String, Boolean> columns, String tableName, List<String> columnNames) {
        for (String column : columnNames) {
            columns.put(tableName + "." + column, columnExists(tableName, column));
        }
    }

    private void putIndexes(Map<String, Boolean> indexes, String tableName, List<String> indexNames) {
        for (String index : indexNames) {
            indexes.put(tableName + "." + index, indexExists(tableName, index));
        }
    }

    private Map<String, Object> flywayLifecycleStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        String historyTable = flywayHistoryTableName();
        String historyTableSql = "`" + historyTable + "`";
        boolean historyTableExists = tableExists(historyTable);
        Map<String, FlywayMigrationExpectation> expectedMigrations;
        String migrationAssetError = null;
        try {
            expectedMigrations = loadCoreMigrationExpectations();
        } catch (Exception e) {
            expectedMigrations = Map.of();
            migrationAssetError = e.getMessage();
        }
        int expectedCoreMigrations = expectedMigrations.size();
        String latestExpectedVersion = expectedMigrations.keySet().stream()
                .reduce((previous, current) -> current)
                .orElse(null);
        boolean assetsReady = migrationAssetError == null
                && expectedCoreMigrations > 0
                && latestExpectedVersion != null;
        status.put("historyTable", historyTable);
        status.put("historyTableExists", historyTableExists);
        status.put("expectedCoreMigrations", expectedCoreMigrations);
        status.put("latestExpectedVersion", latestExpectedVersion);
        status.put("baselineVersion", "0");
        status.put("assetsReady", assetsReady);
        if (migrationAssetError != null) {
            status.put("assetError", migrationAssetError);
        }
        if (!historyTableExists) {
            status.put("ready", false);
            status.put("appliedCoreMigrations", 0);
            status.put("failedMigrations", 0);
            status.put("missingChecksums", expectedCoreMigrations);
            status.put("duplicateVersions", 0);
            status.put("missingVersions", List.copyOf(expectedMigrations.keySet()));
            status.put("checksumMismatches", List.of());
            status.put("scriptMismatches", List.of());
            status.put("unexpectedVersions", List.of());
            status.put("latestAppliedVersion", null);
            return status;
        }

        List<FlywayHistoryRow> historyRows = jdbcTemplate.query(
                """
                SELECT version, script, type, checksum, success
                FROM %s
                ORDER BY installed_rank
                """.formatted(historyTableSql),
                (resultSet, rowNumber) -> {
                    int checksum = resultSet.getInt("checksum");
                    boolean checksumMissing = resultSet.wasNull();
                    return new FlywayHistoryRow(
                            resultSet.getString("version"),
                            resultSet.getString("script"),
                            resultSet.getString("type"),
                            checksumMissing ? null : checksum,
                            resultSet.getBoolean("success"));
                }
        );

        Map<String, List<FlywayHistoryRow>> rowsByVersion = new HashMap<>();
        int failedMigrations = 0;
        int missingChecksums = 0;
        String latestAppliedVersion = null;
        for (FlywayHistoryRow row : historyRows) {
            if (!row.success()) {
                failedMigrations++;
            }
            if ("SQL".equals(row.type()) && row.checksum() == null) {
                missingChecksums++;
            }
            if (row.version() != null && !row.version().isBlank()) {
                rowsByVersion.computeIfAbsent(row.version(), ignored -> new ArrayList<>()).add(row);
                if ("SQL".equals(row.type()) && row.success()) {
                    latestAppliedVersion = row.version();
                }
            }
        }

        int duplicateVersions = (int) rowsByVersion.values().stream()
                .filter(rows -> rows.size() > 1)
                .count();
        List<String> missingVersions = new ArrayList<>();
        List<String> checksumMismatches = new ArrayList<>();
        List<String> scriptMismatches = new ArrayList<>();
        int appliedCoreMigrations = 0;
        for (FlywayMigrationExpectation expected : expectedMigrations.values()) {
            List<FlywayHistoryRow> matchingRows = rowsByVersion.getOrDefault(
                    expected.version(), List.of());
            if (matchingRows.isEmpty()) {
                missingVersions.add(expected.version());
                continue;
            }
            if (matchingRows.size() != 1) {
                continue;
            }
            FlywayHistoryRow actual = matchingRows.get(0);
            boolean checksumMatches = actual.checksum() != null
                    && actual.checksum() == expected.checksum();
            boolean scriptMatches = expected.script().equals(actual.script());
            if (!checksumMatches) {
                checksumMismatches.add(expected.version());
            }
            if (!scriptMatches) {
                scriptMismatches.add(expected.version());
            }
            if (actual.success()
                    && "SQL".equals(actual.type())
                    && checksumMatches
                    && scriptMatches) {
                appliedCoreMigrations++;
            }
        }

        Set<String> expectedVersions = expectedMigrations.keySet();
        Set<String> unexpectedVersionSet = new HashSet<>();
        for (FlywayHistoryRow row : historyRows) {
            if (row.version() != null
                    && !row.version().isBlank()
                    && "SQL".equals(row.type())
                    && !expectedVersions.contains(row.version())) {
                unexpectedVersionSet.add(row.version());
            }
        }
        List<String> unexpectedVersions = unexpectedVersionSet.stream().sorted().toList();

        boolean ready = assetsReady
                && appliedCoreMigrations == expectedCoreMigrations
                && failedMigrations == 0
                && missingChecksums == 0
                && duplicateVersions == 0
                && missingVersions.isEmpty()
                && checksumMismatches.isEmpty()
                && scriptMismatches.isEmpty()
                && unexpectedVersions.isEmpty()
                && latestExpectedVersion.equals(latestAppliedVersion);

        status.put("ready", ready);
        status.put("appliedCoreMigrations", appliedCoreMigrations);
        status.put("failedMigrations", failedMigrations);
        status.put("missingChecksums", missingChecksums);
        status.put("duplicateVersions", duplicateVersions);
        status.put("missingVersions", missingVersions);
        status.put("checksumMismatches", checksumMismatches);
        status.put("scriptMismatches", scriptMismatches);
        status.put("unexpectedVersions", unexpectedVersions);
        status.put("latestAppliedVersion", latestAppliedVersion);
        return status;
    }

    private Map<String, FlywayMigrationExpectation> loadCoreMigrationExpectations() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(CORE_MIGRATION_PATTERN);
        Map<String, FlywayMigrationExpectation> sorted = new TreeMap<>();
        for (Resource resource : resources) {
            String script = resource.getFilename();
            if (script == null) {
                continue;
            }
            Matcher matcher = FLYWAY_RESOURCE_NAME.matcher(script);
            if (!matcher.matches()) {
                continue;
            }
            String version = matcher.group("version");
            FlywayMigrationExpectation expectation = new FlywayMigrationExpectation(
                    version,
                    script,
                    calculateFlywayChecksum(resource));
            FlywayMigrationExpectation previous = sorted.putIfAbsent(version, expectation);
            if (previous != null
                    && (!previous.script().equals(expectation.script())
                    || previous.checksum() != expectation.checksum())) {
                throw new IllegalStateException("conflicting Flyway migration resource for " + version);
            }
        }
        return new LinkedHashMap<>(sorted);
    }

    private int calculateFlywayChecksum(Resource resource) throws Exception {
        CRC32 crc = new CRC32();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }
                crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        return (int) crc.getValue();
    }

    private boolean columnDefinitionMatches(
            String tableName,
            String columnName,
            String expectedColumnType,
            boolean expectedNullable,
            String expectedCharacterSet,
            String expectedCollation,
            String expectedExtra,
            String generationExpressionMarker) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT column_type,
                       is_nullable,
                       character_set_name,
                       collation_name,
                       extra,
                       generation_expression
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, tableName, columnName);
        if (rows.size() != 1) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        if (!expectedColumnType.equalsIgnoreCase(value(row, "column_type"))
                || expectedNullable != "YES".equalsIgnoreCase(value(row, "is_nullable"))) {
            return false;
        }
        if (expectedCharacterSet != null
                && !expectedCharacterSet.equalsIgnoreCase(value(row, "character_set_name"))) {
            return false;
        }
        if (expectedCollation != null
                && !expectedCollation.equalsIgnoreCase(value(row, "collation_name"))) {
            return false;
        }
        if (expectedExtra != null
                && !expectedExtra.equalsIgnoreCase(value(row, "extra").trim())) {
            return false;
        }
        if (generationExpressionMarker != null) {
            String normalized = normalizeSqlExpression(value(row, "generation_expression"));
            String marker = normalizeSqlExpression(generationExpressionMarker);
            if (!normalized.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    private boolean indexDefinitionMatches(
            String tableName,
            String indexName,
            boolean expectedUnique,
            String... expectedColumns) {
        List<IndexColumnDefinition> rows = jdbcTemplate.query("""
                SELECT column_name, non_unique
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                ORDER BY seq_in_index
                """, (resultSet, rowNumber) -> new IndexColumnDefinition(
                resultSet.getString("column_name"),
                resultSet.getInt("non_unique")), tableName, indexName);
        if (rows.size() != expectedColumns.length) {
            return false;
        }
        int expectedNonUnique = expectedUnique ? 0 : 1;
        for (int index = 0; index < expectedColumns.length; index++) {
            IndexColumnDefinition row = rows.get(index);
            if (!expectedColumns[index].equals(row.columnName())
                    || row.nonUnique() != expectedNonUnique) {
                return false;
            }
        }
        return true;
    }

    private boolean foreignKeyDefinitionMatches(
            String tableName,
            String constraintName,
            String expectedColumns,
            String expectedReferencedTable,
            String expectedReferencedColumns,
            String expectedDeleteRule,
            String expectedUpdateRule) {
        List<ForeignKeyColumnDefinition> rows = jdbcTemplate.query("""
                SELECT kcu.column_name,
                       kcu.referenced_table_name,
                       kcu.referenced_column_name,
                       rc.delete_rule,
                       rc.update_rule
                FROM information_schema.key_column_usage kcu
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = kcu.constraint_schema
                 AND rc.table_name = kcu.table_name
                 AND rc.constraint_name = kcu.constraint_name
                WHERE kcu.constraint_schema = DATABASE()
                  AND kcu.table_name = ?
                  AND kcu.constraint_name = ?
                  AND kcu.referenced_table_name IS NOT NULL
                ORDER BY kcu.ordinal_position
                """, (resultSet, rowNumber) -> new ForeignKeyColumnDefinition(
                resultSet.getString("column_name"),
                resultSet.getString("referenced_table_name"),
                resultSet.getString("referenced_column_name"),
                resultSet.getString("delete_rule"),
                resultSet.getString("update_rule")), tableName, constraintName);
        String[] columns = expectedColumns.split(",");
        String[] referencedColumns = expectedReferencedColumns.split(",");
        if (rows.size() != columns.length || columns.length != referencedColumns.length) {
            return false;
        }
        for (int index = 0; index < columns.length; index++) {
            ForeignKeyColumnDefinition row = rows.get(index);
            if (!columns[index].equals(row.columnName())
                    || !expectedReferencedTable.equals(row.referencedTableName())
                    || !referencedColumns[index].equals(row.referencedColumnName())
                    || !expectedDeleteRule.equalsIgnoreCase(row.deleteRule())
                    || !expectedUpdateRule.equalsIgnoreCase(row.updateRule())) {
                return false;
            }
        }
        return true;
    }

    private static String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalizeSqlExpression(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("[\\s`(),']", "")
                .toLowerCase()
                .replace("_utf8mb4", "")
                .replace("_utf8", "")
                .replace("_ascii", "");
    }

    private String flywayHistoryTableName() {
        String configured = flywayHistoryTable == null ? "" : flywayHistoryTable.trim();
        return configured.matches("[A-Za-z0-9_]+") ? configured : "flyway_schema_history";
    }

    private int queryCount(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private List<String> missingItems(Map<String, Boolean>... groups) {
        List<String> missing = new ArrayList<>();
        for (Map<String, Boolean> group : groups) {
            group.entrySet().stream()
                    .filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .forEach(missing::add);
        }
        return missing;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private boolean primaryKeyExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                  AND column_key = 'PRI'
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private boolean foreignKeyExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = ?
                  AND constraint_name = ?
                """, Integer.class, tableName, constraintName);
        return count != null && count > 0;
    }

    private boolean checkConstraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = ?
                  AND constraint_name = ?
                  AND constraint_type = 'CHECK'
                """, Integer.class, tableName, constraintName);
        return count != null && count == 1;
    }

    private boolean triggerDefinitionMatches(
            String tableName,
            String triggerName,
            String timing,
            String event,
            String actionMarker) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.triggers
                WHERE trigger_schema = DATABASE()
                  AND event_object_table = ?
                  AND trigger_name = ?
                  AND action_timing = ?
                  AND event_manipulation = ?
                  AND LOWER(action_statement) LIKE ?
                """, Integer.class,
                tableName,
                triggerName,
                timing,
                event,
                "%" + actionMarker.toLowerCase() + "%");
        return count != null && count == 1;
    }

    private record FlywayMigrationExpectation(String version, String script, int checksum) {
    }

    private record FlywayHistoryRow(
            String version,
            String script,
            String type,
            Integer checksum,
            boolean success) {
    }

    private record IndexColumnDefinition(String columnName, int nonUnique) {
    }

    private record ForeignKeyColumnDefinition(
            String columnName,
            String referencedTableName,
            String referencedColumnName,
            String deleteRule,
            String updateRule) {
    }
}
