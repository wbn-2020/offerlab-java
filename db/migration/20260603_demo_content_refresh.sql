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
    (3005, '鍚庣宸ョ▼甯?, 3, 2, 1)
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
        'OfferLab 婕旂ず绠＄悊鍛?,
        'https://api.dicebear.com/7.x/initials/svg?seed=OfferLab',
        '姝ｅ湪鍑嗗 Java 鍚庣闈㈣瘯锛岄噸鐐瑰鐩橀珮骞跺彂銆佺紦瀛樹竴鑷存€с€佹秷鎭摼璺拰椤圭洰琛ㄨ揪銆?,
        JSON_OBJECT(
            'targetCompanies', JSON_ARRAY('瀛楄妭璺冲姩', '闃块噷宸村反', '缇庡洟'),
            'targetPositions', JSON_ARRAY('Java 鍚庣', '鍚庣宸ョ▼甯?),
            'targetCity', '涓婃捣'
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
    (990010000000000003, '娣辨祴绉戞妧', '娣辨祴绉戞妧鏈夐檺鍏徃', 1),
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
        '娣辨祴绉戞妧 Java 鍚庣涓€闈㈠鐩橈細缂撳瓨銆佷簨鍔″拰鎱?SQL',
        '涓€闈㈤噸鐐瑰洿缁?Spring 浜嬪姟浼犳挱銆丷edis 缂撳瓨涓€鑷存€с€丮ySQL 鎱?SQL 鎺掓煡鍜岄」鐩腑鐨勯檷绾ц璁°€傞潰璇曞畼浼氳拷闂负浠€涔堣繖涔堣璁★紝浠ュ強绾夸笂鎸囨爣濡備綍楠岃瘉銆?,
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
        '娣辨祴绉戞妧浜岄潰澶嶇洏锛欿afka 鍓婂嘲銆佸箓绛夋秷璐瑰拰鍒嗗竷寮忔帓鏌?,
        '浜岄潰鏇村叧娉ㄧ郴缁熻璁″拰绋冲畾鎬э紝鑱婂埌 Kafka 娑堣垂骞傜瓑銆佹秷鎭爢绉畾浣嶃€佹帴鍙ｈ秴鏃舵不鐞嗭紝浠ュ強濡備綍鎶婁竴娆＄嚎涓婃晠闅滆鎴愮粨鏋勫寲 STAR 妗堜緥銆?,
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
        '娣辨祴绉戞妧 HR 鍓嶆妧鏈姞闈細JVM銆佺储寮曞拰椤圭洰浜偣',
        '杩欎竴杞鐩鐩?JVM 鍐呭瓨妯″瀷銆佺储寮曢€夋嫨鎬с€佹帴鍙ｅ帇娴嬬粨鏋滃鐩橈紝浠ュ強濡備綍鎶婇」鐩寒鐐瑰拰宀椾綅瑕佹眰杩炴帴璧锋潵銆?,
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
        '瀛楄妭璺冲姩 Java 鍚庣浜岄潰澶嶇洏锛氶珮骞跺彂鎺ュ彛銆侀檺娴佸拰闄嶇骇',
        '浜岄潰閲嶇偣璁ㄨ娲诲姩椤甸珮骞跺彂鎺ュ彛璁捐锛屽寘鍚儹鐐圭紦瀛橀鐑€佷护鐗屾《闄愭祦銆丷edis 鍏滃簳銆丼pring 浜嬪姟杈圭晫鍜?Kafka 寮傛鍓婂嘲銆傞潰璇曞畼瑕佹眰璇存槑姣忎釜鏂规鐨勫彇鑸嶃€佺洃鎺ф寚鏍囧拰鏁呴殰鎭㈠娴佺▼銆?,
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
        '缇庡洟鍚庣宸ョ▼甯堥潰缁忥細MySQL 绱㈠紩銆佽鍗曚竴鑷存€у拰鍘嬫祴澶嶇洏',
        '杩欎竴杞洿缁曡鍗曢摼璺睍寮€锛岄噸鐐归棶鍒?MySQL 缁勫悎绱㈠紩璁捐銆丷edis 缂撳瓨绌块€忋€佸簱瀛樹竴鑷存€с€佹帴鍙ｅ帇娴嬫寚鏍囦互鍙婂浣曟妸涓€娆℃€ц兘浼樺寲璁叉垚鍙鐩樼殑椤圭洰鎴愭灉銆?,
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
        '闃块噷宸村反 Java 鍚庣缁堥潰鍑嗗锛氶」鐩ǔ瀹氭€с€佹秷鎭摼璺拰 STAR 琛ㄨ揪',
        '缁堥潰鏇村叧娉ㄩ」鐩繁搴﹀拰琛ㄨ揪璐ㄩ噺锛岄渶瑕佹妸 JVM 鎺掓煡銆並afka 娑堟伅琛ュ伩銆丼pring 鏈嶅姟鎷嗗垎鍜岀嚎涓婄ǔ瀹氭€ф不鐞嗕覆鎴愬畬鏁存渚嬶紝骞剁敤 STAR 璇存槑涓汉璐＄尞鍜岄噺鍖栫粨鏋溿€?,
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
    (990100000000000002, 1, JSON_OBJECT('company', '娣辨祴绉戞妧', 'position', '鍚庣宸ョ▼甯?, 'yearsOfExp', 4, 'interviewResult', 2)),
    (990100000000000003, 1, JSON_OBJECT('company', '娣辨祴绉戞妧', 'position', 'Java 鍚庣', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000004, 1, JSON_OBJECT('company', '瀛楄妭璺冲姩', 'position', 'Java 鍚庣', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000005, 1, JSON_OBJECT('company', '缇庡洟', 'position', '鍚庣宸ョ▼甯?, 'yearsOfExp', 4, 'interviewResult', 1)),
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
