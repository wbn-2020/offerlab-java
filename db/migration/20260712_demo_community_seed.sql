-- 20260712_demo_community_seed.sql
-- Non-destructive comprehensive community seed for existing demo databases.
SET NAMES utf8mb4;

SET @community_demo_uid := COALESCE(
    (SELECT id FROM t_user_account WHERE email = 'demo.author@offerlab.local' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE email = 'admin' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE is_deleted = 0 ORDER BY id LIMIT 1)
);

SET @community_seed_guard_sql := IF(
    @community_demo_uid IS NULL,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed requires an existing active author''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;

SET @community_seed_identity_conflicts := (
    SELECT COUNT(*)
    FROM t_tag
    WHERE (id = 991000000000000001 AND tag_name <> '开源工具')
       OR (id = 991000000000000002 AND tag_name <> '数字安全')
       OR (id = 991000000000000003 AND tag_name <> '职业成长')
       OR (id = 991000000000000004 AND tag_name <> '沟通协作')
       OR (id = 991000000000000005 AND tag_name <> '财务安全')
       OR (id = 991000000000000006 AND tag_name <> '风险教育')
       OR (id = 991000000000000007 AND tag_name <> '学习方法')
       OR (id = 991000000000000008 AND tag_name <> '阅读笔记')
       OR (id = 991000000000000009 AND tag_name <> '生活经验')
       OR (id = 991000000000000010 AND tag_name <> '健康管理')
       OR (tag_name = '开源工具' AND is_deleted = 0 AND id <> 991000000000000001)
       OR (tag_name = '数字安全' AND is_deleted = 0 AND id <> 991000000000000002)
       OR (tag_name = '职业成长' AND is_deleted = 0 AND id <> 991000000000000003)
       OR (tag_name = '沟通协作' AND is_deleted = 0 AND id <> 991000000000000004)
       OR (tag_name = '财务安全' AND is_deleted = 0 AND id <> 991000000000000005)
       OR (tag_name = '风险教育' AND is_deleted = 0 AND id <> 991000000000000006)
       OR (tag_name = '学习方法' AND is_deleted = 0 AND id <> 991000000000000007)
       OR (tag_name = '阅读笔记' AND is_deleted = 0 AND id <> 991000000000000008)
       OR (tag_name = '生活经验' AND is_deleted = 0 AND id <> 991000000000000009)
       OR (tag_name = '健康管理' AND is_deleted = 0 AND id <> 991000000000000010)
);
SET @community_seed_guard_sql := IF(
    @community_seed_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed tag identity conflict''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;

INSERT INTO t_tag
    (id, tag_name, tag_type, use_count, is_official, tag_status, recommended)
VALUES
    (991000000000000001, '开源工具', 4, 3, 1, 1, 1),
    (991000000000000002, '数字安全', 4, 3, 1, 1, 1),
    (991000000000000003, '职业成长', 4, 3, 1, 1, 1),
    (991000000000000004, '沟通协作', 4, 3, 1, 1, 1),
    (991000000000000005, '财务安全', 4, 3, 1, 1, 1),
    (991000000000000006, '风险教育', 4, 3, 1, 1, 1),
    (991000000000000007, '学习方法', 4, 3, 1, 1, 1),
    (991000000000000008, '阅读笔记', 4, 3, 1, 1, 1),
    (991000000000000009, '生活经验', 4, 3, 1, 1, 1),
    (991000000000000010, '健康管理', 4, 3, 1, 1, 1)
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    tag_type = VALUES(tag_type),
    use_count = GREATEST(use_count, VALUES(use_count)),
    is_official = VALUES(is_official),
    tag_status = VALUES(tag_status),
    recommended = VALUES(recommended),
    merge_target_id = NULL,
    is_deleted = 0,
    update_time = CURRENT_TIMESTAMP(3);

SET @community_seed_identity_conflicts := (
    SELECT COUNT(*)
    FROM t_community_topic
    WHERE (id = 991500000000000001 AND slug <> 'digital-life-open-tools')
       OR (id = 991500000000000002 AND slug <> 'career-transition-field-notes')
       OR (id = 991500000000000003 AND slug <> 'personal-finance-risk-basics')
       OR (id = 991500000000000004 AND slug <> 'learning-systems')
       OR (id = 991500000000000005 AND slug <> 'everyday-life-practice')
       OR (slug = 'digital-life-open-tools' AND is_deleted = 0 AND id <> 991500000000000001)
       OR (slug = 'career-transition-field-notes' AND is_deleted = 0 AND id <> 991500000000000002)
       OR (slug = 'personal-finance-risk-basics' AND is_deleted = 0 AND id <> 991500000000000003)
       OR (slug = 'learning-systems' AND is_deleted = 0 AND id <> 991500000000000004)
       OR (slug = 'everyday-life-practice' AND is_deleted = 0 AND id <> 991500000000000005)
);
SET @community_seed_guard_sql := IF(
    @community_seed_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed topic identity conflict''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;

INSERT INTO t_community_topic (
    id, slug, topic_name, description, topic_type, cover_url,
    sort_order, featured, topic_status, created_by, updated_by
) VALUES
    (991500000000000001, 'digital-life-open-tools', '数字生活与开源工具',
     '分享普通人能复用的开源工具、账号安全、数据备份和数字生活实践。',
     'resource', NULL, 125, 1, 1, @community_demo_uid, @community_demo_uid),
    (991500000000000002, 'career-transition-field-notes', '职业转型田野笔记',
     '讨论转岗、空窗期、跨职能协作和工作方式变化中的真实经验。',
     'scenario', NULL, 124, 1, 1, @community_demo_uid, @community_demo_uid),
    (991500000000000003, 'personal-finance-risk-basics', '个人财务风险基础',
     '只做预算、应急金、资产配置和风险识别教育，不构成投资建议。',
     'resource', NULL, 123, 1, 1, @community_demo_uid, @community_demo_uid),
    (991500000000000004, 'learning-systems', '可持续学习系统',
     '沉淀阅读、课程、笔记、复习和长期学习计划的可复用方法。',
     'project', NULL, 122, 1, 1, @community_demo_uid, @community_demo_uid),
    (991500000000000005, 'everyday-life-practice', '日常生活实践',
     '围绕居住、健康、关系和生活选择展开具体、友善、可验证的讨论。',
     'scenario', NULL, 121, 1, 1, @community_demo_uid, @community_demo_uid)
ON DUPLICATE KEY UPDATE
    topic_name = VALUES(topic_name),
    description = VALUES(description),
    topic_type = VALUES(topic_type),
    cover_url = VALUES(cover_url),
    sort_order = VALUES(sort_order),
    featured = VALUES(featured),
    topic_status = VALUES(topic_status),
    updated_by = VALUES(updated_by),
    is_deleted = 0,
    update_time = CURRENT_TIMESTAMP(3);

SET @community_seed_identity_conflicts := (
    SELECT COUNT(*)
    FROM (
        SELECT 991510000000000001 AS id, 991500000000000001 AS topic_id, 991000000000000001 AS tag_id
        UNION ALL SELECT 991510000000000002, 991500000000000001, 991000000000000002
        UNION ALL SELECT 991510000000000003, 991500000000000002, 991000000000000003
        UNION ALL SELECT 991510000000000004, 991500000000000002, 991000000000000004
        UNION ALL SELECT 991510000000000005, 991500000000000003, 991000000000000005
        UNION ALL SELECT 991510000000000006, 991500000000000003, 991000000000000006
        UNION ALL SELECT 991510000000000007, 991500000000000004, 991000000000000007
        UNION ALL SELECT 991510000000000008, 991500000000000004, 991000000000000008
        UNION ALL SELECT 991510000000000009, 991500000000000005, 991000000000000009
        UNION ALL SELECT 991510000000000010, 991500000000000005, 991000000000000010
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_community_topic_tag existing
        WHERE existing.id = expected.id
          AND (existing.topic_id <> expected.topic_id OR existing.tag_id <> expected.tag_id)
    )
       OR EXISTS (
        SELECT 1
        FROM t_community_topic_tag existing
        WHERE existing.topic_id = expected.topic_id
          AND existing.tag_id = expected.tag_id
          AND existing.id <> expected.id
    )
);
SET @community_seed_guard_sql := IF(
    @community_seed_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed topic tag identity conflict''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;

INSERT IGNORE INTO t_community_topic_tag (id, topic_id, tag_id) VALUES
    (991510000000000001, 991500000000000001, 991000000000000001),
    (991510000000000002, 991500000000000001, 991000000000000002),
    (991510000000000003, 991500000000000002, 991000000000000003),
    (991510000000000004, 991500000000000002, 991000000000000004),
    (991510000000000005, 991500000000000003, 991000000000000005),
    (991510000000000006, 991500000000000003, 991000000000000006),
    (991510000000000007, 991500000000000004, 991000000000000007),
    (991510000000000008, 991500000000000004, 991000000000000008),
    (991510000000000009, 991500000000000005, 991000000000000009),
    (991510000000000010, 991500000000000005, 991000000000000010);

SET @community_seed_identity_conflicts := (
    SELECT COUNT(*)
    FROM t_post_main
    WHERE (id = 991100000000000001 AND (title <> '数字生活应急包清单：备份、密码与双重验证' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000002 AND (title <> '开源软件要不要默认收集遥测数据' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000003 AND (title <> '把旧电脑改成家庭资料站的一次实践' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000004 AND (title <> '从全职工作到自由职业三个月的真实账本' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000005 AND (title <> '职业空窗期如何向家人和招聘方解释' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000006 AND (title <> '第一次带跨职能项目失败后的复盘' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000007 AND (title <> '建立家庭应急金前的六项检查' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000008 AND (title <> '指数基金定投前先确认哪些风险' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000009 AND (title <> '租房还是买房，先讨论现金流和生活选择' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000010 AND (title <> '我的晨间一小时学习系统运行了半年' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000011 AND (title <> '读一本非虚构书的三层笔记模板' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000012 AND (title <> '连续学习计划中断后的复盘' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000013 AND (title <> '合租公共空间怎么制定不伤人的规则' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000014 AND (title <> '要不要搬去离公司更远但更舒适的房子' OR author_id <> @community_demo_uid))
       OR (id = 991100000000000015 AND (title <> '周末无屏幕半天带来的生活观察' OR author_id <> @community_demo_uid))
);
SET @community_seed_guard_sql := IF(
    @community_seed_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed post identity conflict''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;

INSERT INTO t_post_main
    (id, author_id, post_type, title, content, cover_url, visibility, post_status, create_time, update_time, is_deleted)
VALUES
    -- COMMUNITY_DEMO_POST id=991100000000000001 domain=1 type=14
    (991100000000000001, @community_demo_uid, 14, '数字生活应急包清单：备份、密码与双重验证', '这份清单从重要文件三份备份、密码管理器、双重验证恢复码和设备丢失预案四部分展开，适合普通家庭每半年检查一次。', NULL, 1, 1, NOW(3) - INTERVAL 15 DAY, NOW(3) - INTERVAL 2 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000002 domain=1 type=16
    (991100000000000002, @community_demo_uid, 16, '开源软件要不要默认收集遥测数据', '讨论重点不是简单支持或反对遥测，而是默认关闭还是默认开启、是否充分告知、数据能否本地查看，以及用户能不能真正撤回授权。', NULL, 1, 1, NOW(3) - INTERVAL 14 DAY, NOW(3) - INTERVAL 2 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000003 domain=1 type=15
    (991100000000000003, @community_demo_uid, 15, '把旧电脑改成家庭资料站的一次实践', '我记录了硬盘健康检查、局域网共享、自动备份和断电恢复的完整过程，也保留了失败方案和维护成本，方便非专业用户判断是否值得照做。', NULL, 1, 1, NOW(3) - INTERVAL 13 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000004 domain=2 type=15
    (991100000000000004, @community_demo_uid, 15, '从全职工作到自由职业三个月的真实账本', '这不是成功学总结，而是对收入波动、客户沟通、工作边界和作息变化的逐周记录，并说明哪些准备不足导致了额外压力。', NULL, 1, 1, NOW(3) - INTERVAL 12 DAY, NOW(3) - INTERVAL 2 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000005 domain=2 type=13
    (991100000000000005, @community_demo_uid, 13, '职业空窗期如何向家人和招聘方解释', '希望收集不同处境下的表达方式：既不把空窗期包装成完美故事，也能说明休整、照护、学习或求职过程中真正完成的事情。', NULL, 1, 1, NOW(3) - INTERVAL 11 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000006 domain=2 type=11
    (991100000000000006, @community_demo_uid, 11, '第一次带跨职能项目失败后的复盘', '问题不只在排期，还包括目标定义含糊、决策记录缺失和风险升级太晚。复盘最后给出下一次项目启动会要使用的检查表。', NULL, 1, 1, NOW(3) - INTERVAL 10 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000007 domain=5 type=14
    (991100000000000007, @community_demo_uid, 14, '建立家庭应急金前的六项检查', '先核对必要支出、收入稳定性、保险缺口、负债成本和资金流动性，再决定应急金规模。内容仅作风险教育，不构成投资建议。', NULL, 1, 1, NOW(3) - INTERVAL 9 DAY, NOW(3) - INTERVAL 2 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000008 domain=5 type=13
    (991100000000000008, @community_demo_uid, 13, '指数基金定投前先确认哪些风险', '讨论波动、费用、跟踪误差和长期资金安排，也提醒投资可能发生本金损失。请依据自己的风险承受能力独立判断，不构成投资建议。', NULL, 1, 1, NOW(3) - INTERVAL 8 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000009 domain=5 type=16
    (991100000000000009, @community_demo_uid, 16, '租房还是买房，先讨论现金流和生活选择', '把通勤、家庭计划、城市流动性、首付占用和压力测试放在同一张表里讨论。内容不提供交易结论，只用于识别长期财务风险。', NULL, 1, 1, NOW(3) - INTERVAL 7 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000010 domain=3 type=15
    (991100000000000010, @community_demo_uid, 15, '我的晨间一小时学习系统运行了半年', '学习系统由固定触发、二十五分钟专注、十分钟回忆和每周复盘组成。半年后保留了有效环节，也删掉了让计划越来越重的打卡项目。', NULL, 1, 1, NOW(3) - INTERVAL 6 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000011 domain=3 type=14
    (991100000000000011, @community_demo_uid, 14, '读一本非虚构书的三层笔记模板', '第一层记录原文位置，第二层写自己的解释，第三层连接到问题和行动。模板同时说明哪些书不适合做重笔记，避免形式超过内容。', NULL, 1, 1, NOW(3) - INTERVAL 5 DAY, NOW(3) - INTERVAL 1 DAY, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000012 domain=3 type=11
    (991100000000000012, @community_demo_uid, 11, '连续学习计划中断后的复盘', '连续记录中断后，我检查了任务粒度、环境阻力和休息不足三个原因，把目标从每天完成改成每周可恢复，降低一次中断带来的放弃成本。', NULL, 1, 1, NOW(3) - INTERVAL 4 DAY, NOW(3) - INTERVAL 12 HOUR, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000013 domain=4 type=13
    (991100000000000013, @community_demo_uid, 13, '合租公共空间怎么制定不伤人的规则', '想听听大家如何讨论清洁频率、物品边界、访客和安静时间。重点是把指责改成可执行约定，并保留定期重新协商的空间。', NULL, 1, 1, NOW(3) - INTERVAL 3 DAY, NOW(3) - INTERVAL 12 HOUR, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000014 domain=4 type=16
    (991100000000000014, @community_demo_uid, 16, '要不要搬去离公司更远但更舒适的房子', '欢迎从通勤时间、睡眠、租金、社交支持和居住稳定性几个角度讨论，不把任何一种生活选择当成标准答案。', NULL, 1, 1, NOW(3) - INTERVAL 2 DAY, NOW(3) - INTERVAL 6 HOUR, 0),
    -- COMMUNITY_DEMO_POST id=991100000000000015 domain=4 type=15
    (991100000000000015, @community_demo_uid, 15, '周末无屏幕半天带来的生活观察', '连续四周保留半天不用社交媒体，我记录了焦虑、注意力、散步和与家人交流的变化，也说明这种安排并不适合所有人的工作和照护节奏。', NULL, 1, 1, NOW(3) - INTERVAL 1 DAY, NOW(3), 0)
ON DUPLICATE KEY UPDATE
    author_id = VALUES(author_id),
    post_type = VALUES(post_type),
    title = VALUES(title),
    content = VALUES(content),
    cover_url = VALUES(cover_url),
    visibility = VALUES(visibility),
    post_status = VALUES(post_status),
    is_deleted = VALUES(is_deleted),
    update_time = VALUES(update_time);

INSERT INTO t_post_extension (post_id, post_type, ext_json) VALUES
    (991100000000000001, 14, JSON_OBJECT('domain', 1, 'seed', 'community_seed', 'format', 'RESOURCE')),
    (991100000000000002, 16, JSON_OBJECT('domain', 1, 'seed', 'community_seed', 'format', 'DISCUSSION')),
    (991100000000000003, 15, JSON_OBJECT('domain', 1, 'seed', 'community_seed', 'format', 'EXPERIENCE')),
    (991100000000000004, 15, JSON_OBJECT('domain', 2, 'seed', 'community_seed', 'format', 'EXPERIENCE')),
    (991100000000000005, 13, JSON_OBJECT('domain', 2, 'seed', 'community_seed', 'format', 'QUESTION')),
    (991100000000000006, 11, JSON_OBJECT('domain', 2, 'seed', 'community_seed', 'format', 'REVIEW')),
    (991100000000000007, 14, JSON_OBJECT('domain', 5, 'seed', 'community_seed', 'format', 'RESOURCE')),
    (991100000000000008, 13, JSON_OBJECT('domain', 5, 'seed', 'community_seed', 'format', 'QUESTION')),
    (991100000000000009, 16, JSON_OBJECT('domain', 5, 'seed', 'community_seed', 'format', 'DISCUSSION')),
    (991100000000000010, 15, JSON_OBJECT('domain', 3, 'seed', 'community_seed', 'format', 'EXPERIENCE')),
    (991100000000000011, 14, JSON_OBJECT('domain', 3, 'seed', 'community_seed', 'format', 'RESOURCE')),
    (991100000000000012, 11, JSON_OBJECT('domain', 3, 'seed', 'community_seed', 'format', 'REVIEW')),
    (991100000000000013, 13, JSON_OBJECT('domain', 4, 'seed', 'community_seed', 'format', 'QUESTION')),
    (991100000000000014, 16, JSON_OBJECT('domain', 4, 'seed', 'community_seed', 'format', 'DISCUSSION')),
    (991100000000000015, 15, JSON_OBJECT('domain', 4, 'seed', 'community_seed', 'format', 'EXPERIENCE'))
ON DUPLICATE KEY UPDATE
    post_type = VALUES(post_type),
    ext_json = VALUES(ext_json),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_post_counter
    (post_id, view_count, like_count, comment_count, favorite_count, share_count)
VALUES
    (991100000000000001, 386, 42, 8, 61, 11),
    (991100000000000002, 244, 28, 23, 17, 5),
    (991100000000000003, 318, 37, 12, 44, 8),
    (991100000000000004, 352, 49, 19, 38, 7),
    (991100000000000005, 291, 31, 27, 22, 4),
    (991100000000000006, 278, 35, 16, 29, 5),
    (991100000000000007, 421, 53, 14, 72, 13),
    (991100000000000008, 397, 46, 32, 58, 9),
    (991100000000000009, 265, 29, 38, 18, 6),
    (991100000000000010, 368, 51, 21, 63, 10),
    (991100000000000011, 332, 44, 11, 69, 12),
    (991100000000000012, 247, 32, 18, 35, 5),
    (991100000000000013, 309, 36, 41, 24, 7),
    (991100000000000014, 283, 27, 46, 19, 8),
    (991100000000000015, 341, 48, 25, 39, 9)
ON DUPLICATE KEY UPDATE
    view_count = GREATEST(view_count, VALUES(view_count)),
    like_count = GREATEST(like_count, VALUES(like_count)),
    comment_count = GREATEST(comment_count, VALUES(comment_count)),
    favorite_count = GREATEST(favorite_count, VALUES(favorite_count)),
    share_count = GREATEST(share_count, VALUES(share_count)),
    update_time = CURRENT_TIMESTAMP(3);

SET @community_seed_identity_conflicts := (
    SELECT COUNT(*)
    FROM (
        SELECT 991110000000000001 AS id, 991100000000000001 AS post_id, 991000000000000001 AS tag_id
        UNION ALL SELECT 991110000000000002, 991100000000000001, 991000000000000002
        UNION ALL SELECT 991110000000000003, 991100000000000002, 991000000000000001
        UNION ALL SELECT 991110000000000004, 991100000000000002, 991000000000000002
        UNION ALL SELECT 991110000000000005, 991100000000000003, 991000000000000001
        UNION ALL SELECT 991110000000000006, 991100000000000003, 991000000000000002
        UNION ALL SELECT 991110000000000007, 991100000000000004, 991000000000000003
        UNION ALL SELECT 991110000000000008, 991100000000000004, 991000000000000004
        UNION ALL SELECT 991110000000000009, 991100000000000005, 991000000000000003
        UNION ALL SELECT 991110000000000010, 991100000000000005, 991000000000000004
        UNION ALL SELECT 991110000000000011, 991100000000000006, 991000000000000003
        UNION ALL SELECT 991110000000000012, 991100000000000006, 991000000000000004
        UNION ALL SELECT 991110000000000013, 991100000000000007, 991000000000000005
        UNION ALL SELECT 991110000000000014, 991100000000000007, 991000000000000006
        UNION ALL SELECT 991110000000000015, 991100000000000008, 991000000000000005
        UNION ALL SELECT 991110000000000016, 991100000000000008, 991000000000000006
        UNION ALL SELECT 991110000000000017, 991100000000000009, 991000000000000005
        UNION ALL SELECT 991110000000000018, 991100000000000009, 991000000000000006
        UNION ALL SELECT 991110000000000019, 991100000000000010, 991000000000000007
        UNION ALL SELECT 991110000000000020, 991100000000000010, 991000000000000008
        UNION ALL SELECT 991110000000000021, 991100000000000011, 991000000000000007
        UNION ALL SELECT 991110000000000022, 991100000000000011, 991000000000000008
        UNION ALL SELECT 991110000000000023, 991100000000000012, 991000000000000007
        UNION ALL SELECT 991110000000000024, 991100000000000012, 991000000000000008
        UNION ALL SELECT 991110000000000025, 991100000000000013, 991000000000000009
        UNION ALL SELECT 991110000000000026, 991100000000000013, 991000000000000010
        UNION ALL SELECT 991110000000000027, 991100000000000014, 991000000000000009
        UNION ALL SELECT 991110000000000028, 991100000000000014, 991000000000000010
        UNION ALL SELECT 991110000000000029, 991100000000000015, 991000000000000009
        UNION ALL SELECT 991110000000000030, 991100000000000015, 991000000000000010
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_post_tag_ref existing
        WHERE existing.id = expected.id
          AND (existing.post_id <> expected.post_id OR existing.tag_id <> expected.tag_id)
    )
       OR EXISTS (
        SELECT 1
        FROM t_post_tag_ref existing
        WHERE existing.post_id = expected.post_id
          AND existing.tag_id = expected.tag_id
          AND existing.id <> expected.id
    )
);
SET @community_seed_guard_sql := IF(
    @community_seed_identity_conflicts > 0,
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''community seed post tag identity conflict''',
    'DO 0'
);
PREPARE community_seed_guard_stmt FROM @community_seed_guard_sql;
EXECUTE community_seed_guard_stmt;
DEALLOCATE PREPARE community_seed_guard_stmt;

INSERT IGNORE INTO t_post_tag_ref (id, post_id, tag_id) VALUES
    (991110000000000001, 991100000000000001, 991000000000000001),
    (991110000000000002, 991100000000000001, 991000000000000002),
    (991110000000000003, 991100000000000002, 991000000000000001),
    (991110000000000004, 991100000000000002, 991000000000000002),
    (991110000000000005, 991100000000000003, 991000000000000001),
    (991110000000000006, 991100000000000003, 991000000000000002),
    (991110000000000007, 991100000000000004, 991000000000000003),
    (991110000000000008, 991100000000000004, 991000000000000004),
    (991110000000000009, 991100000000000005, 991000000000000003),
    (991110000000000010, 991100000000000005, 991000000000000004),
    (991110000000000011, 991100000000000006, 991000000000000003),
    (991110000000000012, 991100000000000006, 991000000000000004),
    (991110000000000013, 991100000000000007, 991000000000000005),
    (991110000000000014, 991100000000000007, 991000000000000006),
    (991110000000000015, 991100000000000008, 991000000000000005),
    (991110000000000016, 991100000000000008, 991000000000000006),
    (991110000000000017, 991100000000000009, 991000000000000005),
    (991110000000000018, 991100000000000009, 991000000000000006),
    (991110000000000019, 991100000000000010, 991000000000000007),
    (991110000000000020, 991100000000000010, 991000000000000008),
    (991110000000000021, 991100000000000011, 991000000000000007),
    (991110000000000022, 991100000000000011, 991000000000000008),
    (991110000000000023, 991100000000000012, 991000000000000007),
    (991110000000000024, 991100000000000012, 991000000000000008),
    (991110000000000025, 991100000000000013, 991000000000000009),
    (991110000000000026, 991100000000000013, 991000000000000010),
    (991110000000000027, 991100000000000014, 991000000000000009),
    (991110000000000028, 991100000000000014, 991000000000000010),
    (991110000000000029, 991100000000000015, 991000000000000009),
    (991110000000000030, 991100000000000015, 991000000000000010);

INSERT INTO t_user_counter (user_id, post_count)
VALUES (
    @community_demo_uid,
    (SELECT COUNT(*)
     FROM t_post_main
     WHERE author_id = @community_demo_uid
       AND post_status = 1
       AND is_deleted = 0)
)
ON DUPLICATE KEY UPDATE
    post_count = VALUES(post_count),
    update_time = CURRENT_TIMESTAMP(3);
