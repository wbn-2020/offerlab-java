-- 20260603_demo_content_refresh.sql
-- Non-destructive demo content refresh for existing local databases.
-- It reuses an existing local/demo author and keeps E2E/SMOKE/CODEX records
-- out of default product surfaces through application filters. This common
-- migration must not create login-capable demo accounts.
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

SET @demo_uid := COALESCE(
    (SELECT id FROM t_user_account WHERE email = 'admin' AND is_deleted = 0 LIMIT 1),
    (SELECT id FROM t_user_account WHERE email = 'demo.admin@offerlab.local' AND is_deleted = 0 LIMIT 1),
    990000000000000001
);

INSERT INTO t_tag (id, tag_name, tag_type, use_count, is_official) VALUES
    (1001, 'Java', 1, 5, 1),
    (1004, 'Spring', 1, 3, 1),
    (1005, 'MySQL', 1, 3, 1),
    (1006, 'Redis', 1, 4, 1),
    (1007, 'Kafka', 1, 3, 1),
    (1008, 'Elasticsearch', 1, 0, 1),
    (1010, 'JVM', 1, 2, 1),
    (2001, '瀛楄妭璺冲姩', 2, 1, 1),
    (2002, '闃块噷宸村反', 2, 1, 1),
    (2004, '缇庡洟', 2, 1, 1),
    (2007, '娣辨祴绉戞妧', 2, 3, 1),
    (3001, 'Java 鍚庣', 3, 4, 1),
    (3005, '鍚庣宸ョ▼甯?', 3, 2, 1)
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    tag_type = VALUES(tag_type),
    use_count = GREATEST(use_count, VALUES(use_count)),
    is_official = VALUES(is_official);

INSERT INTO t_user_profile
    (id, nickname, avatar_url, bio, intent_json)
VALUES
    (
        @demo_uid,
        'OfferLab 婕旂ず绠＄悊鍛?',
        'https://api.dicebear.com/7.x/initials/svg?seed=OfferLab',
        '正在准备 Java 后端面试，重点复盘高并发、缓存一致性、消息链路和项目表达。',
        JSON_OBJECT(
            'targetCompanies', JSON_ARRAY('瀛楄妭璺冲姩', '闃块噷宸村反', '缇庡洟'),
            'targetPositions', JSON_ARRAY('Java 鍚庣', '鍚庣宸ョ▼甯?'),
            'targetCity', '上海'
        )
    )
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    avatar_url = VALUES(avatar_url),
    bio = VALUES(bio),
    intent_json = VALUES(intent_json),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_user_counter
    (user_id, follower_count, following_count, post_count, like_received)
VALUES
    (@demo_uid, 18, 7, 6, 196)
ON DUPLICATE KEY UPDATE
    follower_count = GREATEST(follower_count, VALUES(follower_count)),
    following_count = GREATEST(following_count, VALUES(following_count)),
    post_count = GREATEST(post_count, VALUES(post_count)),
    like_received = GREATEST(like_received, VALUES(like_received)),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_company_alias
    (id, canonical_company, alias, status)
