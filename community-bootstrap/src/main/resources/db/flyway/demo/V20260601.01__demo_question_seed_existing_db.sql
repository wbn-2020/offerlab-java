-- 20260601_demo_question_seed_existing_db.sql
-- Non-destructive demo data patch for existing local databases.
-- This file lives under db/migration instead of db/init because init scripts
-- only run for fresh Docker volumes.
-- Run after the schema migrations through 20260601. This script only inserts
-- or refreshes deterministic demo rows; it does not drop, truncate, or delete.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_company_alias (
    id                BIGINT       NOT NULL PRIMARY KEY,
    canonical_company VARCHAR(128) NOT NULL,
    alias             VARCHAR(128) NOT NULL,
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_alias (alias),
    KEY idx_canonical (canonical_company, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company alias';

SET @missing_demo_seed_schema_columns := (
    SELECT GROUP_CONCAT(required_column ORDER BY required_column SEPARATOR ', ')
    FROM (
        SELECT 't_interview_question.exam_point' AS required_column
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_interview_question' AND COLUMN_NAME = 'exam_point'
        )
        UNION ALL
        SELECT 't_interview_question.reference_answer'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_interview_question' AND COLUMN_NAME = 'reference_answer'
        )
        UNION ALL
        SELECT 't_interview_question.source_snippet'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_interview_question' AND COLUMN_NAME = 'source_snippet'
        )
        UNION ALL
        SELECT 't_interview_question.quality_reason'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_interview_question' AND COLUMN_NAME = 'quality_reason'
        )
        UNION ALL
        SELECT 't_user_prep_target.interview_date'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_prep_target' AND COLUMN_NAME = 'interview_date'
        )
        UNION ALL
        SELECT 't_user_prep_target.priority'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_prep_target' AND COLUMN_NAME = 'priority'
        )
        UNION ALL
        SELECT 't_user_prep_target.note'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_prep_target' AND COLUMN_NAME = 'note'
        )
        UNION ALL
        SELECT 't_user_question_progress.mistake_reason'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_question_progress' AND COLUMN_NAME = 'mistake_reason'
        )
        UNION ALL
        SELECT 't_user_question_progress.answer_draft'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_question_progress' AND COLUMN_NAME = 'answer_draft'
        )
        UNION ALL
        SELECT 't_user_question_progress.star_story'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_question_progress' AND COLUMN_NAME = 'star_story'
        )
        UNION ALL
        SELECT 't_user_question_progress.next_review_at'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_question_progress' AND COLUMN_NAME = 'next_review_at'
        )
        UNION ALL
        SELECT 't_mock_interview_answer.question_text_snapshot'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_mock_interview_answer' AND COLUMN_NAME = 'question_text_snapshot'
        )
    ) required_columns
);

