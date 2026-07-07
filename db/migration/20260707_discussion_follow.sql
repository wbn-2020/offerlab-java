SET NAMES utf8mb4;
USE offerlab;

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

DELIMITER ;

CALL v20260707_discussion_follow_create_table_if_missing();

DROP PROCEDURE IF EXISTS v20260707_discussion_follow_create_table_if_missing;
