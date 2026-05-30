-- Add interview schedule metadata to user prep targets without touching existing rows.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_prep_target_add_column_if_missing $$
CREATE PROCEDURE v20260530_prep_target_add_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260530_prep_target_add_index_if_missing $$
CREATE PROCEDURE v20260530_prep_target_add_index_if_missing(
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

CALL v20260530_prep_target_add_column_if_missing('t_user_prep_target', 'interview_date',
    'ALTER TABLE t_user_prep_target ADD COLUMN interview_date DATE NULL AFTER target_value');

CALL v20260530_prep_target_add_column_if_missing('t_user_prep_target', 'priority',
    'ALTER TABLE t_user_prep_target ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT ''medium'' COMMENT ''low / medium / high / urgent'' AFTER interview_date');

CALL v20260530_prep_target_add_column_if_missing('t_user_prep_target', 'note',
    'ALTER TABLE t_user_prep_target ADD COLUMN note VARCHAR(300) NULL AFTER priority');

CALL v20260530_prep_target_add_index_if_missing('t_user_prep_target', 'idx_uid_interview_priority',
    'ALTER TABLE t_user_prep_target ADD KEY idx_uid_interview_priority (uid, interview_date, priority)');

DROP PROCEDURE IF EXISTS v20260530_prep_target_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260530_prep_target_add_index_if_missing;
