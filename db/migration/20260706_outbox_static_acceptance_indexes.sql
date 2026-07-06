-- Static acceptance hardening for outbox claim ordering.
-- Review before execution. This script is additive and does not change data.
-- Execute against the target schema selected by the connection/session.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260706_add_index_if_missing $$
CREATE PROCEDURE v20260706_add_index_if_missing(
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

CALL v20260706_add_index_if_missing('t_outbox_message', 'idx_status_retry_create',
    'ALTER TABLE t_outbox_message ADD KEY idx_status_retry_create (msg_status, next_retry_time, create_time)');

DROP PROCEDURE IF EXISTS v20260706_add_index_if_missing;
