-- 99_seed.sql
-- 演示种子数据：标签库
SET NAMES utf8mb4;

-- Personalized demo records stay on the dedicated local demo identity. Never
-- infer a real user from an existing database and attach private prep data to it.
SET @offerlab_demo_user_uid := 990000000000000001;

-- Validate the deterministic account identity before the first data write.
-- An explicitly enabled local demo admin is preserved; unrelated accounts or
-- duplicate reserved emails fail closed.
SET @offerlab_preserve_existing_local_admin := EXISTS (
    SELECT 1
    FROM t_user_account account
    JOIN t_user_admin admin
      ON admin.uid = account.id
     AND admin.role_code = 'ADMIN'
     AND admin.enabled = 1
    WHERE account.id = 990000000000000001
      AND account.email = 'demo.admin@offerlab.local'
      AND account.account_status = 1
      AND account.is_deleted = 0
);

SET @offerlab_demo_author_identity_conflicts := (
    SELECT COUNT(*)
    FROM t_user_account account
    WHERE account.id = 990000000000000001
      AND NOT (
          account.is_deleted = 0
          AND (
              account.email = 'demo.author@offerlab.local'
              OR @offerlab_preserve_existing_local_admin = 1
          )
      )
) + (
    SELECT COUNT(*)
    FROM t_user_account account
    WHERE account.email IN ('demo.author@offerlab.local', 'demo.admin@offerlab.local')
      AND account.id <> 990000000000000001
 ) + (
    SELECT COUNT(*)
    FROM t_user_profile profile
    WHERE profile.id = 990000000000000001
      AND NOT EXISTS (
          SELECT 1
          FROM t_user_account account
          WHERE account.id = profile.id
            AND account.is_deleted = 0
            AND account.email IN ('demo.author@offerlab.local', 'demo.admin@offerlab.local')
      )
 ) + (
    SELECT COUNT(*)
    FROM t_user_admin admin
    WHERE admin.uid = 990000000000000001
      AND @offerlab_preserve_existing_local_admin = 0
);

DROP TEMPORARY TABLE IF EXISTS offerlab_demo_seed_assertion;
CREATE TEMPORARY TABLE offerlab_demo_seed_assertion (
    assertion_name VARCHAR(96) NOT NULL,
    conflict_count BIGINT NOT NULL,
    CONSTRAINT chk_offerlab_demo_seed_no_conflicts CHECK (conflict_count = 0)
) ENGINE=InnoDB;

-- Fresh database initialization normally starts with autocommit enabled. Keep
-- every persistent seed write in one transaction, then restore the caller's
-- mode at the end. The existing-database refresh wrapper enters with
-- autocommit disabled, so restoring that value does not commit its outer
-- transaction before refresh postconditions run.
SET @offerlab_demo_seed_original_autocommit := @@SESSION.autocommit;
SET SESSION autocommit = 0;

INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('demo_author_identity', @offerlab_demo_author_identity_conflicts);