VALUES
    (990010000000000001, '娣辨祴绉戞妧', '娣辨祴绉戞妧', 1),
    (990010000000000002, '娣辨祴绉戞妧', '娣辨祴', 1),
    (990010000000000003, '深测科技', '深测科技有限公司', 1),
    (990010000000000004, '瀛楄妭璺冲姩', '瀛楄妭璺冲姩', 1),
    (990010000000000005, '瀛楄妭璺冲姩', 'ByteDance', 1),
    (990010000000000006, '闃块噷宸村反', '闃块噷宸村反', 1),
    (990010000000000007, '闃块噷宸村反', '闃块噷', 1),
    (990010000000000008, '缇庡洟', '缇庡洟', 1)
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
        '深测科技二面复盘：Kafka 削峰、幂等消费和分布式排。',
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
    ),
    (
        990100000000000004,
        @demo_uid,
        1,
        '字节跳动 Java 后端二面复盘：高并发接口、限流和降级',
        '二面重点讨论活动页高并发接口设计，包含热点缓存预热、令牌桶限流、Redis 兜底、Spring 事务边界。Kafka 异步削峰。面试官要求说明每个方案的取舍、监控指标和故障恢复流程。',
        NULL,
        1,
        1,
        NOW(3) - INTERVAL 1 DAY,
        NOW(3),
        0
    ),
    (
        990100000000000005,
        @demo_uid,
        1,
        '美团后端工程师面经：MySQL 索引、订单一致性和压测复盘',
        '这一轮围绕订单链路展开，重点问。MySQL 组合索引设计、Redis 缓存穿透、库存一致性、接口压测指标以及如何把一次性能优化讲成可复盘的项目成果。',
        NULL,
        1,
        1,
        NOW(3) - INTERVAL 18 HOUR,
        NOW(3),
        0
    ),
    (
        990100000000000006,
        @demo_uid,
        1,
        '阿里巴巴 Java 后端终面准备：项目稳定性、消息链路和 STAR 表达',
        '终面更关注项目深度和表达质量，需要把 JVM 排查、Kafka 消息补偿、Spring 服务拆分和线上稳定性治理串成完整案例，并用 STAR 说明个人贡献和量化结果。',
        NULL,
        1,
        1,
        NOW(3) - INTERVAL 6 HOUR,
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
    (990100000000000003, 1, JSON_OBJECT('company', '娣辨祴绉戞妧', 'position', 'Java 鍚庣', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000004, 1, JSON_OBJECT('company', '瀛楄妭璺冲姩', 'position', 'Java 鍚庣', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000005, 1, JSON_OBJECT('company', '缇庡洟', 'position', '鍚庣宸ョ▼甯?', 'yearsOfExp', 4, 'interviewResult', 1)),
    (990100000000000006, 1, JSON_OBJECT('company', '闃块噷宸村反', 'position', 'Java 鍚庣', 'yearsOfExp', 5, 'interviewResult', 2))
ON DUPLICATE KEY UPDATE
    post_type = VALUES(post_type),
    ext_json = VALUES(ext_json),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_post_counter
    (post_id, view_count, like_count, comment_count, favorite_count, share_count)
VALUES
    (990100000000000001, 238, 31, 9, 18, 4),
    (990100000000000002, 192, 24, 7, 15, 3),
    (990100000000000003, 156, 19, 5, 11, 2),
    (990100000000000004, 286, 42, 11, 26, 6),
    (990100000000000005, 224, 35, 8, 21, 4),
    (990100000000000006, 198, 28, 6, 19, 3)
ON DUPLICATE KEY UPDATE
    view_count = GREATEST(view_count, VALUES(view_count)),
    like_count = GREATEST(like_count, VALUES(like_count)),
    comment_count = GREATEST(comment_count, VALUES(comment_count)),
    favorite_count = GREATEST(favorite_count, VALUES(favorite_count)),
    share_count = GREATEST(share_count, VALUES(share_count)),
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
    (990110000000000014, 990100000000000003, 3001),
    (990110000000000015, 990100000000000003, 1001),
    (990110000000000016, 990100000000000004, 1001),
    (990110000000000017, 990100000000000004, 1004),
    (990110000000000018, 990100000000000004, 1006),
    (990110000000000019, 990100000000000004, 1007),
    (990110000000000020, 990100000000000004, 2001),
    (990110000000000021, 990100000000000004, 3001),
    (990110000000000022, 990100000000000005, 1001),
    (990110000000000023, 990100000000000005, 1005),
    (990110000000000024, 990100000000000005, 1006),
    (990110000000000025, 990100000000000005, 2004),
    (990110000000000026, 990100000000000005, 3005),
    (990110000000000027, 990100000000000006, 1001),
    (990110000000000028, 990100000000000006, 1004),
    (990110000000000029, 990100000000000006, 1007),
    (990110000000000030, 990100000000000006, 1010),
    (990110000000000031, 990100000000000006, 2002),
    (990110000000000032, 990100000000000006, 3001)
ON DUPLICATE KEY UPDATE
    post_id = VALUES(post_id),
    tag_id = VALUES(tag_id);
