SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_domain_config (
    domain              TINYINT      NOT NULL PRIMARY KEY COMMENT '1 tech, 2 career, 3 reading, 4 lifestyle, 5 investment',
    domain_name         VARCHAR(64)  NOT NULL,
    domain_slug         VARCHAR(32)  NOT NULL,
    description         VARCHAR(500) NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    enabled             TINYINT      NOT NULL DEFAULT 1,
    risk_level          VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    posting_notice      VARCHAR(500) NULL,
    browse_notice       VARCHAR(500) NULL,
    interaction_notice  VARCHAR(500) NULL,
    created_by          BIGINT       NULL,
    updated_by          BIGINT       NULL,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_domain_config_slug (domain_slug),
    KEY idx_domain_config_enabled_sort (enabled, sort_order, domain),
    KEY idx_domain_config_risk_enabled (risk_level, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community domain configuration';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260623_domain_config_add_column_if_missing $$
CREATE PROCEDURE v20260623_domain_config_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260623_domain_config_add_index_if_missing $$
CREATE PROCEDURE v20260623_domain_config_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260623_domain_config_add_primary_if_missing $$
CREATE PROCEDURE v20260623_domain_config_add_primary_if_missing()
BEGIN
    DECLARE v_primary_key_count INT DEFAULT 0;
    DECLARE v_null_domain_count INT DEFAULT 0;
    DECLARE v_duplicate_domain_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_primary_key_count
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_domain_config'
       AND INDEX_NAME = 'PRIMARY'
       AND COLUMN_NAME = 'domain';

    SELECT COUNT(*)
      INTO v_null_domain_count
      FROM t_domain_config
     WHERE domain IS NULL;

    SELECT COUNT(*)
      INTO v_duplicate_domain_count
      FROM (
        SELECT domain
        FROM t_domain_config
        WHERE domain IS NOT NULL
        GROUP BY domain
        HAVING COUNT(*) > 1
      ) duplicated_domains;

    IF v_primary_key_count = 0
       AND v_null_domain_count = 0
       AND v_duplicate_domain_count = 0 THEN
        ALTER TABLE t_domain_config ADD PRIMARY KEY (domain);
    END IF;
END $$

DELIMITER ;

CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'domain',
    'ALTER TABLE t_domain_config ADD COLUMN domain TINYINT NOT NULL COMMENT ''1 tech, 2 career, 3 reading, 4 lifestyle, 5 investment'' FIRST');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'domain_name',
    'ALTER TABLE t_domain_config ADD COLUMN domain_name VARCHAR(64) NOT NULL AFTER domain');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'domain_slug',
    'ALTER TABLE t_domain_config ADD COLUMN domain_slug VARCHAR(32) NOT NULL AFTER domain_name');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'description',
    'ALTER TABLE t_domain_config ADD COLUMN description VARCHAR(500) NULL AFTER domain_slug');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'sort_order',
    'ALTER TABLE t_domain_config ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER description');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'enabled',
    'ALTER TABLE t_domain_config ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER sort_order');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'risk_level',
    'ALTER TABLE t_domain_config ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT ''LOW'' AFTER enabled');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'posting_notice',
    'ALTER TABLE t_domain_config ADD COLUMN posting_notice VARCHAR(500) NULL AFTER risk_level');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'browse_notice',
    'ALTER TABLE t_domain_config ADD COLUMN browse_notice VARCHAR(500) NULL AFTER posting_notice');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'interaction_notice',
    'ALTER TABLE t_domain_config ADD COLUMN interaction_notice VARCHAR(500) NULL AFTER browse_notice');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'created_by',
    'ALTER TABLE t_domain_config ADD COLUMN created_by BIGINT NULL AFTER interaction_notice');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'updated_by',
    'ALTER TABLE t_domain_config ADD COLUMN updated_by BIGINT NULL AFTER created_by');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'create_time',
    'ALTER TABLE t_domain_config ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER updated_by');
CALL v20260623_domain_config_add_column_if_missing('t_domain_config', 'update_time',
    'ALTER TABLE t_domain_config ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');

CALL v20260623_domain_config_add_primary_if_missing();

CALL v20260623_domain_config_add_index_if_missing('t_domain_config', 'uk_domain_config_slug',
    'ALTER TABLE t_domain_config ADD UNIQUE KEY uk_domain_config_slug (domain_slug)');
CALL v20260623_domain_config_add_index_if_missing('t_domain_config', 'idx_domain_config_enabled_sort',
    'ALTER TABLE t_domain_config ADD KEY idx_domain_config_enabled_sort (enabled, sort_order, domain)');
CALL v20260623_domain_config_add_index_if_missing('t_domain_config', 'idx_domain_config_risk_enabled',
    'ALTER TABLE t_domain_config ADD KEY idx_domain_config_risk_enabled (risk_level, enabled)');

INSERT INTO t_domain_config (
    domain, domain_name, domain_slug, description, sort_order, enabled, risk_level,
    posting_notice, browse_notice, interaction_notice, created_by, updated_by
) VALUES
    (1, '技术', 'tech', '技术实践、架构复盘、工程经验与工具资源。', 10, 1, 'LOW',
     '请尽量补充背景、方案与结果，方便后来者复用。',
     '公开技术内容默认可匿名浏览。', '点赞、收藏、评论前请先登录。', 0, 0),
    (2, '职场', 'career', '求职复盘、成长路径、协作经验与职业选择。', 20, 1, 'MEDIUM',
     '请避免泄露敏感个人与企业信息。', '部分内容可能匿名展示作者身份。', '参与互动前请先登录并遵守社区礼仪。', 0, 0),
    (3, '阅读', 'reading', '书单、方法论、长文摘录与学习笔记。', 30, 1, 'LOW',
     '鼓励给出你的理解、摘录边界与适用场景。',
     '公开阅读内容可直接浏览。', '登录后可收藏、评论与追踪后续讨论。', 0, 0),
    (4, '生活', 'lifestyle', '生活效率、习惯、平衡与个人体验。', 40, 1, 'LOW',
     '欢迎分享真实经验，避免医疗与法律断言。',
     '公开生活内容可直接浏览。', '互动前请先登录，保持友善表达。', 0, 0),
    (5, '投资理财', 'investment', '理财认知、风险教育与个人复盘。', 50, 1, 'HIGH',
     '该领域内容默认进入更严格审核，请避免收益承诺与荐股表述。',
     '浏览时请自行判断风险，社区不构成投资建议。', '互动前请先登录并遵守风险提示。', 0, 0)
ON DUPLICATE KEY UPDATE
    domain_name = VALUES(domain_name),
    domain_slug = VALUES(domain_slug),
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled),
    risk_level = VALUES(risk_level),
    posting_notice = VALUES(posting_notice),
    browse_notice = VALUES(browse_notice),
    interaction_notice = VALUES(interaction_notice),
    updated_by = VALUES(updated_by);

DROP PROCEDURE IF EXISTS v20260623_domain_config_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260623_domain_config_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260623_domain_config_add_primary_if_missing;