SELECT COALESCE(@missing_demo_seed_schema_columns, 'none') AS missing_demo_seed_schema_columns;
SET @demo_seed_preflight_sql := IF(
    @missing_demo_seed_schema_columns IS NULL,
    'SELECT ''schema preflight passed'' AS demo_seed_schema_preflight',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''Missing demo seed schema columns; run migrations through 20260601 first.'''
);
PREPARE demo_seed_preflight_stmt FROM @demo_seed_preflight_sql;
EXECUTE demo_seed_preflight_stmt;
DEALLOCATE PREPARE demo_seed_preflight_stmt;

SET @demo_uid := COALESCE(
    (SELECT id FROM t_user_account WHERE email = 'demo.author@offerlab.local' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE email = 'demo.admin@offerlab.local' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE email = 'admin' AND is_deleted = 0 LIMIT 1),
    990000000000000001
);

INSERT INTO t_user_account
    (id, email, password_hash, password_salt, account_status)
VALUES
    (@demo_uid, 'demo.author@offerlab.local', '$2a$10$0CN4aiMIujTsf.AmOBUj8OAO.IrhNxcI5Toug4dnCegzpvuYHYmcG', '', 2)
ON DUPLICATE KEY UPDATE
    id = id;

INSERT INTO t_user_profile
    (id, nickname, avatar_url, bio, intent_json)
VALUES
    (
        @demo_uid,
        'OfferLab 婕旂ず绠＄悊鍛?',
        'https://api.dicebear.com/7.x/initials/svg?seed=OfferLab',
        '正在准备深测科技 Java 后端面试，已整理题单、笔记和 STAR 草稿。',
        JSON_OBJECT(
            'targetCompanies', JSON_ARRAY('娣辨祴绉戞妧'),
            'targetPositions', JSON_ARRAY('Java 鍚庣', '鍚庣宸ョ▼甯?'),
            'targetCity', '上海'
        )
    )
ON DUPLICATE KEY UPDATE
    id = id;

INSERT INTO t_user_counter
    (user_id, follower_count, following_count, post_count, like_received)
VALUES
    (@demo_uid, 12, 5, 3, 86)
ON DUPLICATE KEY UPDATE
    user_id = user_id;

INSERT INTO t_tag (id, tag_name, tag_type, is_official) VALUES
    (1001, 'Java', 1, 1),
    (1004, 'Spring', 1, 1),
    (1005, 'MySQL', 1, 1),
    (1006, 'Redis', 1, 1),
    (1007, 'Kafka', 1, 1),
    (1008, 'Elasticsearch', 1, 1),
    (1010, 'JVM', 1, 1),
    (2007, '娣辨祴绉戞妧', 2, 1),
    (3001, 'Java 鍚庣', 3, 1),
    (3005, '鍚庣宸ョ▼甯?', 3, 1)
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    tag_type = VALUES(tag_type),
    is_official = VALUES(is_official);

INSERT INTO t_company_alias
    (id, canonical_company, alias, status)
VALUES
    (990010000000000001, '娣辨祴绉戞妧', '娣辨祴绉戞妧', 1),
    (990010000000000002, '娣辨祴绉戞妧', '娣辨祴', 1),
    (990010000000000003, '深测科技', '深测科技有限公司', 1)
ON DUPLICATE KEY UPDATE
    canonical_company = VALUES(canonical_company),
    status = VALUES(status),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_post_main
    (id, author_id, post_type, title, content, cover_url, visibility, post_status, create_time, update_time, is_deleted)
VALUES
    (
        990100000000000001,
        @demo_uid,
        1,
        '深测科技 Java 后端一面复盘：缓存、事务和。SQL',
        '一面重点围。Spring 事务传播、Redis 缓存一致性、MySQL 。SQL 排查和项目中的降级设计。面试官会追问为什么这么设计，以及线上指标如何验证。',
        NULL,
        1,
        1,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY,
        0
    ),
    (
        990100000000000002,
        @demo_uid,
        1,
        '深测科技 二面：Kafka 削峰和分布式排查',
        '二面更关注系统设计和稳定性，聊到 Kafka 消费幂等、消息堆积定位、接口超时治理，以及如何把一次线上故障讲成结构化 STAR 案例。',
        NULL,
        1,
        1,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 12 HOUR,
        0
    ),
    (
        990100000000000003,
        @demo_uid,
        1,
        '深测科技 HR 前技术加面：JVM、索引和项目亮点',
        '这一轮题目覆。JVM 内存模型、索引选择性、接口压测结果复盘，以及如何把项目亮点和岗位要求连接起来。',
        NULL,
        1,
        1,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3),
        0
    )
ON DUPLICATE KEY UPDATE
    author_id = VALUES(author_id),
    post_type = VALUES(post_type),
    title = VALUES(title),
    content = VALUES(content),
    cover_url = VALUES(cover_url),
    visibility = VALUES(visibility),
    post_status = VALUES(post_status),
    is_deleted = VALUES(is_deleted),
    create_time = VALUES(create_time),
    update_time = VALUES(update_time);

INSERT INTO t_post_extension
    (post_id, post_type, ext_json)
VALUES
    (990100000000000001, 1, JSON_OBJECT('company', '娣辨祴绉戞妧', 'position', 'Java 鍚庣', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000002, 1, JSON_OBJECT('company', '娣辨祴绉戞妧', 'position', '鍚庣宸ョ▼甯?', 'yearsOfExp', 4, 'interviewResult', 2)),
    (990100000000000003, 1, JSON_OBJECT('company', '娣辨祴绉戞妧', 'position', 'Java 鍚庣', 'yearsOfExp', 3, 'interviewResult', 1))
ON DUPLICATE KEY UPDATE
    post_type = VALUES(post_type),
    ext_json = VALUES(ext_json),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_post_counter
    (post_id, view_count, like_count, comment_count, favorite_count, share_count)
VALUES
    (990100000000000001, 238, 31, 9, 18, 4),
    (990100000000000002, 192, 24, 7, 15, 3),
    (990100000000000003, 156, 19, 5, 11, 2)
ON DUPLICATE KEY UPDATE
    view_count = VALUES(view_count),
    like_count = VALUES(like_count),
    comment_count = VALUES(comment_count),
    favorite_count = VALUES(favorite_count),
    share_count = VALUES(share_count),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_post_tag_ref
    (id, post_id, tag_id)
VALUES
    (990110000000000001, 990100000000000001, 1001),
    (990110000000000002, 990100000000000001, 1004),
    (990110000000000003, 990100000000000001, 1005),
    (990110000000000004, 990100000000000001, 1006),
    (990110000000000005, 990100000000000001, 2007),
    (990110000000000006, 990100000000000001, 3001),
    (990110000000000007, 990100000000000002, 1007),
    (990110000000000008, 990100000000000002, 1006),
    (990110000000000009, 990100000000000002, 2007),
    (990110000000000010, 990100000000000002, 3005),
    (990110000000000011, 990100000000000003, 1010),
    (990110000000000012, 990100000000000003, 1005),
    (990110000000000013, 990100000000000003, 2007),
    (990110000000000014, 990100000000000003, 3001)
ON DUPLICATE KEY UPDATE
    post_id = VALUES(post_id),
    tag_id = VALUES(tag_id);

INSERT INTO t_interview_question
    (id, canonical_id, question_text, normalized_hash, answer_hint, exam_point, reference_answer, source_snippet,
     quality_reason, company, position, interview_round, difficulty, confidence, source_post_id, source_author_uid,
     status, appear_count, quality_score, create_time, update_time)
VALUES
    (
        990200000000000001,
        NULL,
        'Spring 事务在同类方法内部调用时为什么可能不生效？你在项目里怎么规避。',
        SHA2('offerlab-demo-question-990200000000000001', 256),
        '。AOP 代理、传播行为、异常回滚规则、事务边界设计四点回答。',
        'Spring 事务代理与工程落。',
        '同类内部调用绕过代理时，@Transactional 不会被拦截。常见处理是拆分到独。Bean、通过代理对象调用，或把事务边界上移到应用服务层，同时明确 rollbackFor 和传播行为。',
        '面试官追问了为什。private 方法。self-invocation 事务不生效。',
        '覆盖高频 Spring 事务陷阱，并要求结合项目经验说明规避方案。',
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '一。',
        'medium',
        0.9400,
        990100000000000001,
        @demo_uid,
        1,
        7,
        92,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990200000000000002,
        NULL,
        'Redis 缓存和数据库双写不一致时，你会如何设计更新策略？',
        SHA2('offerlab-demo-question-990200000000000002', 256),
        '先说旁路缓存，再比较先删缓存、延迟双删、消息补偿和读写锁。',
        '缓存一致性与降级',
        '常用方案是更新数据库后删除缓存，并通过重试队列。outbox 补偿删除失败；对热点 key 可加互斥重建、逻辑过期或短 TTL，避免把双写顺序说成绝对正确。',
        '候选人需要解释缓存击穿和删除失败时的兜底机制。',
        '同时考察 Redis 基础、异常补偿和线上稳定性意识。',
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '一。',
        'medium',
        0.9300,
        990100000000000001,
        @demo_uid,
        1,
        9,
        95,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990200000000000003,
        NULL,
        'MySQL 慢查询从发现到修复，你的排查步骤是什么？',
        SHA2('offerlab-demo-question-990200000000000003', 256),
        '慢日志、EXPLAIN、索引选择性、回表、排序临时表、分页优化。',
        'MySQL 索引与性能诊断',
        '先用监控和慢日志定位 SQL，再。EXPLAIN 。type、key、rows、Extra；结合业务基数判断索引顺序，必要时改写查询、避免大 offset、拆分宽字段或补充覆盖索引。',
        '面试官给了一。user_id + status + create_time 的组合查询。',
        '有明确诊断路径和可复盘指标，适合准备公司页展示。',
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '一。',
        'easy',
        0.9100,
        990100000000000001,
        @demo_uid,
        1,
        6,
        88,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990200000000000004,
        NULL,
        'Kafka 消费端如何保证幂等？如果出现消息堆积你会先看哪些指标。',
        SHA2('offerlab-demo-question-990200000000000004', 256),
        '幂等键、唯一约束、消费位点、重试死信、lag、消费耗时、分区数。',
        '娑堟伅闃熷垪绋冲畾鎬?',
        '幂等通常依赖业务唯一键、去重表、状态机或数据库唯一约束；堆积时先看 consumer lag、单条处理耗时、失败重试比例、分区倾斜、下游依赖耗时，再决定扩容或降级。',
        '二面把消息堆积和线上故障恢复连起来问。',
        '能支撑模拟面试和 STAR 故障复盘。',
        '娣辨祴绉戞妧',
        '鍚庣宸ョ▼甯?',
        '浜岄潰',
        'hard',
        0.9200,
        990100000000000002,
        @demo_uid,
        1,
        8,
        94,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 12 HOUR
    ),
    (
        990200000000000005,
        NULL,
        '接口超时率突然升高，你会如何定位是应用、数据库还是外部依赖问题。',
        SHA2('offerlab-demo-question-990200000000000005', 256),
        '按入口指标、链路追踪、线程池、连接池、SQL、下游依赖逐层缩小范围。',
        '线上问题定位',
        '先确认错误率、P95/P99、实例分布和变更窗口，再。trace 拆分耗时；同时检查线程池队列、连接池等待、慢 SQL、GC 和下游超时，最后用限流降级恢复服务。',
        '要求讲清楚先恢复再定位的优先级。',
        '贴近后端岗位真实场景，适合 /me/prep 复习计划。',
        '娣辨祴绉戞妧',
        '鍚庣宸ョ▼甯?',
        '浜岄潰',
        'hard',
        0.9000,
        990100000000000002,
        @demo_uid,
        1,
        5,
        90,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 12 HOUR
    ),
    (
        990200000000000006,
        NULL,
        'JVM 老年代持续上涨但 Full GC 后下降不明显，你会怎么分析。',
        SHA2('offerlab-demo-question-990200000000000006', 256),
        '区分内存泄漏、缓存膨胀、大对象、类加载，结。dump 。GC 日志。',
        'JVM 鍐呭瓨鎺掓煡',
        '先看 GC 日志和监控确认趋势，再抓 heap dump 对比对象引用链；常见原因包括本地缓存无上限、静态集合持有、线程池任务堆积、大对象或类加载泄漏。',
        '技术加面要求说。MAT dominator tree 怎么看。',
        '高频 JVM 排障题，能丰富公司准备包的难题列表。',
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '技术加。',
        'hard',
        0.8900,
        990100000000000003,
        @demo_uid,
        1,
        4,
        87,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    ),
    (
        990200000000000007,
        NULL,
        '如何设计一个支持高并发查询的面试题搜索接口。',
        SHA2('offerlab-demo-question-990200000000000007', 256),
        '关键词召回、筛选条件、分页游标、缓存、ES 降级和索引重建。',
        '搜索与接口设。',
        '搜索接口应区分召回和详情读取，使。ES 或倒排索引承接关键词，数据库兜底需要限制分页深度；热门条件可缓存，索引更新要有重试和可观测任务状态。',
        '候选人被要求补充索引失败后的补偿方案。',
        '。OfferLab 自身题库场景贴合，方便演示搜索和高亮。',
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '技术加。',
        'medium',
        0.8800,
        990100000000000003,
        @demo_uid,
        1,
        3,
        84,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    ),
    (
        990200000000000008,
        NULL,
        '项目经历里你如何。STAR 讲清一次稳定性优化？',
        SHA2('offerlab-demo-question-990200000000000008', 256),
        'Situation、Task、Action、Result，每一步都要有指标和个人贡献。',
        '项目表达。STAR',
        '先描述业务场景和故障影响，再说明自己的任务边界；行动部分讲监控、限流、缓存、SQL 优化等关键取舍，结果要落到延迟、错误率、成本或人效指标。',
        'HR 前技术加面会把技术方案和表达能力一起考察。',
        '补齐项目表达维度，避免题库只有技术知识点。',
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '技术加。',
        'easy',
        0.8700,
        990100000000000003,
        @demo_uid,
        1,
        3,
        82,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    )
ON DUPLICATE KEY UPDATE
    canonical_id = VALUES(canonical_id),
    question_text = VALUES(question_text),
    normalized_hash = VALUES(normalized_hash),
    answer_hint = VALUES(answer_hint),
    exam_point = VALUES(exam_point),
    reference_answer = VALUES(reference_answer),
    source_snippet = VALUES(source_snippet),
    quality_reason = VALUES(quality_reason),
    company = VALUES(company),
    position = VALUES(position),
    interview_round = VALUES(interview_round),
    difficulty = VALUES(difficulty),
    confidence = VALUES(confidence),
    source_post_id = VALUES(source_post_id),
    source_author_uid = VALUES(source_author_uid),
    status = VALUES(status),
    appear_count = VALUES(appear_count),
    quality_score = VALUES(quality_score),
    create_time = VALUES(create_time),
    update_time = VALUES(update_time);

INSERT INTO t_interview_question_tag
    (id, question_id, tag_id)
VALUES
    (990210000000000001, 990200000000000001, 1001),
    (990210000000000002, 990200000000000001, 1004),
    (990210000000000003, 990200000000000002, 1006),
    (990210000000000004, 990200000000000002, 1001),
    (990210000000000005, 990200000000000003, 1005),
    (990210000000000006, 990200000000000003, 1001),
    (990210000000000007, 990200000000000004, 1007),
    (990210000000000008, 990200000000000004, 1001),
    (990210000000000009, 990200000000000005, 1004),
    (990210000000000010, 990200000000000005, 1006),
    (990210000000000011, 990200000000000006, 1010),
    (990210000000000012, 990200000000000006, 1001),
    (990210000000000013, 990200000000000007, 1008),
    (990210000000000014, 990200000000000007, 1001),
    (990210000000000015, 990200000000000008, 3001),
    (990210000000000016, 990200000000000008, 2007)
ON DUPLICATE KEY UPDATE
    question_id = VALUES(question_id),
    tag_id = VALUES(tag_id);

INSERT INTO t_user_prep_target
    (id, uid, target_type, target_value, interview_date, priority, note)
VALUES
    (990300000000000001, @demo_uid, 'company', '深测科技', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'urgent', '优先刷深测科技高频后端题，准备一面到加面的完整链路。'),
    (990300000000000002, @demo_uid, 'position', 'Java 后端', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'high', '围绕 Spring、MySQL、Redis、Kafka、JVM 做专项复盘。'),
    (990300000000000003, @demo_uid, 'tag', 'Kafka', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'medium', '补齐消息堆积、幂等和重试死信案例。')
ON DUPLICATE KEY UPDATE
    uid = VALUES(uid),
    interview_date = VALUES(interview_date),
    priority = VALUES(priority),
    note = VALUES(note);

INSERT INTO t_user_question_progress
    (id, uid, question_id, progress_status, favorite, note, mistake_reason, answer_draft, star_story,
     next_review_at, last_reviewed_at, review_count, review_interval_days, create_time, update_time)
VALUES
    (990310000000000001, @demo_uid, 990200000000000001, 'mastered', 1, '同类方法调用不走代理，要主动说出事务边界为什么放。service 编排层。', NULL, '我会先解。Spring AOP 代理机制，再结合订单结算项目说明如何把事务入口放到应用服务层，并补充 rollbackFor 和传播行为。', 'S: 订单结算存在部分写入风险；T: 梳理事务边界；A: 拆分库存、订单、流水写入并统一由应用服务编排；R: 回滚路径清晰，压测无脏数据。', DATE_ADD(NOW(3), INTERVAL 10 DAY), NOW(3) - INTERVAL 1 DAY, 3, 10, NOW(3) - INTERVAL 8 DAY, NOW(3) - INTERVAL 1 DAY),
    (990310000000000002, @demo_uid, 990200000000000002, 'review', 1, '缓存一致性不能只背延迟双删，要讲删除失败补偿和热点重建。', 'concept', '旁路缓存下先更新 DB 再删缓存；删除失败进入重试队列，热点 key 用逻辑过期和互斥锁重建。', 'S: 活动页库存缓存偶发脏读；T: 降低不一致窗口；A: DB 成功后删缓存，失败写入重试任务，热点 key 加短 TTL；R: 投诉下降，缓存命中保持稳定。', NOW(3) - INTERVAL 2 HOUR, NOW(3) - INTERVAL 2 DAY, 2, 3, NOW(3) - INTERVAL 7 DAY, NOW(3) - INTERVAL 2 HOUR),
    (990310000000000003, @demo_uid, 990200000000000003, 'learning', 0, 'EXPLAIN 字段要讲 type/key/rows/Extra，再落到索引顺序。', 'expression', '。SQL 排查会先用慢日志定位，再。EXPLAIN 和数据分布，最后用组合索引、覆盖索引或分页改写验证效果。', NULL, DATE_ADD(NOW(3), INTERVAL 1 DAY), NOW(3) - INTERVAL 3 DAY, 1, 2, NOW(3) - INTERVAL 6 DAY, NOW(3) - INTERVAL 1 DAY),
    (990310000000000004, @demo_uid, 990200000000000004, 'review', 1, 'Kafka 堆积要按 lag、消费耗时、失败重试、分区倾斜和下游耗时拆。', 'project', '幂等用业务唯一键和数据库唯一约束；堆积先确认 lag 和处理耗时，再判断扩容、限流还是下游降级。', 'S: 促销消息堆积导致履约延迟；T: 恢复消费并避免重复扣减；A: 增加幂等表、调整批量提交、隔离慢下游；R: lag 。20 分钟内恢复到安全水位。', NOW(3) - INTERVAL 1 HOUR, NOW(3) - INTERVAL 2 DAY, 2, 3, NOW(3) - INTERVAL 5 DAY, NOW(3) - INTERVAL 1 HOUR),
    (990310000000000005, @demo_uid, 990200000000000005, 'todo', 0, '需要补一段链路追踪定位外部依赖超时的真实案例。', 'memory', NULL, NULL, NULL, NULL, 0, 1, NOW(3) - INTERVAL 5 DAY, NOW(3) - INTERVAL 5 DAY),
    (990310000000000006, @demo_uid, 990200000000000006, 'learning', 1, 'JVM 题要。dump、GC 日志和对象引用链连起来。', 'concept', '我会先看 Full GC 后老年代回收比例，再抓 heap dump，用 MAT 。dominator tree 。GC Roots，确认是否是缓存或静态集合持有。', NULL, DATE_ADD(NOW(3), INTERVAL 2 DAY), NOW(3) - INTERVAL 1 DAY, 1, 2, NOW(3) - INTERVAL 2 DAY, NOW(3) - INTERVAL 1 DAY),
    (990310000000000007, @demo_uid, 990200000000000007, 'todo', 0, '准备。OfferLab 题库搜索作为项目案例来讲。', NULL, '搜索接口会用 ES 做关键词召回，DB 查询详情；索引失败进入重试任务，前端展示任务状态和降级结果。', 'S: 题库增长后搜索延迟升高；T: 保持搜索体验；A: 引入索引重建和失败重试；R: 热门关键词响应稳定在百毫秒级。', NULL, NULL, 0, 1, NOW(3) - INTERVAL 2 DAY, NOW(3)),
    (990310000000000008, @demo_uid, 990200000000000008, 'review', 1, 'STAR 表达要量化结果，不要只说做了优化。', 'expression', '我会用场景、任务、行动、结果四段回答，并把结果落到错误率、P99 延迟和排查效率。', 'S: 核心接口在活动期间超时；T: 我负责定位并降低超时；A: 。tracing、优化慢 SQL、热点缓存预热；R: P99 。1.8s 降到 420ms，错误率低于 0.1%。', NOW(3) - INTERVAL 30 MINUTE, NOW(3) - INTERVAL 1 DAY, 1, 2, NOW(3) - INTERVAL 2 DAY, NOW(3) - INTERVAL 30 MINUTE)
ON DUPLICATE KEY UPDATE
    uid = VALUES(uid),
    progress_status = VALUES(progress_status),
    favorite = VALUES(favorite),
    note = VALUES(note),
    mistake_reason = VALUES(mistake_reason),
    answer_draft = VALUES(answer_draft),
    star_story = VALUES(star_story),
    next_review_at = VALUES(next_review_at),
    last_reviewed_at = VALUES(last_reviewed_at),
    review_count = VALUES(review_count),
    review_interval_days = VALUES(review_interval_days),
    update_time = VALUES(update_time);

INSERT INTO t_mock_interview_session
    (id, uid, company, position, difficulty, focus_tag, question_count, answered_count, total_score,
     duration_seconds, status, create_time, update_time)
VALUES
    (990400000000000001, @demo_uid, '娣辨祴绉戞妧', 'Java 鍚庣', 'medium', 'Kafka', 3, 3, 12, 1280, 'completed', NOW(3) - INTERVAL 1 DAY, NOW(3) - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE
    uid = VALUES(uid),
    company = VALUES(company),
    position = VALUES(position),
    difficulty = VALUES(difficulty),
    focus_tag = VALUES(focus_tag),
    question_count = VALUES(question_count),
    answered_count = VALUES(answered_count),
    total_score = VALUES(total_score),
    duration_seconds = VALUES(duration_seconds),
    status = VALUES(status),
    update_time = VALUES(update_time);

INSERT INTO t_mock_interview_answer
    (id, session_id, uid, question_id, sequence_no, question_text_snapshot, answer_hint_snapshot,
     company_snapshot, position_snapshot, round_snapshot, difficulty_snapshot, answer_text, self_review, score)
VALUES
    (990410000000000001, 990400000000000001, @demo_uid, 990200000000000002, 1, 'Redis 缓存和数据库双写不一致时，你会如何设计更新策略？', '旁路缓存、删除失败补偿和热点重建。', '深测科技', 'Java 后端', '一。', 'medium', '先更新数据库再删除缓存，删除失败写入重试任务；热。key 使用逻辑过期和互斥重建。', '还需要补充为什么不直接先写缓存。', 4),
    (990410000000000002, 990400000000000001, @demo_uid, 990200000000000004, 2, 'Kafka 消费端如何保证幂等？如果出现消息堆积你会先看哪些指标。', '幂等键、唯一约束、lag、分区倾斜。', '深测科技', '后端工程。', '二面', 'hard', '用业务唯一键和去重表保证幂等；堆积先看 lag、消费耗时、失败重试比例、分区倾斜和下游耗时。', '回答完整，但 STAR 案例可以更紧。', 4),
    (990410000000000003, 990400000000000001, @demo_uid, 990200000000000008, 3, '项目经历里你如何。STAR 讲清一次稳定性优化？', 'STAR 四段和量化结果。', '深测科技', 'Java 后端', '技术加。', 'easy', '我会用场景、任务、行动、结果四段讲，把 P99、错误率和恢复时间作为结果指标。', '表达更顺了，下一版要加个人贡献边界。', 4)
ON DUPLICATE KEY UPDATE
    uid = VALUES(uid),
    sequence_no = VALUES(sequence_no),
    question_text_snapshot = VALUES(question_text_snapshot),
    answer_hint_snapshot = VALUES(answer_hint_snapshot),
    company_snapshot = VALUES(company_snapshot),
    position_snapshot = VALUES(position_snapshot),
    round_snapshot = VALUES(round_snapshot),
    difficulty_snapshot = VALUES(difficulty_snapshot),
    answer_text = VALUES(answer_text),
    self_review = VALUES(self_review),
    score = VALUES(score);
