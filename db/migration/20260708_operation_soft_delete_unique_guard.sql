-- Repair operation curation soft-delete unique keys so deleted history can repeat.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260708_operation_add_column_if_missing $$
CREATE PROCEDURE v20260708_operation_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
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

DROP PROCEDURE IF EXISTS v20260708_operation_drop_index_if_exists $$
CREATE PROCEDURE v20260708_operation_drop_index_if_exists(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' DROP INDEX ', p_index);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260708_operation_add_index_if_missing $$
CREATE PROCEDURE v20260708_operation_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
    )
    AND NOT EXISTS (
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

CALL v20260708_operation_add_column_if_missing('t_operation_curation_item', 'active_guard',
    'ALTER TABLE t_operation_curation_item ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER is_deleted');
CALL v20260708_operation_add_column_if_missing('t_operation_slot_item', 'active_guard',
    'ALTER TABLE t_operation_slot_item ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER is_deleted');

CALL v20260708_operation_drop_index_if_exists('t_operation_curation_item', 'uk_operation_curation_source');
CALL v20260708_operation_add_index_if_missing('t_operation_curation_item', 'uk_operation_curation_source',
    'ALTER TABLE t_operation_curation_item ADD UNIQUE KEY uk_operation_curation_source (source_type, source_id, active_guard)');

CALL v20260708_operation_drop_index_if_exists('t_operation_slot_item', 'uk_operation_slot_source');
CALL v20260708_operation_add_index_if_missing('t_operation_slot_item', 'uk_operation_slot_source',
    'ALTER TABLE t_operation_slot_item ADD UNIQUE KEY uk_operation_slot_source (slot_id, source_type, source_id, active_guard)');

DROP PROCEDURE IF EXISTS v20260708_operation_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260708_operation_drop_index_if_exists;
DROP PROCEDURE IF EXISTS v20260708_operation_add_index_if_missing;