-- Validate every deterministic demo asset before the first persistent write.
-- Both the reserved id and its natural identity must agree; otherwise an
-- existing local row could be silently repurposed by ON DUPLICATE KEY UPDATE.
SET @offerlab_demo_seed_asset_identity_conflicts := (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"1001","name":"Java"},{"id":"1002","name":"Go"},{"id":"1003","name":"Python"},{"id":"1004","name":"Spring"},{"id":"1005","name":"MySQL"},{"id":"1006","name":"Redis"},{"id":"1007","name":"Kafka"},{"id":"1008","name":"Elasticsearch"},{"id":"1009","name":"Netty"},{"id":"1010","name":"JVM"},{"id":"2001","name":"字节跳动"},{"id":"2002","name":"阿里巴巴"},{"id":"2003","name":"腾讯"},{"id":"2004","name":"美团"},{"id":"2005","name":"小红书"},{"id":"2006","name":"百度"},{"id":"2007","name":"深测科技"},{"id":"3001","name":"Java 后端"},{"id":"3002","name":"Go 后端"},{"id":"3003","name":"前端"},{"id":"3004","name":"算法工程师"},{"id":"3005","name":"后端工程师"},{"id":"991000000000000001","name":"开源工具"},{"id":"991000000000000002","name":"数字安全"},{"id":"991000000000000003","name":"职业成长"},{"id":"991000000000000004","name":"沟通协作"},{"id":"991000000000000005","name":"财务安全"},{"id":"991000000000000006","name":"风险教育"},{"id":"991000000000000007","name":"学习方法"},{"id":"991000000000000008","name":"阅读笔记"},{"id":"991000000000000009","name":"生活经验"},{"id":"991000000000000010","name":"健康管理"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            tag_name VARCHAR(64) PATH '$.name'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_tag existing
        WHERE existing.id = expected.id
          AND NOT (existing.tag_name <=> expected.tag_name)
    )
       OR EXISTS (
        SELECT 1
        FROM t_tag existing
        WHERE existing.tag_name = expected.tag_name
          AND existing.is_deleted = 0
          AND existing.id <> expected.id
    )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990500000000000001","slug":"java-backend-roadmap"},{"id":"990500000000000002","slug":"redis-cache-consistency"},{"id":"990500000000000003","slug":"kafka-reliability"},{"id":"990500000000000004","slug":"elasticsearch-search-index"},{"id":"991500000000000001","slug":"digital-life-open-tools"},{"id":"991500000000000002","slug":"career-transition-field-notes"},{"id":"991500000000000003","slug":"personal-finance-risk-basics"},{"id":"991500000000000004","slug":"learning-systems"},{"id":"991500000000000005","slug":"everyday-life-practice"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            slug VARCHAR(64) PATH '$.slug'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_community_topic existing
        WHERE existing.id = expected.id
          AND NOT (existing.slug <=> expected.slug)
    )
       OR EXISTS (
        SELECT 1
        FROM t_community_topic existing
        WHERE existing.slug = expected.slug
          AND existing.is_deleted = 0
          AND existing.id <> expected.id
    )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990510000000000001","topic":"990500000000000001","tag":"1001"},{"id":"990510000000000002","topic":"990500000000000001","tag":"1004"},{"id":"990510000000000003","topic":"990500000000000001","tag":"1005"},{"id":"990510000000000004","topic":"990500000000000001","tag":"1006"},{"id":"990510000000000005","topic":"990500000000000001","tag":"1007"},{"id":"990510000000000006","topic":"990500000000000002","tag":"1006"},{"id":"990510000000000007","topic":"990500000000000003","tag":"1007"},{"id":"990510000000000008","topic":"990500000000000004","tag":"1008"},{"id":"991510000000000001","topic":"991500000000000001","tag":"991000000000000001"},{"id":"991510000000000002","topic":"991500000000000001","tag":"991000000000000002"},{"id":"991510000000000003","topic":"991500000000000002","tag":"991000000000000003"},{"id":"991510000000000004","topic":"991500000000000002","tag":"991000000000000004"},{"id":"991510000000000005","topic":"991500000000000003","tag":"991000000000000005"},{"id":"991510000000000006","topic":"991500000000000003","tag":"991000000000000006"},{"id":"991510000000000007","topic":"991500000000000004","tag":"991000000000000007"},{"id":"991510000000000008","topic":"991500000000000004","tag":"991000000000000008"},{"id":"991510000000000009","topic":"991500000000000005","tag":"991000000000000009"},{"id":"991510000000000010","topic":"991500000000000005","tag":"991000000000000010"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            topic_id BIGINT PATH '$.topic',
            tag_id BIGINT PATH '$.tag'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_community_topic_tag existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.topic_id <=> expected.topic_id)
              OR NOT (existing.tag_id <=> expected.tag_id)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_community_topic_tag existing
        WHERE existing.topic_id = expected.topic_id
          AND existing.tag_id = expected.tag_id
          AND existing.id <> expected.id
    )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990010000000000001","canonical":"深测科技","alias":"深测科技"},{"id":"990010000000000002","canonical":"深测科技","alias":"深测"},{"id":"990010000000000003","canonical":"深测科技","alias":"深测科技有限公司"},{"id":"990010000000000004","canonical":"字节跳动","alias":"字节跳动"},{"id":"990010000000000005","canonical":"字节跳动","alias":"ByteDance"},{"id":"990010000000000006","canonical":"阿里巴巴","alias":"阿里巴巴"},{"id":"990010000000000007","canonical":"阿里巴巴","alias":"阿里"},{"id":"990010000000000008","canonical":"美团","alias":"美团"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            canonical_company VARCHAR(128) PATH '$.canonical',
            alias VARCHAR(128) PATH '$.alias'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_company_alias existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.alias <=> expected.alias)
              OR NOT (existing.canonical_company <=> expected.canonical_company)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_company_alias existing
        WHERE existing.alias = expected.alias
          AND existing.id <> expected.id
    )
);

SET @offerlab_demo_seed_asset_identity_conflicts := @offerlab_demo_seed_asset_identity_conflicts + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990100000000000001","title":"深测科技 Java 后端一面复盘：缓存、事务和慢 SQL"},{"id":"990100000000000002","title":"深测科技 二面：Kafka 削峰和分布式排查"},{"id":"990100000000000003","title":"深测科技 HR 前技术加面：JVM、索引和项目亮点"},{"id":"990100000000000004","title":"字节跳动 Java 后端二面复盘：高并发接口、限流和降级"},{"id":"990100000000000005","title":"美团后端工程师面经：MySQL 索引、订单一致性和压测复盘"},{"id":"990100000000000006","title":"阿里巴巴 Java 后端终面准备：项目稳定性、消息链路和 STAR 表达"},{"id":"991100000000000001","title":"数字生活应急包清单：备份、密码与双重验证"},{"id":"991100000000000002","title":"开源软件要不要默认收集遥测数据"},{"id":"991100000000000003","title":"把旧电脑改成家庭资料站的一次实践"},{"id":"991100000000000004","title":"从全职工作到自由职业三个月的真实账本"},{"id":"991100000000000005","title":"职业空窗期如何向家人和招聘方解释"},{"id":"991100000000000006","title":"第一次带跨职能项目失败后的复盘"},{"id":"991100000000000007","title":"建立家庭应急金前的六项检查"},{"id":"991100000000000008","title":"指数基金定投前先确认哪些风险"},{"id":"991100000000000009","title":"租房还是买房，先讨论现金流和生活选择"},{"id":"991100000000000010","title":"我的晨间一小时学习系统运行了半年"},{"id":"991100000000000011","title":"读一本非虚构书的三层笔记模板"},{"id":"991100000000000012","title":"连续学习计划中断后的复盘"},{"id":"991100000000000013","title":"合租公共空间怎么制定不伤人的规则"},{"id":"991100000000000014","title":"要不要搬去离公司更远但更舒适的房子"},{"id":"991100000000000015","title":"周末无屏幕半天带来的生活观察"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            title VARCHAR(255) PATH '$.title'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_post_main existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.title <=> expected.title)
              OR NOT (existing.author_id <=> @offerlab_demo_user_uid)
          )
    )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990200000000000001","post":"990100000000000001"},{"id":"990200000000000002","post":"990100000000000001"},{"id":"990200000000000003","post":"990100000000000001"},{"id":"990200000000000004","post":"990100000000000002"},{"id":"990200000000000005","post":"990100000000000002"},{"id":"990200000000000006","post":"990100000000000003"},{"id":"990200000000000007","post":"990100000000000003"},{"id":"990200000000000008","post":"990100000000000003"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            source_post_id BIGINT PATH '$.post'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_interview_question existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.normalized_hash <=> SHA2(CONCAT('offerlab-demo-question-', expected.id), 256))
              OR NOT (existing.source_post_id <=> expected.source_post_id)
              OR NOT (existing.source_author_uid <=> @offerlab_demo_user_uid)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_interview_question existing
        WHERE existing.source_post_id = expected.source_post_id
          AND existing.normalized_hash = SHA2(CONCAT('offerlab-demo-question-', expected.id), 256)
          AND existing.id <> expected.id
    )
);

