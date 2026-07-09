-- 99_seed.sql
-- 婕旂ず绉嶅瓙鏁版嵁锛氭爣绛惧簱
SET NAMES utf8mb4;

INSERT INTO t_tag (id, tag_name, tag_type, use_count, is_official) VALUES
    (1001, 'Java', 1, 5, 1),
    (1002, 'Go', 1, 0, 1),
    (1003, 'Python', 1, 0, 1),
    (1004, 'Spring', 1, 3, 1),
    (1005, 'MySQL', 1, 3, 1),
    (1006, 'Redis', 1, 4, 1),
    (1007, 'Kafka', 1, 3, 1),
    (1008, 'Elasticsearch', 1, 0, 1),
    (1009, 'Netty', 1, 0, 1),
    (1010, 'JVM', 1, 2, 1),
    (2001, '瀛楄妭璺冲姩', 2, 1, 1),
    (2002, '闃块噷宸村反', 2, 1, 1),
    (2003, '鑵捐', 2, 0, 1),
    (2004, '缇庡洟', 2, 1, 1),
    (2005, '灏忕孩涔?, 2, 0, 1),
    (2006, '鐧惧害', 2, 0, 1),
    (2007, '娣辨祴绉戞妧', 2, 3, 1),
    (3001, 'Java 鍚庣', 3, 4, 1),
    (3002, 'Go 鍚庣', 3, 0, 1),
    (3003, '鍓嶇', 3, 0, 1),
    (3004, '绠楁硶宸ョ▼甯?, 3, 0, 1),
    (3005, '鍚庣宸ョ▼甯?, 3, 2, 1)
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    tag_type = VALUES(tag_type),
    use_count = GREATEST(use_count, VALUES(use_count)),
    is_official = VALUES(is_official);

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

-- Demo content author. This account is disabled by default and is not granted
-- admin permissions. Use db/local/seed_local_demo_admin.sql explicitly for
-- local-only admin demos.
INSERT INTO t_user_account
    (id, email, password_hash, password_salt, account_status)
VALUES
    (990000000000000001, 'demo.author@offerlab.local', '$2a$10$0CN4aiMIujTsf.AmOBUj8OAO.IrhNxcI5Toug4dnCegzpvuYHYmcG', '', 2)
ON DUPLICATE KEY UPDATE
    email = VALUES(email),
    password_hash = VALUES(password_hash),
    password_salt = VALUES(password_salt),
    account_status = VALUES(account_status),
    update_time = CURRENT_TIMESTAMP(3);

INSERT INTO t_user_profile
    (id, nickname, avatar_url, bio, intent_json)
VALUES
    (
        990000000000000001,
        'OfferLab 婕旂ず绠＄悊鍛?,
        'https://api.dicebear.com/7.x/initials/svg?seed=OfferLab',
        '姝ｅ湪鍑嗗娣辨祴绉戞妧 Java 鍚庣闈㈣瘯锛屽凡鏁寸悊棰樺崟銆佺瑪璁板拰 STAR 鑽夌銆?,
        JSON_OBJECT(
            'targetCompanies', JSON_ARRAY('娣辨祴绉戞妧'),
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
    (990000000000000001, 18, 7, 6, 196)
ON DUPLICATE KEY UPDATE
    follower_count = VALUES(follower_count),
    following_count = VALUES(following_count),
    post_count = VALUES(post_count),
    like_received = VALUES(like_received),
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
        990000000000000001,
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
        990000000000000001,
        1,
        '娣辨祴绉戞妧 浜岄潰锛欿afka 鍓婂嘲鍜屽垎甯冨紡鎺掓煡',
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
        990000000000000001,
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
        990000000000000001,
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
        990000000000000001,
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
        990000000000000001,
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

INSERT INTO t_interview_question
    (id, canonical_id, question_text, normalized_hash, answer_hint, exam_point, reference_answer, source_snippet,
     quality_reason, company, position, interview_round, difficulty, confidence, source_post_id, source_author_uid,
     status, appear_count, quality_score, create_time, update_time)
VALUES
    (
        990200000000000001,
        NULL,
        'Spring 浜嬪姟鍦ㄥ悓绫绘柟娉曞唴閮ㄨ皟鐢ㄦ椂涓轰粈涔堝彲鑳戒笉鐢熸晥锛熶綘鍦ㄩ」鐩噷鎬庝箞瑙勯伩锛?,
        SHA2('offerlab-demo-question-990200000000000001', 256),
        '浠?AOP 浠ｇ悊銆佷紶鎾涓恒€佸紓甯稿洖婊氳鍒欍€佷簨鍔¤竟鐣岃璁″洓鐐瑰洖绛斻€?,
        'Spring 浜嬪姟浠ｇ悊涓庡伐绋嬭惤鍦?,
        '鍚岀被鍐呴儴璋冪敤缁曡繃浠ｇ悊鏃讹紝@Transactional 涓嶄細琚嫤鎴€傚父瑙佸鐞嗘槸鎷嗗垎鍒扮嫭绔?Bean銆侀€氳繃浠ｇ悊瀵硅薄璋冪敤锛屾垨鎶婁簨鍔¤竟鐣屼笂绉诲埌搴旂敤鏈嶅姟灞傦紝鍚屾椂鏄庣‘ rollbackFor 鍜屼紶鎾涓恒€?,
        '闈㈣瘯瀹樿拷闂簡涓轰粈涔?private 鏂规硶鍜?self-invocation 浜嬪姟涓嶇敓鏁堛€?,
        '瑕嗙洊楂橀 Spring 浜嬪姟闄烽槺锛屽苟瑕佹眰缁撳悎椤圭洰缁忛獙璇存槑瑙勯伩鏂规銆?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '涓€闈?,
        'medium',
        0.9400,
        990100000000000001,
        990000000000000001,
        1,
        7,
        92,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990200000000000002,
        NULL,
        'Redis 缂撳瓨鍜屾暟鎹簱鍙屽啓涓嶄竴鑷存椂锛屼綘浼氬浣曡璁℃洿鏂扮瓥鐣ワ紵',
        SHA2('offerlab-demo-question-990200000000000002', 256),
        '鍏堣鏃佽矾缂撳瓨锛屽啀姣旇緝鍏堝垹缂撳瓨銆佸欢杩熷弻鍒犮€佹秷鎭ˉ鍋垮拰璇诲啓閿併€?,
        '缂撳瓨涓€鑷存€т笌闄嶇骇',
        '甯哥敤鏂规鏄洿鏂版暟鎹簱鍚庡垹闄ょ紦瀛橈紝骞堕€氳繃閲嶈瘯闃熷垪鎴?outbox 琛ュ伩鍒犻櫎澶辫触锛涘鐑偣 key 鍙姞浜掓枼閲嶅缓銆侀€昏緫杩囨湡鎴栫煭 TTL锛岄伩鍏嶆妸鍙屽啓椤哄簭璇存垚缁濆姝ｇ‘銆?,
        '鍊欓€変汉闇€瑕佽В閲婄紦瀛樺嚮绌垮拰鍒犻櫎澶辫触鏃剁殑鍏滃簳鏈哄埗銆?,
        '鍚屾椂鑰冨療 Redis 鍩虹銆佸紓甯歌ˉ鍋垮拰绾夸笂绋冲畾鎬ф剰璇嗐€?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '涓€闈?,
        'medium',
        0.9300,
        990100000000000001,
        990000000000000001,
        1,
        9,
        95,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990200000000000003,
        NULL,
        'MySQL 鎱㈡煡璇粠鍙戠幇鍒颁慨澶嶏紝浣犵殑鎺掓煡姝ラ鏄粈涔堬紵',
        SHA2('offerlab-demo-question-990200000000000003', 256),
        '鎱㈡棩蹇椼€丒XPLAIN銆佺储寮曢€夋嫨鎬с€佸洖琛ㄣ€佹帓搴忎复鏃惰〃銆佸垎椤典紭鍖栥€?,
        'MySQL 绱㈠紩涓庢€ц兘璇婃柇',
        '鍏堢敤鐩戞帶鍜屾參鏃ュ織瀹氫綅 SQL锛屽啀鐪?EXPLAIN 鐨?type銆乲ey銆乺ows銆丒xtra锛涚粨鍚堜笟鍔″熀鏁板垽鏂储寮曢『搴忥紝蹇呰鏃舵敼鍐欐煡璇€侀伩鍏嶅ぇ offset銆佹媶鍒嗗瀛楁鎴栬ˉ鍏呰鐩栫储寮曘€?,
        '闈㈣瘯瀹樼粰浜嗕竴涓?user_id + status + create_time 鐨勭粍鍚堟煡璇€?,
        '鏈夋槑纭瘖鏂矾寰勫拰鍙鐩樻寚鏍囷紝閫傚悎鍑嗗鍏徃椤靛睍绀恒€?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '涓€闈?,
        'easy',
        0.9100,
        990100000000000001,
        990000000000000001,
        1,
        6,
        88,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990200000000000004,
        NULL,
        'Kafka 娑堣垂绔浣曚繚璇佸箓绛夛紵濡傛灉鍑虹幇娑堟伅鍫嗙Н浣犱細鍏堢湅鍝簺鎸囨爣锛?,
        SHA2('offerlab-demo-question-990200000000000004', 256),
        '骞傜瓑閿€佸敮涓€绾︽潫銆佹秷璐逛綅鐐广€侀噸璇曟淇°€乴ag銆佹秷璐硅€楁椂銆佸垎鍖烘暟銆?,
        '娑堟伅闃熷垪绋冲畾鎬?,
        '骞傜瓑閫氬父渚濊禆涓氬姟鍞竴閿€佸幓閲嶈〃銆佺姸鎬佹満鎴栨暟鎹簱鍞竴绾︽潫锛涘爢绉椂鍏堢湅 consumer lag銆佸崟鏉″鐞嗚€楁椂銆佸け璐ラ噸璇曟瘮渚嬨€佸垎鍖哄€炬枩銆佷笅娓镐緷璧栬€楁椂锛屽啀鍐冲畾鎵╁鎴栭檷绾с€?,
        '浜岄潰鎶婃秷鎭爢绉拰绾夸笂鏁呴殰鎭㈠杩炶捣鏉ラ棶銆?,
        '鑳芥敮鎾戞ā鎷熼潰璇曞拰 STAR 鏁呴殰澶嶇洏銆?,
        '娣辨祴绉戞妧',
        '鍚庣宸ョ▼甯?,
        '浜岄潰',
        'hard',
        0.9200,
        990100000000000002,
        990000000000000001,
        1,
        8,
        94,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 12 HOUR
    ),
    (
        990200000000000005,
        NULL,
        '鎺ュ彛瓒呮椂鐜囩獊鐒跺崌楂橈紝浣犱細濡備綍瀹氫綅鏄簲鐢ㄣ€佹暟鎹簱杩樻槸澶栭儴渚濊禆闂锛?,
        SHA2('offerlab-demo-question-990200000000000005', 256),
        '鎸夊叆鍙ｆ寚鏍囥€侀摼璺拷韪€佺嚎绋嬫睜銆佽繛鎺ユ睜銆丼QL銆佷笅娓镐緷璧栭€愬眰缂╁皬鑼冨洿銆?,
        '绾夸笂闂瀹氫綅',
        '鍏堢‘璁ら敊璇巼銆丳95/P99銆佸疄渚嬪垎甯冨拰鍙樻洿绐楀彛锛屽啀鐢?trace 鎷嗗垎鑰楁椂锛涘悓鏃舵鏌ョ嚎绋嬫睜闃熷垪銆佽繛鎺ユ睜绛夊緟銆佹參 SQL銆丟C 鍜屼笅娓歌秴鏃讹紝鏈€鍚庣敤闄愭祦闄嶇骇鎭㈠鏈嶅姟銆?,
        '瑕佹眰璁叉竻妤氬厛鎭㈠鍐嶅畾浣嶇殑浼樺厛绾с€?,
        '璐磋繎鍚庣宀椾綅鐪熷疄鍦烘櫙锛岄€傚悎 /me/prep 澶嶄範璁″垝銆?,
        '娣辨祴绉戞妧',
        '鍚庣宸ョ▼甯?,
        '浜岄潰',
        'hard',
        0.9000,
        990100000000000002,
        990000000000000001,
        1,
        5,
        90,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 12 HOUR
    ),
    (
        990200000000000006,
        NULL,
        'JVM 鑰佸勾浠ｆ寔缁笂娑ㄤ絾 Full GC 鍚庝笅闄嶄笉鏄庢樉锛屼綘浼氭€庝箞鍒嗘瀽锛?,
        SHA2('offerlab-demo-question-990200000000000006', 256),
        '鍖哄垎鍐呭瓨娉勬紡銆佺紦瀛樿啫鑳€銆佸ぇ瀵硅薄銆佺被鍔犺浇锛岀粨鍚?dump 鍜?GC 鏃ュ織銆?,
        'JVM 鍐呭瓨鎺掓煡',
        '鍏堢湅 GC 鏃ュ織鍜岀洃鎺х‘璁よ秼鍔匡紝鍐嶆姄 heap dump 瀵规瘮瀵硅薄寮曠敤閾撅紱甯歌鍘熷洜鍖呮嫭鏈湴缂撳瓨鏃犱笂闄愩€侀潤鎬侀泦鍚堟寔鏈夈€佺嚎绋嬫睜浠诲姟鍫嗙Н銆佸ぇ瀵硅薄鎴栫被鍔犺浇娉勬紡銆?,
        '鎶€鏈姞闈㈣姹傝鏄?MAT dominator tree 鎬庝箞鐪嬨€?,
        '楂橀 JVM 鎺掗殰棰橈紝鑳戒赴瀵屽叕鍙稿噯澶囧寘鐨勯毦棰樺垪琛ㄣ€?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '鎶€鏈姞闈?,
        'hard',
        0.8900,
        990100000000000003,
        990000000000000001,
        1,
        4,
        87,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    ),
    (
        990200000000000007,
        NULL,
        '濡備綍璁捐涓€涓敮鎸侀珮骞跺彂鏌ヨ鐨勯潰璇曢鎼滅储鎺ュ彛锛?,
        SHA2('offerlab-demo-question-990200000000000007', 256),
        '鍏抽敭璇嶅彫鍥炪€佺瓫閫夋潯浠躲€佸垎椤垫父鏍囥€佺紦瀛樸€丒S 闄嶇骇鍜岀储寮曢噸寤恒€?,
        '鎼滅储涓庢帴鍙ｈ璁?,
        '鎼滅储鎺ュ彛搴斿尯鍒嗗彫鍥炲拰璇︽儏璇诲彇锛屼娇鐢?ES 鎴栧€掓帓绱㈠紩鎵挎帴鍏抽敭璇嶏紝鏁版嵁搴撳厹搴曢渶瑕侀檺鍒跺垎椤垫繁搴︼紱鐑棬鏉′欢鍙紦瀛橈紝绱㈠紩鏇存柊瑕佹湁閲嶈瘯鍜屽彲瑙傛祴浠诲姟鐘舵€併€?,
        '鍊欓€変汉琚姹傝ˉ鍏呯储寮曞け璐ュ悗鐨勮ˉ鍋挎柟妗堛€?,
        '涓?OfferLab 鑷韩棰樺簱鍦烘櫙璐村悎锛屾柟渚挎紨绀烘悳绱㈠拰楂樹寒銆?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '鎶€鏈姞闈?,
        'medium',
        0.8800,
        990100000000000003,
        990000000000000001,
        1,
        3,
        84,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    ),
    (
        990200000000000008,
        NULL,
        '椤圭洰缁忓巻閲屼綘濡備綍鐢?STAR 璁叉竻涓€娆＄ǔ瀹氭€т紭鍖栵紵',
        SHA2('offerlab-demo-question-990200000000000008', 256),
        'Situation銆乀ask銆丄ction銆丷esult锛屾瘡涓€姝ラ兘瑕佹湁鎸囨爣鍜屼釜浜鸿础鐚€?,
        '椤圭洰琛ㄨ揪涓?STAR',
        '鍏堟弿杩颁笟鍔″満鏅拰鏁呴殰褰卞搷锛屽啀璇存槑鑷繁鐨勪换鍔¤竟鐣岋紱琛屽姩閮ㄥ垎璁茬洃鎺с€侀檺娴併€佺紦瀛樸€丼QL 浼樺寲绛夊叧閿彇鑸嶏紝缁撴灉瑕佽惤鍒板欢杩熴€侀敊璇巼銆佹垚鏈垨浜烘晥鎸囨爣銆?,
        'HR 鍓嶆妧鏈姞闈細鎶婃妧鏈柟妗堝拰琛ㄨ揪鑳藉姏涓€璧疯€冨療銆?,
        '琛ラ綈椤圭洰琛ㄨ揪缁村害锛岄伩鍏嶉搴撳彧鏈夋妧鏈煡璇嗙偣銆?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '鎶€鏈姞闈?,
        'easy',
        0.8700,
        990100000000000003,
        990000000000000001,
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
    (990300000000000001, 990000000000000001, 'company', '娣辨祴绉戞妧', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'urgent', '浼樺厛鍒锋繁娴嬬鎶€楂橀鍚庣棰橈紝鍑嗗涓€闈㈠埌鍔犻潰鐨勫畬鏁撮摼璺€?),
    (990300000000000002, 990000000000000001, 'position', 'Java 鍚庣', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'high', '鍥寸粫 Spring銆丮ySQL銆丷edis銆並afka銆丣VM 鍋氫笓椤瑰鐩樸€?),
    (990300000000000003, 990000000000000001, 'tag', 'Kafka', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'medium', '琛ラ綈娑堟伅鍫嗙Н銆佸箓绛夊拰閲嶈瘯姝讳俊妗堜緥銆?)
ON DUPLICATE KEY UPDATE
    interview_date = VALUES(interview_date),
    priority = VALUES(priority),
    note = VALUES(note);

INSERT INTO t_user_question_progress
    (id, uid, question_id, progress_status, favorite, note, mistake_reason, answer_draft, star_story,
     next_review_at, last_reviewed_at, review_count, review_interval_days, create_time, update_time)
VALUES
    (
        990310000000000001,
        990000000000000001,
        990200000000000001,
        'mastered',
        1,
        '鍚岀被鏂规硶璋冪敤涓嶈蛋浠ｇ悊锛岃涓诲姩璇村嚭浜嬪姟杈圭晫涓轰粈涔堟斁鍦?service 缂栨帓灞傘€?,
        NULL,
        '鎴戜細鍏堣В閲?Spring AOP 浠ｇ悊鏈哄埗锛屽啀缁撳悎璁㈠崟缁撶畻椤圭洰璇存槑濡備綍鎶婁簨鍔″叆鍙ｆ斁鍒板簲鐢ㄦ湇鍔″眰锛屽苟琛ュ厖 rollbackFor 鍜屼紶鎾涓恒€?,
        'S: 璁㈠崟缁撶畻瀛樺湪閮ㄥ垎鍐欏叆椋庨櫓锛汿: 姊崇悊浜嬪姟杈圭晫锛汚: 鎷嗗垎搴撳瓨銆佽鍗曘€佹祦姘村啓鍏ュ苟缁熶竴鐢卞簲鐢ㄦ湇鍔＄紪鎺掞紱R: 鍥炴粴璺緞娓呮櫚锛屽帇娴嬫棤鑴忔暟鎹€?,
        DATE_ADD(NOW(3), INTERVAL 10 DAY),
        NOW(3) - INTERVAL 1 DAY,
        3,
        10,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990310000000000002,
        990000000000000001,
        990200000000000002,
        'review',
        1,
        '缂撳瓨涓€鑷存€т笉鑳藉彧鑳屽欢杩熷弻鍒狅紝瑕佽鍒犻櫎澶辫触琛ュ伩鍜岀儹鐐归噸寤恒€?,
        'concept',
        '鏃佽矾缂撳瓨涓嬪厛鏇存柊 DB 鍐嶅垹缂撳瓨锛涘垹闄ゅけ璐ヨ繘鍏ラ噸璇曢槦鍒楋紝鐑偣 key 鐢ㄩ€昏緫杩囨湡鍜屼簰鏂ラ攣閲嶅缓銆?,
        'S: 娲诲姩椤靛簱瀛樼紦瀛樺伓鍙戣剰璇伙紱T: 闄嶄綆涓嶄竴鑷寸獥鍙ｏ紱A: DB 鎴愬姛鍚庡垹缂撳瓨锛屽け璐ュ啓鍏ラ噸璇曚换鍔★紝鐑偣 key 鍔犵煭 TTL锛汻: 鎶曡瘔涓嬮檷锛岀紦瀛樺懡涓繚鎸佺ǔ瀹氥€?,
        NOW(3) - INTERVAL 2 HOUR,
        NOW(3) - INTERVAL 2 DAY,
        2,
        3,
        NOW(3) - INTERVAL 7 DAY,
        NOW(3) - INTERVAL 2 HOUR
    ),
    (
        990310000000000003,
        990000000000000001,
        990200000000000003,
        'learning',
        0,
        'EXPLAIN 瀛楁瑕佽 type/key/rows/Extra锛屽啀钀藉埌绱㈠紩椤哄簭銆?,
        'expression',
        '鎱?SQL 鎺掓煡浼氬厛鐢ㄦ參鏃ュ織瀹氫綅锛屽啀鐪?EXPLAIN 鍜屾暟鎹垎甯冿紝鏈€鍚庣敤缁勫悎绱㈠紩銆佽鐩栫储寮曟垨鍒嗛〉鏀瑰啓楠岃瘉鏁堟灉銆?,
        NULL,
        DATE_ADD(NOW(3), INTERVAL 1 DAY),
        NOW(3) - INTERVAL 3 DAY,
        1,
        2,
        NOW(3) - INTERVAL 6 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990310000000000004,
        990000000000000001,
        990200000000000004,
        'review',
        1,
        'Kafka 鍫嗙Н瑕佹寜 lag銆佹秷璐硅€楁椂銆佸け璐ラ噸璇曘€佸垎鍖哄€炬枩鍜屼笅娓歌€楁椂鎷嗐€?,
        'project',
        '骞傜瓑鐢ㄤ笟鍔″敮涓€閿拰鏁版嵁搴撳敮涓€绾︽潫锛涘爢绉厛纭 lag 鍜屽鐞嗚€楁椂锛屽啀鍒ゆ柇鎵╁銆侀檺娴佽繕鏄笅娓搁檷绾с€?,
        'S: 淇冮攢娑堟伅鍫嗙Н瀵艰嚧灞ョ害寤惰繜锛汿: 鎭㈠娑堣垂骞堕伩鍏嶉噸澶嶆墸鍑忥紱A: 澧炲姞骞傜瓑琛ㄣ€佽皟鏁存壒閲忔彁浜ゃ€侀殧绂绘參涓嬫父锛汻: lag 鍦?20 鍒嗛挓鍐呮仮澶嶅埌瀹夊叏姘翠綅銆?,
        NOW(3) - INTERVAL 1 HOUR,
        NOW(3) - INTERVAL 2 DAY,
        2,
        3,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 1 HOUR
    ),
    (
        990310000000000005,
        990000000000000001,
        990200000000000005,
        'todo',
        0,
        '闇€瑕佽ˉ涓€娈甸摼璺拷韪畾浣嶅閮ㄤ緷璧栬秴鏃剁殑鐪熷疄妗堜緥銆?,
        'memory',
        NULL,
        NULL,
        NULL,
        NULL,
        0,
        1,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 5 DAY
    ),
    (
        990310000000000006,
        990000000000000001,
        990200000000000006,
        'learning',
        1,
        'JVM 棰樿鎶?dump銆丟C 鏃ュ織鍜屽璞″紩鐢ㄩ摼杩炶捣鏉ャ€?,
        'concept',
        '鎴戜細鍏堢湅 Full GC 鍚庤€佸勾浠ｅ洖鏀舵瘮渚嬶紝鍐嶆姄 heap dump锛岀敤 MAT 鐪?dominator tree 鍜?GC Roots锛岀‘璁ゆ槸鍚︽槸缂撳瓨鎴栭潤鎬侀泦鍚堟寔鏈夈€?,
        NULL,
        DATE_ADD(NOW(3), INTERVAL 2 DAY),
        NOW(3) - INTERVAL 1 DAY,
        1,
        2,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990310000000000007,
        990000000000000001,
        990200000000000007,
        'todo',
        0,
        '鍑嗗鎶?OfferLab 棰樺簱鎼滅储浣滀负椤圭洰妗堜緥鏉ヨ銆?,
        NULL,
        '鎼滅储鎺ュ彛浼氱敤 ES 鍋氬叧閿瘝鍙洖锛孌B 鏌ヨ璇︽儏锛涚储寮曞け璐ヨ繘鍏ラ噸璇曚换鍔★紝鍓嶇灞曠ず浠诲姟鐘舵€佸拰闄嶇骇缁撴灉銆?,
        'S: 棰樺簱澧為暱鍚庢悳绱㈠欢杩熷崌楂橈紱T: 淇濇寔鎼滅储浣撻獙锛汚: 寮曞叆绱㈠紩閲嶅缓鍜屽け璐ラ噸璇曪紱R: 鐑棬鍏抽敭璇嶅搷搴旂ǔ瀹氬湪鐧炬绉掔骇銆?,
        NULL,
        NULL,
        0,
        1,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    ),
    (
        990310000000000008,
        990000000000000001,
        990200000000000008,
        'review',
        1,
        'STAR 琛ㄨ揪瑕侀噺鍖栫粨鏋滐紝涓嶈鍙鍋氫簡浼樺寲銆?,
        'expression',
        '鎴戜細鐢ㄥ満鏅€佷换鍔°€佽鍔ㄣ€佺粨鏋滃洓娈靛洖绛旓紝骞舵妸缁撴灉钀藉埌閿欒鐜囥€丳99 寤惰繜鍜屾帓鏌ユ晥鐜囥€?,
        'S: 鏍稿績鎺ュ彛鍦ㄦ椿鍔ㄦ湡闂磋秴鏃讹紱T: 鎴戣礋璐ｅ畾浣嶅苟闄嶄綆瓒呮椂锛汚: 鍔?tracing銆佷紭鍖栨參 SQL銆佺儹鐐圭紦瀛橀鐑紱R: P99 浠?1.8s 闄嶅埌 420ms锛岄敊璇巼浣庝簬 0.1%銆?,
        NOW(3) - INTERVAL 30 MINUTE,
        NOW(3) - INTERVAL 1 DAY,
        1,
        2,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3) - INTERVAL 30 MINUTE
    )
ON DUPLICATE KEY UPDATE
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
    (990400000000000001, 990000000000000001, '娣辨祴绉戞妧', 'Java 鍚庣', 'medium', 'Kafka', 3, 3, 12, 1280, 'completed', NOW(3) - INTERVAL 1 DAY, NOW(3) - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE
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
     company_snapshot, position_snapshot, round_snapshot, difficulty_snapshot, answer_text, self_review,
     score, ai_reviewed, ai_review_status, ai_score, ai_completeness, ai_project_expression, ai_follow_up_suggestion)
VALUES
    (
        990410000000000001,
        990400000000000001,
        990000000000000001,
        990200000000000002,
        1,
        'Redis 缂撳瓨鍜屾暟鎹簱鍙屽啓涓嶄竴鑷存椂锛屼綘浼氬浣曡璁℃洿鏂扮瓥鐣ワ紵',
        '鏃佽矾缂撳瓨銆佸垹闄ゅけ璐ヨˉ鍋垮拰鐑偣閲嶅缓銆?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '涓€闈?,
        'medium',
        '鍏堟洿鏂版暟鎹簱鍐嶅垹闄ょ紦瀛橈紝鍒犻櫎澶辫触鍐欏叆閲嶈瘯浠诲姟锛涚儹鐐?key 浣跨敤閫昏緫杩囨湡鍜屼簰鏂ラ噸寤恒€?,
        '杩橀渶瑕佽ˉ鍏呬负浠€涔堜笉鐩存帴鍏堝啓缂撳瓨銆?,
        4,
        1,
        'SUCCEEDED',
        82,
        '瑕嗙洊浜嗕富娴佺▼鍜岃ˉ鍋匡紝浣嗗彲浠ヨˉ鍏呭苟鍙戣鍐欑獥鍙ｃ€?,
        '椤圭洰妗堜緥鏈夐洀褰紝闇€瑕佸鍔犻噺鍖栨寚鏍囥€?,
        '琛ヤ竴涓垹闄ゅけ璐ュ悗鐨勭洃鎺у憡璀︽寚鏍囥€?
    ),
    (
        990410000000000002,
        990400000000000001,
        990000000000000001,
        990200000000000004,
        2,
        'Kafka 娑堣垂绔浣曚繚璇佸箓绛夛紵濡傛灉鍑虹幇娑堟伅鍫嗙Н浣犱細鍏堢湅鍝簺鎸囨爣锛?,
        '骞傜瓑閿€佸敮涓€绾︽潫銆乴ag銆佸垎鍖哄€炬枩銆?,
        '娣辨祴绉戞妧',
        '鍚庣宸ョ▼甯?,
        '浜岄潰',
        'hard',
        '鐢ㄤ笟鍔″敮涓€閿拰鍘婚噸琛ㄤ繚璇佸箓绛夛紱鍫嗙Н鍏堢湅 lag銆佹秷璐硅€楁椂銆佸け璐ラ噸璇曟瘮渚嬨€佸垎鍖哄€炬枩鍜屼笅娓歌€楁椂銆?,
        '鍥炵瓟瀹屾暣锛屼絾 STAR 妗堜緥鍙互鏇寸揣銆?,
        4,
        1,
        'SUCCEEDED',
        80,
        '鍏抽敭鎸囨爣榻愬叏銆?,
        '闇€瑕佹妸鏁呴殰鎭㈠杩囩▼鍘嬬缉鎴?60 绉掔増鏈€?,
        '鍑嗗涓€鍙ユ槑纭殑鏈€缁堢粨鏋滄寚鏍囥€?
    ),
    (
        990410000000000003,
        990400000000000001,
        990000000000000001,
        990200000000000008,
        3,
        '椤圭洰缁忓巻閲屼綘濡備綍鐢?STAR 璁叉竻涓€娆＄ǔ瀹氭€т紭鍖栵紵',
        'STAR 鍥涙鍜岄噺鍖栫粨鏋溿€?,
        '娣辨祴绉戞妧',
        'Java 鍚庣',
        '鎶€鏈姞闈?,
        'easy',
        '鎴戜細鐢ㄥ満鏅€佷换鍔°€佽鍔ㄣ€佺粨鏋滃洓娈佃锛屾妸 P99銆侀敊璇巼鍜屾仮澶嶆椂闂翠綔涓虹粨鏋滄寚鏍囥€?,
        '琛ㄨ揪鏇撮『浜嗭紝涓嬩竴鐗堣鍔犱釜浜鸿础鐚竟鐣屻€?,
        4,
        1,
        'SUCCEEDED',
        85,
        '缁撴瀯娓呮櫚銆?,
        '缁撴灉閲忓寲杈冨ソ锛屼釜浜鸿础鐚繕鍙洿绐佸嚭銆?,
        '琛ュ厖鑷繁璐熻矗鐨勬ā鍧楀拰鍗忎綔杈圭晫銆?
    )
ON DUPLICATE KEY UPDATE
    sequence_no = VALUES(sequence_no),
    question_text_snapshot = VALUES(question_text_snapshot),
    answer_hint_snapshot = VALUES(answer_hint_snapshot),
    company_snapshot = VALUES(company_snapshot),
    position_snapshot = VALUES(position_snapshot),
    round_snapshot = VALUES(round_snapshot),
    difficulty_snapshot = VALUES(difficulty_snapshot),
    answer_text = VALUES(answer_text),
    self_review = VALUES(self_review),
    score = VALUES(score),
    ai_reviewed = VALUES(ai_reviewed),
    ai_review_status = VALUES(ai_review_status),
    ai_score = VALUES(ai_score),
    ai_completeness = VALUES(ai_completeness),
    ai_project_expression = VALUES(ai_project_expression),
    ai_follow_up_suggestion = VALUES(ai_follow_up_suggestion);
