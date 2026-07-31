-- 20260614_demo_topic_seed.sql
-- Non-destructive demo topic seed for existing local OfferLab databases.
-- Review before running; this script only upserts deterministic demo rows.
SET NAMES utf8mb4;

INSERT INTO t_community_topic (
    id, slug, topic_name, description, topic_type, cover_url,
    sort_order, featured, topic_status, created_by, updated_by
) VALUES
    (990500000000000001, 'java-backend-roadmap', 'Java 后端成长路线',
     '串联 Spring、MySQL、Redis、Kafka 。JVM 高频面试复盘，适合本地演示专题聚合与关注。',
     'tech_stack', NULL, 100, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000002, 'redis-cache-consistency', 'Redis 缓存一致。',
     '聚合缓存穿透、击穿、双写一致性、热点重建和降级补偿相关帖子。',
     'scenario', NULL, 90, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000003, 'kafka-reliability', 'Kafka 绋冲畾鎬ф不鐞?',
     '覆盖消息幂等、Outbox、堆积排查、重试死信和消费者延迟观测。',
     'scenario', NULL, 80, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000004, 'elasticsearch-search-index', '搜索与索引诊。',
     '围绕 Elasticsearch 索引、搜索降级、召回诊断和重建补偿做专题演示。',
     'tech_stack', NULL, 70, 1, 1, 990000000000000001, 990000000000000001)
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

INSERT INTO t_community_topic_tag (id, topic_id, tag_id) VALUES
    (990510000000000001, 990500000000000001, 1001),
    (990510000000000002, 990500000000000001, 1004),
    (990510000000000003, 990500000000000001, 1005),
    (990510000000000004, 990500000000000001, 1006),
    (990510000000000005, 990500000000000001, 1007),
    (990510000000000006, 990500000000000002, 1006),
    (990510000000000007, 990500000000000003, 1007),
    (990510000000000008, 990500000000000004, 1008)
ON DUPLICATE KEY UPDATE
    topic_id = VALUES(topic_id),
    tag_id = VALUES(tag_id);
