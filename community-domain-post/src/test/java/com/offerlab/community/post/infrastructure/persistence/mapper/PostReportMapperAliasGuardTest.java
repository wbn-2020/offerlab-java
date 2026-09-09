package com.offerlab.community.post.infrastructure.persistence.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostReportMapper.selectRecent 的 SQL 守卫：
 * 该查询 LEFT JOIN t_post_extension（含 update_time 等同名列），SELECT 列表必须
 * 全部带 r. 表别名前缀，否则 MySQL 会报 "Column ... is ambiguous" 并导致
 * /api/v1/posts/admin/reports 返回 500（20001）。2026-09-09 线上实测复现后修复。
 */
class PostReportMapperAliasGuardTest {

    @Test
    void selectRecentMustPrefixAllSelectedColumnsWithReportAlias() throws Exception {
        Path path = Path.of("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostReportMapper.java");
        String source = Files.readString(path);
        assertTrue(source.contains("LEFT JOIN t_post_extension e ON e.post_id = r.post_id"),
                "selectRecent must keep joining t_post_extension for domain filtering");
        assertTrue(source.contains("SELECT r.id,"), "selectRecent must prefix id with r.");
        assertTrue(source.contains("r.update_time AS updateTime"),
                "selectRecent must prefix update_time with r. (ambiguous with t_post_extension.update_time otherwise)");
        assertTrue(source.contains("r.create_time AS createTime"), "selectRecent must prefix create_time with r.");
        assertTrue(source.contains("r.reviewer_uid AS reviewerUid"), "selectRecent must prefix reviewer_uid with r.");
        assertTrue(source.contains("r.review_note AS reviewNote"), "selectRecent must prefix review_note with r.");
    }
}
