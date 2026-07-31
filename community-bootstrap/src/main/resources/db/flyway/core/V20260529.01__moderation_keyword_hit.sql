-- 20260529_moderation_keyword_hit.sql
-- Non-destructive table for sensitive keyword hit logging.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_moderation_keyword_hit (
    id              BIGINT        NOT NULL PRIMARY KEY,
    scope           VARCHAR(32)   NOT NULL DEFAULT 'ALL',
    uid             BIGINT        NULL,
    keyword_id      BIGINT        NULL,
    keyword         VARCHAR(128)  NOT NULL,
    action          VARCHAR(16)   NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK / REVIEW',
    content_summary VARCHAR(200)  NOT NULL DEFAULT '',
    source_type     VARCHAR(32)   NULL COMMENT 'POST / COMMENT / CONTACT_REQUEST',
    source_id       BIGINT        NULL,
    review_status   VARCHAR(16)   NULL COMMENT 'APPROVED / REJECTED / CLOSED',
    reviewer_uid    BIGINT        NULL,
    review_note     VARCHAR(1000) NULL,
    review_time     DATETIME(3)   NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_scope_time (scope, create_time),
    KEY idx_uid_time (uid, create_time),
    KEY idx_keyword_time (keyword, create_time),
    KEY idx_action_time (action, create_time),
    KEY idx_source (source_type, source_id),
    KEY idx_review_status_time (review_status, review_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Moderation keyword hit log';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260529_moderation_hit_add_column_if_missing $$
CREATE PROCEDURE v20260529_moderation_hit_add_column_if_missing(
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_moderation_keyword_hit'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_moderation_keyword_hit'
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260529_moderation_hit_add_index_if_missing $$
CREATE PROCEDURE v20260529_moderation_hit_add_index_if_missing(
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_moderation_keyword_hit'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_moderation_keyword_hit'
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260529_moderation_hit_add_column_if_missing('source_type',
    'ALTER TABLE t_moderation_keyword_hit ADD COLUMN source_type VARCHAR(32) NULL COMMENT ''POST / COMMENT / CONTACT_REQUEST'' AFTER content_summary');
CALL v20260529_moderation_hit_add_column_if_missing('source_id',
    'ALTER TABLE t_moderation_keyword_hit ADD COLUMN source_id BIGINT NULL AFTER source_type');
CALL v20260529_moderation_hit_add_column_if_missing('review_status',
    'ALTER TABLE t_moderation_keyword_hit ADD COLUMN review_status VARCHAR(16) NULL COMMENT ''APPROVED / REJECTED / CLOSED'' AFTER source_id');
CALL v20260529_moderation_hit_add_column_if_missing('reviewer_uid',
    'ALTER TABLE t_moderation_keyword_hit ADD COLUMN reviewer_uid BIGINT NULL AFTER review_status');
CALL v20260529_moderation_hit_add_column_if_missing('review_note',
    'ALTER TABLE t_moderation_keyword_hit ADD COLUMN review_note VARCHAR(1000) NULL AFTER reviewer_uid');
CALL v20260529_moderation_hit_add_column_if_missing('review_time',
    'ALTER TABLE t_moderation_keyword_hit ADD COLUMN review_time DATETIME(3) NULL AFTER review_note');
CALL v20260529_moderation_hit_add_index_if_missing('idx_source',
    'ALTER TABLE t_moderation_keyword_hit ADD KEY idx_source (source_type, source_id)');
CALL v20260529_moderation_hit_add_index_if_missing('idx_review_status_time',
    'ALTER TABLE t_moderation_keyword_hit ADD KEY idx_review_status_time (review_status, review_time)');

DROP PROCEDURE IF EXISTS v20260529_moderation_hit_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260529_moderation_hit_add_index_if_missing;
