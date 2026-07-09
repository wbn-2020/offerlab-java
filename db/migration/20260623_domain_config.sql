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
    (1, '鎶€鏈?, 'tech', '鎶€鏈疄璺点€佹灦鏋勫鐩樸€佸伐绋嬬粡楠屼笌宸ュ叿璧勬簮銆?, 10, 1, 'LOW',
     '璇峰敖閲忚ˉ鍏呰儗鏅€佹柟妗堜笌缁撴灉锛屾柟渚垮悗鏉ヨ€呭鐢ㄣ€?,
     '鍏紑鎶€鏈唴瀹归粯璁ゅ彲鍖垮悕娴忚銆?, '鐐硅禐銆佹敹钘忋€佽瘎璁哄墠璇峰厛鐧诲綍銆?, 0, 0),
    (2, '鑱屽満', 'career', '姹傝亴澶嶇洏銆佹垚闀胯矾寰勩€佸崗浣滅粡楠屼笌鑱屼笟閫夋嫨銆?, 20, 1, 'MEDIUM',
     '璇烽伩鍏嶆硠闇叉晱鎰熶釜浜轰笌浼佷笟淇℃伅銆?, '閮ㄥ垎鍐呭鍙兘鍖垮悕灞曠ず浣滆€呰韩浠姐€?, '鍙備笌浜掑姩鍓嶈鍏堢櫥褰曞苟閬靛畧绀惧尯绀间华銆?, 0, 0),
    (3, '闃呰', 'reading', '涔﹀崟銆佹柟娉曡銆侀暱鏂囨憳褰曚笌瀛︿範绗旇銆?, 30, 1, 'LOW',
     '榧撳姳缁欏嚭浣犵殑鐞嗚В銆佹憳褰曡竟鐣屼笌閫傜敤鍦烘櫙銆?,
     '鍏紑闃呰鍐呭鍙洿鎺ユ祻瑙堛€?, '鐧诲綍鍚庡彲鏀惰棌銆佽瘎璁轰笌杩借釜鍚庣画璁ㄨ銆?, 0, 0),
    (4, '鐢熸椿', 'lifestyle', '鐢熸椿鏁堢巼銆佷範鎯€佸钩琛′笌涓汉浣撻獙銆?, 40, 1, 'LOW',
     '娆㈣繋鍒嗕韩鐪熷疄缁忛獙锛岄伩鍏嶅尰鐤椾笌娉曞緥鏂█銆?,
     '鍏紑鐢熸椿鍐呭鍙洿鎺ユ祻瑙堛€?, '浜掑姩鍓嶈鍏堢櫥褰曪紝淇濇寔鍙嬪杽琛ㄨ揪銆?, 0, 0),
    (5, '鎶曡祫鐞嗚储', 'investment', '鐞嗚储璁ょ煡銆侀闄╂暀鑲蹭笌涓汉澶嶇洏銆?, 50, 1, 'HIGH',
     '璇ラ鍩熷唴瀹归粯璁よ繘鍏ユ洿涓ユ牸瀹℃牳锛岃閬垮厤鏀剁泭鎵胯涓庤崘鑲¤〃杩般€?,
     '娴忚鏃惰鑷鍒ゆ柇椋庨櫓锛岀ぞ鍖轰笉鏋勬垚鎶曡祫寤鸿銆?, '浜掑姩鍓嶈鍏堢櫥褰曞苟閬靛畧椋庨櫓鎻愮ず銆?, 0, 0)
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