SET @offerlab_demo_seed_asset_identity_conflicts := @offerlab_demo_seed_asset_identity_conflicts + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990300000000000001","type":"company","value":"深测科技"},{"id":"990300000000000002","type":"position","value":"Java 后端"},{"id":"990300000000000003","type":"tag","value":"Kafka"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            target_type VARCHAR(16) PATH '$.type',
            target_value VARCHAR(128) PATH '$.value'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_user_prep_target existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.uid <=> @offerlab_demo_user_uid)
              OR NOT (existing.target_type <=> expected.target_type)
              OR NOT (existing.target_value <=> expected.target_value)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_user_prep_target existing
        WHERE existing.uid = @offerlab_demo_user_uid
          AND existing.target_type = expected.target_type
          AND existing.target_value = expected.target_value
          AND existing.id <> expected.id
    )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990310000000000001","question":"990200000000000001"},{"id":"990310000000000002","question":"990200000000000002"},{"id":"990310000000000003","question":"990200000000000003"},{"id":"990310000000000004","question":"990200000000000004"},{"id":"990310000000000005","question":"990200000000000005"},{"id":"990310000000000006","question":"990200000000000006"},{"id":"990310000000000007","question":"990200000000000007"},{"id":"990310000000000008","question":"990200000000000008"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            question_id BIGINT PATH '$.question'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_user_question_progress existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.uid <=> @offerlab_demo_user_uid)
              OR NOT (existing.question_id <=> expected.question_id)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_user_question_progress existing
        WHERE existing.uid = @offerlab_demo_user_uid
          AND existing.question_id = expected.question_id
          AND existing.id <> expected.id
    )
) + (
    SELECT COUNT(*)
    FROM t_mock_interview_session existing
    WHERE existing.id = 990400000000000001
      AND (
          NOT (existing.uid <=> @offerlab_demo_user_uid)
          OR NOT (existing.company <=> '深测科技')
          OR NOT (existing.position <=> 'Java 后端')
      )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990410000000000001","question":"990200000000000002"},{"id":"990410000000000002","question":"990200000000000004"},{"id":"990410000000000003","question":"990200000000000008"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            question_id BIGINT PATH '$.question'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_mock_interview_answer existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.uid <=> @offerlab_demo_user_uid)
              OR NOT (existing.session_id <=> 990400000000000001)
              OR NOT (existing.question_id <=> expected.question_id)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_mock_interview_answer existing
        WHERE existing.session_id = 990400000000000001
          AND existing.question_id = expected.question_id
          AND existing.id <> expected.id
    )
);

