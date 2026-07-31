SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260707_discussion_follow_create_table_if_missing $$
CREATE PROCEDURE v20260707_discussion_follow_create_table_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_discussion_follow'
    ) THEN
        CREATE TABLE t_int_discussion_follow (
            id                        BIGINT      NOT NULL PRIMARY KEY,
            uid                       BIGINT      NOT NULL,
            post_id                   BIGINT      NOT NULL,
            follow_status             TINYINT     NOT NULL DEFAULT 1 COMMENT '1 following 0 unfollowed',
            last_read_comment_id      BIGINT      NULL,
            last_notified_comment_id  BIGINT      NULL,
            create_time               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            update_time               DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            is_deleted                TINYINT     NOT NULL DEFAULT 0,
            UNIQUE KEY uk_discussion_follow_user_post (uid, post_id),
            KEY idx_discussion_follow_post_status (post_id, follow_status, id),
            KEY idx_discussion_follow_uid_status (uid, follow_status, update_time)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='post discussion follows';
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_discussion_follow_add_column_if_missing $$
CREATE PROCEDURE v20260707_discussion_follow_add_column_if_missing(
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_discussion_follow'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_discussion_follow'
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_discussion_follow_add_index_if_missing $$
CREATE PROCEDURE v20260707_discussion_follow_add_index_if_missing(
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_discussion_follow'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_discussion_follow'
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_discussion_follow_precheck $$
CREATE PROCEDURE v20260707_discussion_follow_precheck()
BEGIN
    UPDATE t_int_discussion_follow
    SET follow_status = 1
    WHERE follow_status IS NULL;

    UPDATE t_int_discussion_follow
    SET is_deleted = 0
    WHERE is_deleted IS NULL;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT uid, post_id
            FROM t_int_discussion_follow
            GROUP BY uid, post_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate t_int_discussion_follow(uid, post_id) rows must be repaired before adding uk_discussion_follow_user_post';
    END IF;
END $$

DELIMITER ;

CALL v20260707_discussion_follow_create_table_if_missing();

CALL v20260707_discussion_follow_add_column_if_missing('uid',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN uid BIGINT NOT NULL DEFAULT 0 AFTER id');
CALL v20260707_discussion_follow_add_column_if_missing('post_id',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN post_id BIGINT NOT NULL DEFAULT 0 AFTER uid');
CALL v20260707_discussion_follow_add_column_if_missing('follow_status',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN follow_status TINYINT NOT NULL DEFAULT 1 AFTER post_id');
CALL v20260707_discussion_follow_add_column_if_missing('last_read_comment_id',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN last_read_comment_id BIGINT NULL AFTER follow_status');
CALL v20260707_discussion_follow_add_column_if_missing('last_notified_comment_id',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN last_notified_comment_id BIGINT NULL AFTER last_read_comment_id');
CALL v20260707_discussion_follow_add_column_if_missing('create_time',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER last_notified_comment_id');
CALL v20260707_discussion_follow_add_column_if_missing('update_time',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');
CALL v20260707_discussion_follow_add_column_if_missing('is_deleted',
    'ALTER TABLE t_int_discussion_follow ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER update_time');

CALL v20260707_discussion_follow_precheck();

CALL v20260707_discussion_follow_add_index_if_missing('uk_discussion_follow_user_post',
    'ALTER TABLE t_int_discussion_follow ADD UNIQUE KEY uk_discussion_follow_user_post (uid, post_id)');
CALL v20260707_discussion_follow_add_index_if_missing('idx_discussion_follow_post_status',
    'ALTER TABLE t_int_discussion_follow ADD KEY idx_discussion_follow_post_status (post_id, follow_status, id)');
CALL v20260707_discussion_follow_add_index_if_missing('idx_discussion_follow_uid_status',
    'ALTER TABLE t_int_discussion_follow ADD KEY idx_discussion_follow_uid_status (uid, follow_status, update_time)');

DROP PROCEDURE IF EXISTS v20260707_discussion_follow_create_table_if_missing;
DROP PROCEDURE IF EXISTS v20260707_discussion_follow_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260707_discussion_follow_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260707_discussion_follow_precheck;
