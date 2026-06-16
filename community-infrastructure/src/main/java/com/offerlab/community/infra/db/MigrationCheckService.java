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
                "t_ai_extract_task"
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
        boolean ready = tables.values().stream().allMatch(Boolean::booleanValue)
                && columns.values().stream().allMatch(Boolean::booleanValue)
                && indexes.values().stream().allMatch(Boolean::booleanValue);
        List<String> missing = missingItems(tables, columns, indexes);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ready", ready);
        status.put("status", ready ? "UP" : "BLOCKED_BY_SCHEMA");
        status.put("tables", tables);
        status.put("columns", columns);
        status.put("indexes", indexes);
        status.put("missing", missing);
        status.put("migration", "db/migration/20260608_tag_governance.sql");
        status.put("migrations", List.of(
                "db/migration/20260608_tag_governance.sql",
                "db/migration/20260605_ai_extract_task_metrics.sql",
                "db/migration/20260608_community_topics.sql",
                "db/migration/20260608_review_queue.sql",
                "db/migration/20260608_mock_interview_ai_review_transparency.sql"
        ));
        if (!ready) {
            status.put("message", "数据库迁移未补齐，标签治理、社区专题、审核队列、发布和搜索会降级或被阻断");
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
}
