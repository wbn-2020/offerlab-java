-- Add granular notification preferences without changing existing choices.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_notification_pref_add_column_if_missing $$
CREATE PROCEDURE v20260530_notification_pref_add_column_if_missing(
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

DELIMITER ;

CALL v20260530_notification_pref_add_column_if_missing('t_user_privacy_setting', 'like_notification',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN like_notification TINYINT NOT NULL DEFAULT 1 AFTER system_notification');

CALL v20260530_notification_pref_add_column_if_missing('t_user_privacy_setting', 'comment_notification',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN comment_notification TINYINT NOT NULL DEFAULT 1 AFTER like_notification');

CALL v20260530_notification_pref_add_column_if_missing('t_user_privacy_setting', 'follow_notification',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN follow_notification TINYINT NOT NULL DEFAULT 1 AFTER comment_notification');

CALL v20260530_notification_pref_add_column_if_missing('t_user_privacy_setting', 'favorite_notification',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN favorite_notification TINYINT NOT NULL DEFAULT 1 AFTER follow_notification');

CALL v20260530_notification_pref_add_column_if_missing('t_user_privacy_setting', 'mention_notification',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN mention_notification TINYINT NOT NULL DEFAULT 1 AFTER favorite_notification');

DROP PROCEDURE IF EXISTS v20260530_notification_pref_add_column_if_missing;
