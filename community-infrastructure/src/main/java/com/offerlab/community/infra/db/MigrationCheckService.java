package com.offerlab.community.infra.db;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
                "t_comment_report"
        )) {
            tables.put(table, tableExists(table));
        }
        Map<String, Boolean> indexes = new LinkedHashMap<>();
        indexes.put("t_post_report.idx_post_reporter_status", indexExists("t_post_report", "idx_post_reporter_status"));
        indexes.put("t_comment_report.idx_comment_reporter_status", indexExists("t_comment_report", "idx_comment_reporter_status"));
        indexes.put("t_interview_question.idx_status_time", indexExists("t_interview_question", "idx_status_time"));
        boolean ready = tables.values().stream().allMatch(Boolean::booleanValue)
                && indexes.values().stream().allMatch(Boolean::booleanValue);
        return Map.of("ready", ready, "tables", tables, "indexes", indexes);
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
