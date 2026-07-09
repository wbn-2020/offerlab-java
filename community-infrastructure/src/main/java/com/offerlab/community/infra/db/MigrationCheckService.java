package com.offerlab.community.infra.db;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MigrationCheckService {
    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> governanceStatus() {
        Map<String, Boolean> tables = new LinkedHashMap<>();
        for (String table : List.of(
                "t_admin_audit_log",
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
                "t_post_extension",
                "t_user_task_state",
                "t_content_assist_record",
                "t_content_series",
                "t_content_series_post",
                "t_feed_recommend_support_stat",
                "t_expert_cert_application",
                "t_operation_curation_item",
                "t_operation_slot",
                "t_operation_slot_item",
                "t_operation_topic",
                "t_operation_topic_section",
                "t_int_contact_request",
                "t_user_privacy_setting",
                "t_int_discussion_follow",
                "t_int_favorite",
                "t_int_favorite_folder",
                "t_int_comment_quality_signal",
                "t_int_comment_helpful"
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
        Map<String, Boolean> indexes = new LinkedHashMap<>();
        indexes.put("t_post_report.idx_post_reporter_status", indexExists("t_post_report", "idx_post_reporter_status"));
        indexes.put("t_comment_report.idx_comment_reporter_status", indexExists("t_comment_report", "idx_comment_reporter_status"));
        indexes.put("t_interview_question.idx_status_time", indexExists("t_interview_question", "idx_status_time"));
        indexes.put("t_tag.idx_tag_status_recommend", indexExists("t_tag", "idx_tag_status_recommend"));
        indexes.put("t_tag.idx_tag_merge_target", indexExists("t_tag", "idx_tag_merge_target"));
        indexes.put("t_community_topic.uk_topic_slug", indexExists("t_community_topic", "uk_topic_slug"));
        indexes.put("t_community_topic.idx_topic_status_sort", indexExists("t_community_topic", "idx_topic_status_sort"));
        indexes.put("t_community_topic.idx_topic_featured_sort", indexExists("t_community_topic", "idx_topic_featured_sort"));
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
        Map<String, Boolean> constraints = new LinkedHashMap<>();
        constraints.put("t_domain_moderator.PRIMARY(id)", primaryKeyExists("t_domain_moderator", "id"));
        constraints.put("t_domain_config.PRIMARY(domain)", primaryKeyExists("t_domain_config", "domain"));
        constraints.put("t_growth_event.PRIMARY(id)", primaryKeyExists("t_growth_event", "id"));
        constraints.put("t_user_task_state.PRIMARY(id)", primaryKeyExists("t_user_task_state", "id"));
        constraints.put("t_content_assist_record.PRIMARY(id)", primaryKeyExists("t_content_assist_record", "id"));
        constraints.put("t_content_series.PRIMARY(id)", primaryKeyExists("t_content_series", "id"));
        constraints.put("t_content_series_post.PRIMARY(id)", primaryKeyExists("t_content_series_post", "id"));
        constraints.put("t_feed_recommend_support_stat.PRIMARY(id)", primaryKeyExists("t_feed_recommend_support_stat", "id"));
        constraints.put("t_expert_cert_application.PRIMARY(id)", primaryKeyExists("t_expert_cert_application", "id"));
        constraints.put("t_operation_curation_item.PRIMARY(id)", primaryKeyExists("t_operation_curation_item", "id"));
        constraints.put("t_operation_slot.PRIMARY(id)", primaryKeyExists("t_operation_slot", "id"));
        constraints.put("t_operation_slot_item.PRIMARY(id)", primaryKeyExists("t_operation_slot_item", "id"));
        constraints.put("t_operation_topic.PRIMARY(id)", primaryKeyExists("t_operation_topic", "id"));
        constraints.put("t_operation_topic_section.PRIMARY(id)", primaryKeyExists("t_operation_topic_section", "id"));
        constraints.put("t_int_contact_request.PRIMARY(id)", primaryKeyExists("t_int_contact_request", "id"));
        constraints.put("t_user_privacy_setting.PRIMARY(user_id)", primaryKeyExists("t_user_privacy_setting", "user_id"));
        constraints.put("t_int_discussion_follow.PRIMARY(id)", primaryKeyExists("t_int_discussion_follow", "id"));
        constraints.put("t_int_favorite.PRIMARY(id)", primaryKeyExists("t_int_favorite", "id"));
        constraints.put("t_int_favorite_folder.PRIMARY(id)", primaryKeyExists("t_int_favorite_folder", "id"));
        constraints.put("t_int_comment_quality_signal.PRIMARY(id)", primaryKeyExists("t_int_comment_quality_signal", "id"));
        constraints.put("t_int_comment_helpful.PRIMARY(id)", primaryKeyExists("t_int_comment_helpful", "id"));
        boolean ready = tables.values().stream().allMatch(Boolean::booleanValue)
                && columns.values().stream().allMatch(Boolean::booleanValue)
                && indexes.values().stream().allMatch(Boolean::booleanValue)
                && constraints.values().stream().allMatch(Boolean::booleanValue);
        List<String> missing = missingItems(tables, columns, indexes, constraints);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", ready);
        status.put("status", ready ? "UP" : "BLOCKED_BY_SCHEMA");
        status.put("tables", tables);
        status.put("columns", columns);
        status.put("indexes", indexes);
        status.put("constraints", constraints);
        status.put("missing", missing);
        status.put("migration", "See status.migrations for the required schema files.");
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
                "db/migration/20260709_retry_task_claim_indexes.sql"
        ));
        if (!ready) {
            status.put("message", "数据库迁移未补齐，治理、领域配置、增长埋点、内容助手、系列能力和专家认证试点会降级或被阻断。");
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
                && indexExists("t_growth_event", "idx_growth_event_content_time");
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
}
