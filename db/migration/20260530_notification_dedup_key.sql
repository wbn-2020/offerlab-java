-- Add business idempotency key for notifications.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_notif_dedup_add_column_if_missing $$
CREATE PROCEDURE v20260530_notif_dedup_add_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260530_notif_dedup_add_index_if_missing $$
CREATE PROCEDURE v20260530_notif_dedup_add_index_if_missing(
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

CALL v20260530_notif_dedup_add_column_if_missing('t_notif_message', 'dedup_key',
    'ALTER TABLE t_notif_message ADD COLUMN dedup_key VARCHAR(255) NULL AFTER content_json');

UPDATE t_notif_message
SET dedup_key = CONCAT_WS(':', receiver_uid, COALESCE(sender_uid, 0), notif_type, COALESCE(target_type, 0), COALESCE(target_id, id), id)
WHERE dedup_key IS NULL OR dedup_key = '';

ALTER TABLE t_notif_message MODIFY COLUMN dedup_key VARCHAR(255) NOT NULL;

CALL v20260530_notif_dedup_add_index_if_missing('t_notif_message', 'uk_notif_dedup',
    'ALTER TABLE t_notif_message ADD UNIQUE KEY uk_notif_dedup (dedup_key)');

DROP PROCEDURE IF EXISTS v20260530_notif_dedup_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260530_notif_dedup_add_index_if_missing;