SET @offerlab_demo_seed_asset_identity_conflicts := @offerlab_demo_seed_asset_identity_conflicts + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990110000000000001","post":"990100000000000001","tag":"1001"},{"id":"990110000000000002","post":"990100000000000001","tag":"1004"},{"id":"990110000000000003","post":"990100000000000001","tag":"1005"},{"id":"990110000000000004","post":"990100000000000001","tag":"1006"},{"id":"990110000000000005","post":"990100000000000001","tag":"2007"},{"id":"990110000000000006","post":"990100000000000001","tag":"3001"},{"id":"990110000000000007","post":"990100000000000002","tag":"1007"},{"id":"990110000000000008","post":"990100000000000002","tag":"1006"},{"id":"990110000000000009","post":"990100000000000002","tag":"2007"},{"id":"990110000000000010","post":"990100000000000002","tag":"3005"},{"id":"990110000000000011","post":"990100000000000003","tag":"1010"},{"id":"990110000000000012","post":"990100000000000003","tag":"1005"},{"id":"990110000000000013","post":"990100000000000003","tag":"2007"},{"id":"990110000000000014","post":"990100000000000003","tag":"3001"},{"id":"990110000000000015","post":"990100000000000003","tag":"1001"},{"id":"990110000000000016","post":"990100000000000004","tag":"1001"},{"id":"990110000000000017","post":"990100000000000004","tag":"1004"},{"id":"990110000000000018","post":"990100000000000004","tag":"1006"},{"id":"990110000000000019","post":"990100000000000004","tag":"1007"},{"id":"990110000000000020","post":"990100000000000004","tag":"2001"},{"id":"990110000000000021","post":"990100000000000004","tag":"3001"},{"id":"990110000000000022","post":"990100000000000005","tag":"1001"},{"id":"990110000000000023","post":"990100000000000005","tag":"1005"},{"id":"990110000000000024","post":"990100000000000005","tag":"1006"},{"id":"990110000000000025","post":"990100000000000005","tag":"2004"},{"id":"990110000000000026","post":"990100000000000005","tag":"3005"},{"id":"990110000000000027","post":"990100000000000006","tag":"1001"},{"id":"990110000000000028","post":"990100000000000006","tag":"1004"},{"id":"990110000000000029","post":"990100000000000006","tag":"1007"},{"id":"990110000000000030","post":"990100000000000006","tag":"1010"},{"id":"990110000000000031","post":"990100000000000006","tag":"2002"},{"id":"990110000000000032","post":"990100000000000006","tag":"3001"},{"id":"991110000000000001","post":"991100000000000001","tag":"991000000000000001"},{"id":"991110000000000002","post":"991100000000000001","tag":"991000000000000002"},{"id":"991110000000000003","post":"991100000000000002","tag":"991000000000000001"},{"id":"991110000000000004","post":"991100000000000002","tag":"991000000000000002"},{"id":"991110000000000005","post":"991100000000000003","tag":"991000000000000001"},{"id":"991110000000000006","post":"991100000000000003","tag":"991000000000000002"},{"id":"991110000000000007","post":"991100000000000004","tag":"991000000000000003"},{"id":"991110000000000008","post":"991100000000000004","tag":"991000000000000004"},{"id":"991110000000000009","post":"991100000000000005","tag":"991000000000000003"},{"id":"991110000000000010","post":"991100000000000005","tag":"991000000000000004"},{"id":"991110000000000011","post":"991100000000000006","tag":"991000000000000003"},{"id":"991110000000000012","post":"991100000000000006","tag":"991000000000000004"},{"id":"991110000000000013","post":"991100000000000007","tag":"991000000000000005"},{"id":"991110000000000014","post":"991100000000000007","tag":"991000000000000006"},{"id":"991110000000000015","post":"991100000000000008","tag":"991000000000000005"},{"id":"991110000000000016","post":"991100000000000008","tag":"991000000000000006"},{"id":"991110000000000017","post":"991100000000000009","tag":"991000000000000005"},{"id":"991110000000000018","post":"991100000000000009","tag":"991000000000000006"},{"id":"991110000000000019","post":"991100000000000010","tag":"991000000000000007"},{"id":"991110000000000020","post":"991100000000000010","tag":"991000000000000008"},{"id":"991110000000000021","post":"991100000000000011","tag":"991000000000000007"},{"id":"991110000000000022","post":"991100000000000011","tag":"991000000000000008"},{"id":"991110000000000023","post":"991100000000000012","tag":"991000000000000007"},{"id":"991110000000000024","post":"991100000000000012","tag":"991000000000000008"},{"id":"991110000000000025","post":"991100000000000013","tag":"991000000000000009"},{"id":"991110000000000026","post":"991100000000000013","tag":"991000000000000010"},{"id":"991110000000000027","post":"991100000000000014","tag":"991000000000000009"},{"id":"991110000000000028","post":"991100000000000014","tag":"991000000000000010"},{"id":"991110000000000029","post":"991100000000000015","tag":"991000000000000009"},{"id":"991110000000000030","post":"991100000000000015","tag":"991000000000000010"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            post_id BIGINT PATH '$.post',
            tag_id BIGINT PATH '$.tag'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_post_tag_ref existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.post_id <=> expected.post_id)
              OR NOT (existing.tag_id <=> expected.tag_id)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_post_tag_ref existing
        WHERE existing.post_id = expected.post_id
          AND existing.tag_id = expected.tag_id
          AND existing.id <> expected.id
    )
) + (
    SELECT COUNT(*)
    FROM JSON_TABLE(
        '[{"id":"990210000000000001","question":"990200000000000001","tag":"1001"},{"id":"990210000000000002","question":"990200000000000001","tag":"1004"},{"id":"990210000000000003","question":"990200000000000002","tag":"1006"},{"id":"990210000000000004","question":"990200000000000002","tag":"1001"},{"id":"990210000000000005","question":"990200000000000003","tag":"1005"},{"id":"990210000000000006","question":"990200000000000003","tag":"1001"},{"id":"990210000000000007","question":"990200000000000004","tag":"1007"},{"id":"990210000000000008","question":"990200000000000004","tag":"1001"},{"id":"990210000000000009","question":"990200000000000005","tag":"1004"},{"id":"990210000000000010","question":"990200000000000005","tag":"1006"},{"id":"990210000000000011","question":"990200000000000006","tag":"1010"},{"id":"990210000000000012","question":"990200000000000006","tag":"1001"},{"id":"990210000000000013","question":"990200000000000007","tag":"1008"},{"id":"990210000000000014","question":"990200000000000007","tag":"1001"},{"id":"990210000000000015","question":"990200000000000008","tag":"3001"},{"id":"990210000000000016","question":"990200000000000008","tag":"2007"}]',
        '$[*]' COLUMNS (
            id BIGINT PATH '$.id',
            question_id BIGINT PATH '$.question',
            tag_id BIGINT PATH '$.tag'
        )
    ) expected
    WHERE EXISTS (
        SELECT 1
        FROM t_interview_question_tag existing
        WHERE existing.id = expected.id
          AND (
              NOT (existing.question_id <=> expected.question_id)
              OR NOT (existing.tag_id <=> expected.tag_id)
          )
    )
       OR EXISTS (
        SELECT 1
        FROM t_interview_question_tag existing
        WHERE existing.question_id = expected.question_id
          AND existing.tag_id = expected.tag_id
          AND existing.id <> expected.id
    )
);

INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('demo_asset_identity', @offerlab_demo_seed_asset_identity_conflicts);

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
    (2001, '字节跳动', 2, 1, 1),
    (2002, '阿里巴巴', 2, 1, 1),
    (2003, '腾讯', 2, 0, 1),
    (2004, '美团', 2, 1, 1),
    (2005, '小红书', 2, 0, 1),
    (2006, '百度', 2, 0, 1),
    (2007, '深测科技', 2, 3, 1),
    (3001, 'Java 后端', 3, 4, 1),
    (3002, 'Go 后端', 3, 0, 1),
    (3003, '前端', 3, 0, 1),
    (3004, '算法工程师', 3, 0, 1),
    (3005, '后端工程师', 3, 2, 1)
ON DUPLICATE KEY UPDATE
    tag_name = VALUES(tag_name),
    tag_type = VALUES(tag_type),
    use_count = GREATEST(use_count, VALUES(use_count)),
    is_official = VALUES(is_official),
    tag_status = 1,
    merge_target_id = NULL,
    is_deleted = 0;

