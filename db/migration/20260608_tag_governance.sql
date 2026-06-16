-- Tag governance extension for OfferLab community operations.
-- Review duplicate rows before running this migration in production.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260608_add_column_if_missing $$
CREATE PROCEDURE v20260608_add_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260608_add_index_if_missing $$
CREATE PROCEDURE v20260608_add_index_if_missing(
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

DELIMITER ;

CALL v20260608_add_column_if_missing('t_tag', 'tag_status',
    'ALTER TABLE t_tag ADD COLUMN tag_status TINYINT NOT NULL DEFAULT 1 COMMENT ''1启用 0禁用/合并'' AFTER is_official');
CALL v20260608_add_column_if_missing('t_tag', 'recommended',
    'ALTER TABLE t_tag ADD COLUMN recommended TINYINT NOT NULL DEFAULT 0 COMMENT ''1推荐标签'' AFTER tag_status');
CALL v20260608_add_column_if_missing('t_tag', 'synonyms',
    'ALTER TABLE t_tag ADD COLUMN synonyms VARCHAR(512) NULL COMMENT ''同义词，逗号分隔'' AFTER recommended');
CALL v20260608_add_column_if_missing('t_tag', 'merge_target_id',
    'ALTER TABLE t_tag ADD COLUMN merge_target_id BIGINT NULL COMMENT ''合并目标标签'' AFTER synonyms');
CALL v20260608_add_column_if_missing('t_tag', 'update_time',
    'ALTER TABLE t_tag ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');

CALL v20260608_add_index_if_missing('t_tag', 'idx_tag_status_recommend',
    'ALTER TABLE t_tag ADD KEY idx_tag_status_recommend (tag_status, recommended, use_count)');
CALL v20260608_add_index_if_missing('t_tag', 'idx_tag_merge_target',
    'ALTER TABLE t_tag ADD KEY idx_tag_merge_target (merge_target_id)');

DROP PROCEDURE IF EXISTS v20260608_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260608_add_index_if_missing;
