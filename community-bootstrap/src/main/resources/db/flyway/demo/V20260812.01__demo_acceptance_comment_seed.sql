-- Deterministic acceptance comments for the fixed demo post.
-- This migration belongs to the isolated demo Flyway stream and must be
-- applied explicitly with flyway_demo_schema_history. It is never part of the
-- production/core application startup path.
SET NAMES utf8mb4;

SET @acceptance_comment_post_id := 991100000000000010;
SET @acceptance_comment_author_uid := (
    SELECT author_id
    FROM t_post_main
    WHERE id = @acceptance_comment_post_id
      AND visibility = 1
      AND post_status = 1
      AND is_deleted = 0
    LIMIT 1
);

SET @acceptance_comment_seed_guard_sql := IF(
    @acceptance_comment_author_uid IS NULL,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''acceptance comment seed requires the fixed public demo post''',
    'DO 0'
);
PREPARE acceptance_comment_seed_guard_stmt FROM @acceptance_comment_seed_guard_sql;
EXECUTE acceptance_comment_seed_guard_stmt;
DEALLOCATE PREPARE acceptance_comment_seed_guard_stmt;

SET @acceptance_comment_identity_conflicts := (
    SELECT COUNT(*)
    FROM t_int_comment
    WHERE id BETWEEN 991120000000000001 AND 991120000000000021
      AND (
          post_id <> @acceptance_comment_post_id
          OR post_author_id <> @acceptance_comment_author_uid
          OR author_id <> @acceptance_comment_author_uid
          OR root_id <> 0
          OR parent_id <> 0
      )
);
SET @acceptance_comment_seed_guard_sql := IF(
    @acceptance_comment_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''acceptance comment seed identity conflict''',
    'DO 0'
);
PREPARE acceptance_comment_seed_guard_stmt FROM @acceptance_comment_seed_guard_sql;
EXECUTE acceptance_comment_seed_guard_stmt;
DEALLOCATE PREPARE acceptance_comment_seed_guard_stmt;

INSERT INTO t_int_comment
    (id, post_id, post_author_id, author_id, root_id, parent_id, reply_to_uid,
     content, like_count, helpful_count, comment_status, create_time, update_time, is_deleted)
VALUES
    (991120000000000001, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '固定时间和固定触发确实能减少每天重新做决定的消耗。', 2, 1, 1, '2026-07-21 08:00:00.000', '2026-07-21 08:00:00.000', 0),
    (991120000000000002, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '二十五分钟专注适合启动困难的时候，进入状态后也可以适当延长。', 4, 2, 1, '2026-07-21 08:05:00.000', '2026-07-21 08:05:00.000', 0),
    (991120000000000003, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '十分钟回忆比单纯重读更容易发现自己没有真正理解的部分。', 6, 3, 1, '2026-07-21 08:10:00.000', '2026-07-21 08:10:00.000', 0),
    (991120000000000004, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '每周复盘最好只保留一个明确调整，否则系统会越改越复杂。', 1, 4, 1, '2026-07-21 08:15:00.000', '2026-07-21 08:15:00.000', 0),
    (991120000000000005, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '删除低价值打卡项目这一点很重要，记录本身不应该成为负担。', 3, 5, 1, '2026-07-21 08:20:00.000', '2026-07-21 08:20:00.000', 0),
    (991120000000000006, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '晨间被临时事务打断时，可以准备一个十五分钟的最低可行版本。', 5, 6, 1, '2026-07-21 08:25:00.000', '2026-07-21 08:25:00.000', 0),
    (991120000000000007, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '固定地点也能形成触发线索，比只依赖意志力稳定得多。', 7, 0, 1, '2026-07-21 08:30:00.000', '2026-07-21 08:30:00.000', 0),
    (991120000000000008, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '回忆结束后立刻写下一个问题，第二天更容易继续。', 0, 1, 1, '2026-07-21 08:35:00.000', '2026-07-21 08:35:00.000', 0),
    (991120000000000009, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '计划中断后先恢复最小节奏，再分析原因，心理负担会更小。', 2, 2, 1, '2026-07-21 08:40:00.000', '2026-07-21 08:40:00.000', 0),
    (991120000000000010, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '半年维度的复盘比连续天数更能说明系统是否可持续。', 4, 3, 1, '2026-07-21 08:45:00.000', '2026-07-21 08:45:00.000', 0),
    (991120000000000011, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '周末作息变化较大时，触发条件可以从时间改成早餐结束。', 6, 4, 1, '2026-07-21 08:50:00.000', '2026-07-21 08:50:00.000', 0),
    (991120000000000012, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '专注阶段只准备一个材料入口，可以减少切换工具造成的分心。', 1, 5, 1, '2026-07-21 08:55:00.000', '2026-07-21 08:55:00.000', 0),
    (991120000000000013, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '复盘记录建议同时写保留项和删除项，不要只增加新规则。', 3, 6, 1, '2026-07-21 09:00:00.000', '2026-07-21 09:00:00.000', 0),
    (991120000000000014, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '睡眠不足时降低学习强度，比勉强完成后连续停摆更稳妥。', 5, 0, 1, '2026-07-21 09:05:00.000', '2026-07-21 09:05:00.000', 0),
    (991120000000000015, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '把手机放在另一个房间，是我实践中最有效的环境调整。', 7, 1, 1, '2026-07-21 09:10:00.000', '2026-07-21 09:10:00.000', 0),
    (991120000000000016, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '如果当天没有新内容，回顾旧问题也能保持连续的学习线索。', 0, 2, 1, '2026-07-21 09:15:00.000', '2026-07-21 09:15:00.000', 0),
    (991120000000000017, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '学习系统应该服务目标，目标变化后流程也需要允许被替换。', 2, 3, 1, '2026-07-21 09:20:00.000', '2026-07-21 09:20:00.000', 0),
    (991120000000000018, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '用问题清单代替章节进度，更容易保持主动学习。', 4, 4, 1, '2026-07-21 09:25:00.000', '2026-07-21 09:25:00.000', 0),
    (991120000000000019, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '偶尔错过一次不需要补偿两次，直接回到正常节奏即可。', 6, 5, 1, '2026-07-21 09:30:00.000', '2026-07-21 09:30:00.000', 0),
    (991120000000000020, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '可恢复性比完美执行更重要，这也是半年后还能继续的关键。', 1, 6, 1, '2026-07-21 09:35:00.000', '2026-07-21 09:35:00.000', 0),
    (991120000000000021, @acceptance_comment_post_id, @acceptance_comment_author_uid, @acceptance_comment_author_uid, 0, 0, NULL, '这套方法的价值在于能够删减，而不是不断累积更多步骤。', 3, 0, 1, '2026-07-21 09:40:00.000', '2026-07-21 09:40:00.000', 0)
ON DUPLICATE KEY UPDATE
    content = VALUES(content),
    like_count = VALUES(like_count),
    helpful_count = VALUES(helpful_count),
    comment_status = VALUES(comment_status),
    create_time = VALUES(create_time),
    update_time = VALUES(update_time),
    is_deleted = VALUES(is_deleted);

INSERT INTO t_post_counter
    (post_id, view_count, like_count, comment_count, favorite_count, share_count)
VALUES
    (@acceptance_comment_post_id, 0, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
    post_id = VALUES(post_id);

UPDATE t_post_counter
SET comment_count = (
        SELECT COUNT(*)
        FROM t_int_comment
        WHERE post_id = @acceptance_comment_post_id
          AND comment_status = 1
          AND is_deleted = 0
    ),
    update_time = CURRENT_TIMESTAMP(3)
WHERE post_id = @acceptance_comment_post_id;