INSERT INTO t_community_topic (
    id, slug, topic_name, description, topic_type, cover_url,
    sort_order, featured, topic_status, created_by, updated_by
) VALUES
    (990500000000000001, 'java-backend-roadmap', 'Java 后端成长路线',
     '串联 Spring、MySQL、Redis、Kafka 和 JVM 高频面试复盘，适合本地演示专题聚合与关注。',
     'tech_stack', NULL, 100, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000002, 'redis-cache-consistency', 'Redis 缓存一致性',
     '聚合缓存穿透、击穿、双写一致性、热点重建和降级补偿相关帖子。',
     'scenario', NULL, 90, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000003, 'kafka-reliability', 'Kafka 稳定性治理',
     '覆盖消息幂等、Outbox、堆积排查、重试死信和消费者延迟观测。',
     'scenario', NULL, 80, 1, 1, 990000000000000001, 990000000000000001),
    (990500000000000004, 'elasticsearch-search-index', '搜索与索引诊断',
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

-- Demo content author. This account is disabled by default and is not granted
-- admin permissions. If an existing local database has explicitly promoted
-- this deterministic uid to an enabled admin, preserve that login identity
-- instead of silently replacing its credentials during an idempotent refresh.
-- Use db/local/seed_local_demo_admin.sql explicitly to promote this identity
-- for a fresh local-only admin demo.
INSERT INTO t_user_account
    (id, email, password_hash, password_salt, account_status)
VALUES
    (990000000000000001, 'demo.author@offerlab.local', '$2a$10$0CN4aiMIujTsf.AmOBUj8OAO.IrhNxcI5Toug4dnCegzpvuYHYmcG', '', 2)
ON DUPLICATE KEY UPDATE
    email = IF(@offerlab_preserve_existing_local_admin = 1, email, VALUES(email)),
    password_hash = IF(@offerlab_preserve_existing_local_admin = 1, password_hash, VALUES(password_hash)),
    password_salt = IF(@offerlab_preserve_existing_local_admin = 1, password_salt, VALUES(password_salt)),
    account_status = IF(@offerlab_preserve_existing_local_admin = 1, account_status, VALUES(account_status)),
    update_time = IF(@offerlab_preserve_existing_local_admin = 1, update_time, CURRENT_TIMESTAMP(3));

INSERT INTO t_user_profile
    (id, nickname, avatar_url, bio, intent_json)
VALUES
    (
        990000000000000001,
        'OfferLab 演示管理员',
        NULL,
        '正在准备深测科技 Java 后端面试，已整理题单、笔记和 STAR 草稿。',
        JSON_OBJECT(
            'targetCompanies', JSON_ARRAY('深测科技'),
            'targetPositions', JSON_ARRAY('Java 后端', '后端工程师'),
            'targetCity', '上海'
        )
    )
ON DUPLICATE KEY UPDATE
    nickname = IF(@offerlab_preserve_existing_local_admin = 1, nickname, VALUES(nickname)),
    avatar_url = IF(@offerlab_preserve_existing_local_admin = 1, avatar_url, VALUES(avatar_url)),
    bio = IF(@offerlab_preserve_existing_local_admin = 1, bio, VALUES(bio)),
    intent_json = IF(@offerlab_preserve_existing_local_admin = 1, intent_json, VALUES(intent_json)),
    update_time = IF(@offerlab_preserve_existing_local_admin = 1, update_time, CURRENT_TIMESTAMP(3));

INSERT INTO t_user_counter
    (user_id, follower_count, following_count, post_count, like_received)
VALUES
    (990000000000000001, 18, 7, 6, 196)
ON DUPLICATE KEY UPDATE
    follower_count = IF(@offerlab_preserve_existing_local_admin = 1, follower_count, VALUES(follower_count)),
    following_count = IF(@offerlab_preserve_existing_local_admin = 1, following_count, VALUES(following_count)),
    post_count = IF(@offerlab_preserve_existing_local_admin = 1, post_count, VALUES(post_count)),
    like_received = IF(@offerlab_preserve_existing_local_admin = 1, like_received, VALUES(like_received)),
    update_time = IF(@offerlab_preserve_existing_local_admin = 1, update_time, CURRENT_TIMESTAMP(3));

INSERT INTO t_company_alias
    (id, canonical_company, alias, status)
VALUES
    (990010000000000001, '深测科技', '深测科技', 1),
    (990010000000000002, '深测科技', '深测', 1),
    (990010000000000003, '深测科技', '深测科技有限公司', 1),
    (990010000000000004, '字节跳动', '字节跳动', 1),
    (990010000000000005, '字节跳动', 'ByteDance', 1),
    (990010000000000006, '阿里巴巴', '阿里巴巴', 1),
    (990010000000000007, '阿里巴巴', '阿里', 1),
    (990010000000000008, '美团', '美团', 1)
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
        '深测科技 Java 后端一面复盘：缓存、事务和慢 SQL',
        '一面重点围绕 Spring 事务传播、Redis 缓存一致性、MySQL 慢 SQL 排查和项目中的降级设计。面试官会追问为什么这么设计，以及线上指标如何验证。',
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
        990000000000000001,
        1,
        '深测科技 HR 前技术加面：JVM、索引和项目亮点',
        '这一轮题目覆盖 JVM 内存模型、索引选择性、接口压测结果复盘，以及如何把项目亮点和岗位要求连接起来。',
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
        '字节跳动 Java 后端二面复盘：高并发接口、限流和降级',
        '二面重点讨论活动页高并发接口设计，包含热点缓存预热、令牌桶限流、Redis 兜底、Spring 事务边界和 Kafka 异步削峰。面试官要求说明每个方案的取舍、监控指标和故障恢复流程。',
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
        '美团后端工程师面经：MySQL 索引、订单一致性和压测复盘',
        '这一轮围绕订单链路展开，重点问到 MySQL 组合索引设计、Redis 缓存穿透、库存一致性、接口压测指标以及如何把一次性能优化讲成可复盘的项目成果。',
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
    (990100000000000001, 1, JSON_OBJECT('company', '深测科技', 'position', 'Java 后端', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000002, 1, JSON_OBJECT('company', '深测科技', 'position', '后端工程师', 'yearsOfExp', 4, 'interviewResult', 2)),
    (990100000000000003, 1, JSON_OBJECT('company', '深测科技', 'position', 'Java 后端', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000004, 1, JSON_OBJECT('company', '字节跳动', 'position', 'Java 后端', 'yearsOfExp', 3, 'interviewResult', 1)),
    (990100000000000005, 1, JSON_OBJECT('company', '美团', 'position', '后端工程师', 'yearsOfExp', 4, 'interviewResult', 1)),
    (990100000000000006, 1, JSON_OBJECT('company', '阿里巴巴', 'position', 'Java 后端', 'yearsOfExp', 5, 'interviewResult', 2))
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
        'Spring 事务在同类方法内部调用时为什么可能不生效？你在项目里怎么规避？',
        SHA2('offerlab-demo-question-990200000000000001', 256),
        '从 AOP 代理、传播行为、异常回滚规则、事务边界设计四点回答。',
        'Spring 事务代理与工程落地',
        '同类内部调用绕过代理时，@Transactional 不会被拦截。常见处理是拆分到独立 Bean、通过代理对象调用，或把事务边界上移到应用服务层，同时明确 rollbackFor 和传播行为。',
        '面试官追问了为什么 private 方法和 self-invocation 事务不生效。',
        '覆盖高频 Spring 事务陷阱，并要求结合项目经验说明规避方案。',
        '深测科技',
        'Java 后端',
        '一面',
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
        'Redis 缓存和数据库双写不一致时，你会如何设计更新策略？',
        SHA2('offerlab-demo-question-990200000000000002', 256),
        '先说旁路缓存，再比较先删缓存、延迟双删、消息补偿和读写锁。',
        '缓存一致性与降级',
        '常用方案是更新数据库后删除缓存，并通过重试队列或 outbox 补偿删除失败；对热点 key 可加互斥重建、逻辑过期或短 TTL，避免把双写顺序说成绝对正确。',
        '候选人需要解释缓存击穿和删除失败时的兜底机制。',
        '同时考察 Redis 基础、异常补偿和线上稳定性意识。',
        '深测科技',
        'Java 后端',
        '一面',
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
        'MySQL 慢查询从发现到修复，你的排查步骤是什么？',
        SHA2('offerlab-demo-question-990200000000000003', 256),
        '慢日志、EXPLAIN、索引选择性、回表、排序临时表、分页优化。',
        'MySQL 索引与性能诊断',
        '先用监控和慢日志定位 SQL，再看 EXPLAIN 的 type、key、rows、Extra；结合业务基数判断索引顺序，必要时改写查询、避免大 offset、拆分宽字段或补充覆盖索引。',
        '面试官给了一个 user_id + status + create_time 的组合查询。',
        '有明确诊断路径和可复盘指标，适合准备公司页展示。',
        '深测科技',
        'Java 后端',
        '一面',
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
        'Kafka 消费端如何保证幂等？如果出现消息堆积你会先看哪些指标？',
        SHA2('offerlab-demo-question-990200000000000004', 256),
        '幂等键、唯一约束、消费位点、重试死信、lag、消费耗时、分区数。',
        '消息队列稳定性',
        '幂等通常依赖业务唯一键、去重表、状态机或数据库唯一约束；堆积时先看 consumer lag、单条处理耗时、失败重试比例、分区倾斜、下游依赖耗时，再决定扩容或降级。',
        '二面把消息堆积和线上故障恢复连起来问。',
        '能支撑模拟面试和 STAR 故障复盘。',
        '深测科技',
        '后端工程师',
        '二面',
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
        '接口超时率突然升高，你会如何定位是应用、数据库还是外部依赖问题？',
        SHA2('offerlab-demo-question-990200000000000005', 256),
        '按入口指标、链路追踪、线程池、连接池、SQL、下游依赖逐层缩小范围。',
        '线上问题定位',
        '先确认错误率、P95/P99、实例分布和变更窗口，再用 trace 拆分耗时；同时检查线程池队列、连接池等待、慢 SQL、GC 和下游超时，最后用限流降级恢复服务。',
        '要求讲清楚先恢复再定位的优先级。',
        '贴近后端岗位真实场景，适合 /me/prep 复习计划。',
        '深测科技',
        '后端工程师',
        '二面',
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
        'JVM 老年代持续上涨但 Full GC 后下降不明显，你会怎么分析？',
        SHA2('offerlab-demo-question-990200000000000006', 256),
        '区分内存泄漏、缓存膨胀、大对象、类加载，结合 dump 和 GC 日志。',
        'JVM 内存排查',
        '先看 GC 日志和监控确认趋势，再抓 heap dump 对比对象引用链；常见原因包括本地缓存无上限、静态集合持有、线程池任务堆积、大对象或类加载泄漏。',
        '技术加面要求说明 MAT dominator tree 怎么看。',
        '高频 JVM 排障题，能丰富公司准备包的难题列表。',
        '深测科技',
        'Java 后端',
        '技术加面',
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
        '如何设计一个支持高并发查询的面试题搜索接口？',
        SHA2('offerlab-demo-question-990200000000000007', 256),
        '关键词召回、筛选条件、分页游标、缓存、ES 降级和索引重建。',
        '搜索与接口设计',
        '搜索接口应区分召回和详情读取，使用 ES 或倒排索引承接关键词，数据库兜底需要限制分页深度；热门条件可缓存，索引更新要有重试和可观测任务状态。',
        '候选人被要求补充索引失败后的补偿方案。',
        '与 OfferLab 自身题库场景贴合，方便演示搜索和高亮。',
        '深测科技',
        'Java 后端',
        '技术加面',
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
        '项目经历里你如何用 STAR 讲清一次稳定性优化？',
        SHA2('offerlab-demo-question-990200000000000008', 256),
        'Situation、Task、Action、Result，每一步都要有指标和个人贡献。',
        '项目表达与 STAR',
        '先描述业务场景和故障影响，再说明自己的任务边界；行动部分讲监控、限流、缓存、SQL 优化等关键取舍，结果要落到延迟、错误率、成本或人效指标。',
        'HR 前技术加面会把技术方案和表达能力一起考察。',
        '补齐项目表达维度，避免题库只有技术知识点。',
        '深测科技',
        'Java 后端',
        '技术加面',
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
    (990300000000000001, @offerlab_demo_user_uid, 'company', '深测科技', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'urgent', '优先刷深测科技高频后端题，准备一面到加面的完整链路。'),
    (990300000000000002, @offerlab_demo_user_uid, 'position', 'Java 后端', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'high', '围绕 Spring、MySQL、Redis、Kafka、JVM 做专项复盘。'),
    (990300000000000003, @offerlab_demo_user_uid, 'tag', 'Kafka', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'medium', '补齐消息堆积、幂等和重试死信案例。')
ON DUPLICATE KEY UPDATE
    uid = VALUES(uid),
    interview_date = VALUES(interview_date),
    priority = VALUES(priority),
    note = VALUES(note);

INSERT INTO t_user_question_progress
    (id, uid, question_id, progress_status, favorite, note, mistake_reason, answer_draft, star_story,
     next_review_at, last_reviewed_at, review_count, review_interval_days, create_time, update_time)
VALUES
    (
        990310000000000001,
        @offerlab_demo_user_uid,
        990200000000000001,
        'mastered',
        1,
        '同类方法调用不走代理，要主动说出事务边界为什么放在 service 编排层。',
        NULL,
        '我会先解释 Spring AOP 代理机制，再结合订单结算项目说明如何把事务入口放到应用服务层，并补充 rollbackFor 和传播行为。',
        'S: 订单结算存在部分写入风险；T: 梳理事务边界；A: 拆分库存、订单、流水写入并统一由应用服务编排；R: 回滚路径清晰，压测无脏数据。',
        DATE_ADD(NOW(3), INTERVAL 10 DAY),
        NOW(3) - INTERVAL 1 DAY,
        3,
        10,
        NOW(3) - INTERVAL 8 DAY,
        NOW(3) - INTERVAL 1 DAY
    ),
    (
        990310000000000002,
        @offerlab_demo_user_uid,
        990200000000000002,
        'review',
        1,
        '缓存一致性不能只背延迟双删，要讲删除失败补偿和热点重建。',
        'concept',
        '旁路缓存下先更新 DB 再删缓存；删除失败进入重试队列，热点 key 用逻辑过期和互斥锁重建。',
        'S: 活动页库存缓存偶发脏读；T: 降低不一致窗口；A: DB 成功后删缓存，失败写入重试任务，热点 key 加短 TTL；R: 投诉下降，缓存命中保持稳定。',
        NOW(3) - INTERVAL 2 HOUR,
        NOW(3) - INTERVAL 2 DAY,
        2,
        3,
        NOW(3) - INTERVAL 7 DAY,
        NOW(3) - INTERVAL 2 HOUR
    ),
    (
        990310000000000003,
        @offerlab_demo_user_uid,
        990200000000000003,
        'learning',
        0,
        'EXPLAIN 字段要讲 type/key/rows/Extra，再落到索引顺序。',
        'expression',
        '慢 SQL 排查会先用慢日志定位，再看 EXPLAIN 和数据分布，最后用组合索引、覆盖索引或分页改写验证效果。',
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
        @offerlab_demo_user_uid,
        990200000000000004,
        'review',
        1,
        'Kafka 堆积要按 lag、消费耗时、失败重试、分区倾斜和下游耗时拆。',
        'project',
        '幂等用业务唯一键和数据库唯一约束；堆积先确认 lag 和处理耗时，再判断扩容、限流还是下游降级。',
        'S: 促销消息堆积导致履约延迟；T: 恢复消费并避免重复扣减；A: 增加幂等表、调整批量提交、隔离慢下游；R: lag 在 20 分钟内恢复到安全水位。',
        NOW(3) - INTERVAL 1 HOUR,
        NOW(3) - INTERVAL 2 DAY,
        2,
        3,
        NOW(3) - INTERVAL 5 DAY,
        NOW(3) - INTERVAL 1 HOUR
    ),
    (
        990310000000000005,
        @offerlab_demo_user_uid,
        990200000000000005,
        'todo',
        0,
        '需要补一段链路追踪定位外部依赖超时的真实案例。',
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
        @offerlab_demo_user_uid,
        990200000000000006,
        'learning',
        1,
        'JVM 题要把 dump、GC 日志和对象引用链连起来。',
        'concept',
        '我会先看 Full GC 后老年代回收比例，再抓 heap dump，用 MAT 看 dominator tree 和 GC Roots，确认是否是缓存或静态集合持有。',
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
        @offerlab_demo_user_uid,
        990200000000000007,
        'todo',
        0,
        '准备把 OfferLab 题库搜索作为项目案例来讲。',
        NULL,
        '搜索接口会用 ES 做关键词召回，DB 查询详情；索引失败进入重试任务，前端展示任务状态和降级结果。',
        'S: 题库增长后搜索延迟升高；T: 保持搜索体验；A: 引入索引重建和失败重试；R: 热门关键词响应稳定在百毫秒级。',
        NULL,
        NULL,
        0,
        1,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3)
    ),
    (
        990310000000000008,
        @offerlab_demo_user_uid,
        990200000000000008,
        'review',
        1,
        'STAR 表达要量化结果，不要只说做了优化。',
        'expression',
        '我会用场景、任务、行动、结果四段回答，并把结果落到错误率、P99 延迟和排查效率。',
        'S: 核心接口在活动期间超时；T: 我负责定位并降低超时；A: 加 tracing、优化慢 SQL、热点缓存预热；R: P99 从 1.8s 降到 420ms，错误率低于 0.1%。',
        NOW(3) - INTERVAL 30 MINUTE,
        NOW(3) - INTERVAL 1 DAY,
        1,
        2,
        NOW(3) - INTERVAL 2 DAY,
        NOW(3) - INTERVAL 30 MINUTE
    )
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
    (990400000000000001, @offerlab_demo_user_uid, '深测科技', 'Java 后端', 'medium', 'Kafka', 3, 3, 12, 1280, 'completed', NOW(3) - INTERVAL 1 DAY, NOW(3) - INTERVAL 1 HOUR)
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
     company_snapshot, position_snapshot, round_snapshot, difficulty_snapshot, answer_text, self_review,
     score, ai_reviewed, ai_review_status, ai_score, ai_completeness, ai_project_expression, ai_follow_up_suggestion)
VALUES
    (
        990410000000000001,
        990400000000000001,
        @offerlab_demo_user_uid,
        990200000000000002,
        1,
        'Redis 缓存和数据库双写不一致时，你会如何设计更新策略？',
        '旁路缓存、删除失败补偿和热点重建。',
        '深测科技',
        'Java 后端',
        '一面',
        'medium',
        '先更新数据库再删除缓存，删除失败写入重试任务；热点 key 使用逻辑过期和互斥重建。',
        '还需要补充为什么不直接先写缓存。',
        4,
        1,
        'SUCCEEDED',
        82,
        '覆盖了主流程和补偿，但可以补充并发读写窗口。',
        '项目案例有雏形，需要增加量化指标。',
        '补一个删除失败后的监控告警指标。'
    ),
    (
        990410000000000002,
        990400000000000001,
        @offerlab_demo_user_uid,
        990200000000000004,
        2,
        'Kafka 消费端如何保证幂等？如果出现消息堆积你会先看哪些指标？',
        '幂等键、唯一约束、lag、分区倾斜。',
        '深测科技',
        '后端工程师',
        '二面',
        'hard',
        '用业务唯一键和去重表保证幂等；堆积先看 lag、消费耗时、失败重试比例、分区倾斜和下游耗时。',
        '回答完整，但 STAR 案例可以更紧。',
        4,
        1,
        'SUCCEEDED',
        80,
        '关键指标齐全。',
        '需要把故障恢复过程压缩成 60 秒版本。',
        '准备一句明确的最终结果指标。'
    ),
    (
        990410000000000003,
        990400000000000001,
        @offerlab_demo_user_uid,
        990200000000000008,
        3,
        '项目经历里你如何用 STAR 讲清一次稳定性优化？',
        'STAR 四段和量化结果。',
        '深测科技',
        'Java 后端',
        '技术加面',
        'easy',
        '我会用场景、任务、行动、结果四段讲，把 P99、错误率和恢复时间作为结果指标。',
        '表达更顺了，下一版要加个人贡献边界。',
        4,
        1,
        'SUCCEEDED',
        85,
        '结构清晰。',
        '结果量化较好，个人贡献还可更突出。',
        '补充自己负责的模块和协作边界。'
    )
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
    score = VALUES(score),
    ai_reviewed = VALUES(ai_reviewed),
    ai_review_status = VALUES(ai_review_status),
    ai_score = VALUES(ai_score),
    ai_completeness = VALUES(ai_completeness),
    ai_project_expression = VALUES(ai_project_expression),
    ai_follow_up_suggestion = VALUES(ai_follow_up_suggestion);

-- BEGIN GENERATED FROM db/migration/20260712_demo_community_seed.sql
-- 20260712_demo_community_seed.sql
-- Non-destructive comprehensive community seed for existing demo databases.
SET NAMES utf8mb4;

-- Keep community demo content on the same reserved identity. In particular,
-- never fall back to a real admin or the first active account when the local
-- demo identity has been explicitly promoted to demo.admin.
SET @community_demo_uid := @offerlab_demo_user_uid;

SET @community_seed_identity_conflicts := IF(
    @community_demo_uid IS NULL OR NOT EXISTS (
        SELECT 1
        FROM t_user_account
        WHERE id = @community_demo_uid
          AND is_deleted = 0
    ),
    1,
    0
);
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_demo_author', @community_seed_identity_conflicts);

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
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_tag_identity', @community_seed_identity_conflicts);

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
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_topic_identity', @community_seed_identity_conflicts);

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
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_topic_tag_identity', @community_seed_identity_conflicts);

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
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_post_identity', @community_seed_identity_conflicts);

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
INSERT INTO offerlab_demo_seed_assertion (assertion_name, conflict_count)
VALUES ('community_post_tag_identity', @community_seed_identity_conflicts);

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
DROP TEMPORARY TABLE offerlab_demo_seed_assertion;
-- END GENERATED FROM db/migration/20260712_demo_community_seed.sql

-- Restoring 1 commits the fresh-init transaction. Restoring 0 leaves the
-- existing-database refresh transaction open for its own postcondition guard.
SET SESSION autocommit = @offerlab_demo_seed_original_autocommit;
SET @offerlab_demo_seed_original_autocommit := NULL;
