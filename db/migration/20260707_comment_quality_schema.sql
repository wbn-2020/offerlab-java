SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260707_comment_quality_add_column_if_missing $$
CREATE PROCEDURE v20260707_comment_quality_add_column_if_missing()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_comment'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_comment'
          AND COLUMN_NAME = 'helpful_count'
    ) THEN
        ALTER TABLE t_int_comment
            ADD COLUMN helpful_count INT NOT NULL DEFAULT 0 AFTER like_count;
    END IF;
END $$

DELIMITER ;

CALL v20260707_comment_quality_add_column_if_missing();

DROP PROCEDURE IF EXISTS v20260707_comment_quality_add_column_if_missing;

CREATE TABLE IF NOT EXISTS t_int_comment_quality_signal (
    id              BIGINT       NOT NULL PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    comment_id      BIGINT       NOT NULL,
    root_id         BIGINT       NOT NULL DEFAULT 0,
    signal_type     VARCHAR(40)  NOT NULL COMMENT 'AUTHOR_PINNED / FEATURED / LOW_QUALITY_FOLDED',
    signal_status   TINYINT      NOT NULL DEFAULT 1 COMMENT '1 active 0 inactive',
    operator_uid    BIGINT       NOT NULL,
    operator_role   VARCHAR(32)  NOT NULL DEFAULT 'user',
    reason          VARCHAR(255) NULL,
    source          VARCHAR(32)  NOT NULL DEFAULT 'author',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_comment_quality_signal_comment_type (comment_id, signal_type),
    KEY idx_comment_quality_signal_post_type_status (post_id, signal_type, signal_status, update_time),
    KEY idx_comment_quality_signal_root_type_status (root_id, signal_type, signal_status),
    KEY idx_comment_quality_signal_operator_time (operator_uid, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment quality signals';

CREATE TABLE IF NOT EXISTS t_int_comment_helpful (
    id              BIGINT      NOT NULL PRIMARY KEY,
    uid             BIGINT      NOT NULL,
    post_id         BIGINT      NOT NULL,
    comment_id      BIGINT      NOT NULL,
    helpful_status  TINYINT     NOT NULL DEFAULT 1 COMMENT '1 helpful 0 canceled',
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_comment_helpful_uid_comment (uid, comment_id),
    KEY idx_comment_helpful_comment_status (comment_id, helpful_status),
    KEY idx_comment_helpful_user_status (uid, helpful_status, update_time),
    KEY idx_comment_helpful_post_comment (post_id, comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='comment helpful marks';
