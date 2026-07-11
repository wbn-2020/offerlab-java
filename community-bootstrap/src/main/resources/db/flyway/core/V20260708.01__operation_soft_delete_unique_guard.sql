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

DROP PROCEDURE IF EXISTS v20260708_operation_ensure_unique_index $$
CREATE PROCEDURE v20260708_operation_ensure_unique_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_expected_columns VARCHAR(255),
    IN p_add_clause TEXT
)
BEGIN
    DECLARE v_actual_columns VARCHAR(255);
    DECLARE v_non_unique INT DEFAULT NULL;

    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','),
           MIN(NON_UNIQUE)
      INTO v_actual_columns, v_non_unique
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table
       AND INDEX_NAME = p_index;

    IF v_actual_columns IS NULL THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_add_clause);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF v_non_unique <> 0 OR v_actual_columns <> p_expected_columns THEN
        -- Keep the old key active until MySQL atomically commits the
        -- replacement definition in this same ALTER TABLE statement.
        SET @ddl = CONCAT(
            'ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`, ADD ', p_add_clause
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260708_operation_assert_no_active_duplicates $$
CREATE PROCEDURE v20260708_operation_assert_no_active_duplicates()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT source_type, source_id
            FROM t_operation_curation_item
            WHERE is_deleted = 0
            GROUP BY source_type, source_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate active operation curation sources must be repaired before replacing uk_operation_curation_source';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT slot_id, source_type, source_id
            FROM t_operation_slot_item
            WHERE is_deleted = 0
            GROUP BY slot_id, source_type, source_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate active operation slot sources must be repaired before replacing uk_operation_slot_source';
    END IF;
END $$

DELIMITER ;

CALL v20260708_operation_add_column_if_missing('t_operation_curation_item', 'active_guard',
    'ALTER TABLE t_operation_curation_item ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER is_deleted');
CALL v20260708_operation_add_column_if_missing('t_operation_slot_item', 'active_guard',
    'ALTER TABLE t_operation_slot_item ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED AFTER is_deleted');

CALL v20260708_operation_assert_no_active_duplicates();

CALL v20260708_operation_ensure_unique_index(
    't_operation_curation_item',
    'uk_operation_curation_source',
    'source_type,source_id,active_guard',
    'UNIQUE KEY `uk_operation_curation_source` (`source_type`, `source_id`, `active_guard`)'
);

CALL v20260708_operation_ensure_unique_index(
    't_operation_slot_item',
    'uk_operation_slot_source',
    'slot_id,source_type,source_id,active_guard',
    'UNIQUE KEY `uk_operation_slot_source` (`slot_id`, `source_type`, `source_id`, `active_guard`)'
);

DROP PROCEDURE IF EXISTS v20260708_operation_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260708_operation_ensure_unique_index;
DROP PROCEDURE IF EXISTS v20260708_operation_assert_no_active_duplicates;
