-- Add claim/lease columns for multi-instance outbox flushing.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_outbox_add_column_if_missing $$
CREATE PROCEDURE v20260530_outbox_add_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260530_outbox_add_index_if_missing $$
CREATE PROCEDURE v20260530_outbox_add_index_if_missing(
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

CALL v20260530_outbox_add_column_if_missing('t_outbox_message', 'lock_owner',
    'ALTER TABLE t_outbox_message ADD COLUMN lock_owner VARCHAR(128) NULL COMMENT ''scheduler instance that claimed the message'' AFTER retry_count');

CALL v20260530_outbox_add_column_if_missing('t_outbox_message', 'lock_until',
    'ALTER TABLE t_outbox_message ADD COLUMN lock_until DATETIME(3) NULL COMMENT ''claim lease expiry'' AFTER lock_owner');

CALL v20260530_outbox_add_index_if_missing('t_outbox_message', 'idx_lock_owner',
    'ALTER TABLE t_outbox_message ADD KEY idx_lock_owner (lock_owner, lock_until)');

DROP PROCEDURE IF EXISTS v20260530_outbox_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260530_outbox_add_index_if_missing;