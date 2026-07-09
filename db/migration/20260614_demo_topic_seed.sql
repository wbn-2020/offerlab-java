-- 20260614_demo_topic_seed.sql
-- Non-destructive demo topic seed for existing local OfferLab databases.
-- Review before running; this script only upserts deterministic demo rows.
SET NAMES utf8mb4;

INSERT INTO t_community_topic (
    id, slug, topic_name, description, topic_type, cover_url,
    sort_order, featured, topic_status, created_by, updated_by
) VALUES
    (990500000000000001, 'java-backend-roadmap', 'Java 鍚庣鎴愰暱璺嚎',
     '涓茶仈 Spring銆丮ySQL銆丷edis銆並afka 鍜?JVM 楂橀闈㈣瘯澶嶇洏锛岄€傚悎鏈湴婕旂ず涓撻鑱氬悎涓庡叧娉ㄣ€?,
     'tech_stack', NULL, 100, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000002, 'redis-cache-consistency', 'Redis 缂撳瓨涓€鑷存€?,
     '鑱氬悎缂撳瓨绌块€忋€佸嚮绌裤€佸弻鍐欎竴鑷存€с€佺儹鐐归噸寤哄拰闄嶇骇琛ュ伩鐩稿叧甯栧瓙銆?,
     'scenario', NULL, 90, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000003, 'kafka-reliability', 'Kafka 绋冲畾鎬ф不鐞?,
     '瑕嗙洊娑堟伅骞傜瓑銆丱utbox銆佸爢绉帓鏌ャ€侀噸璇曟淇″拰娑堣垂鑰呭欢杩熻娴嬨€?,
     'scenario', NULL, 80, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000004, 'elasticsearch-search-index', '鎼滅储涓庣储寮曡瘖鏂?,
     '鍥寸粫 Elasticsearch 绱㈠紩銆佹悳绱㈤檷绾с€佸彫鍥炶瘖鏂拰閲嶅缓琛ュ伩鍋氫笓棰樻紨绀恒€?,
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
