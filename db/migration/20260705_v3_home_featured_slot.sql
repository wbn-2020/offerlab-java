-- 20260705_v3_home_featured_slot.sql
-- Non-destructive extension for OfferLab V3 HOME_FEATURED published slot snapshots.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260705_add_operation_slot_column_if_missing$$
CREATE PROCEDURE v20260705_add_operation_slot_column_if_missing(
    IN p_column_name VARCHAR(64),
    IN alter_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 't_operation_slot'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = alter_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CALL v20260705_add_operation_slot_column_if_missing('current_version',
    'ALTER TABLE t_operation_slot ADD COLUMN current_version INT NOT NULL DEFAULT 0 AFTER preview_token')$$
CALL v20260705_add_operation_slot_column_if_missing('published_snapshot_json',
    'ALTER TABLE t_operation_slot ADD COLUMN published_snapshot_json JSON NULL AFTER current_version')$$
CALL v20260705_add_operation_slot_column_if_missing('rollback_snapshot_json',
    'ALTER TABLE t_operation_slot ADD COLUMN rollback_snapshot_json JSON NULL AFTER published_snapshot_json')$$

DROP PROCEDURE IF EXISTS v20260705_add_operation_slot_column_if_missing$$

DELIMITER ;

-- Phase 2 discovery square uses the same operation slot lifecycle as HOME_FEATURED.
-- This only creates an empty draft slot; no demo, fixture, fallback, or published content is seeded.
INSERT INTO t_operation_slot (
    id,
    slot_code,
    slot_name,
    description,
    slot_status,
    sort_order,
    default_limit,
    current_version,
    is_deleted
)
SELECT
    202607050002,
    'DISCOVERY_FEATURED_TOPICS',
    '发现页精选专题',
    'OfferLab V3 第二阶段发现页专题广场运营位，仅用于公开可见专题和内容编排。',
    'DRAFT',
    20,
    5,
    0,
    0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM t_operation_slot
    WHERE slot_code = 'DISCOVERY_FEATURED_TOPICS'
      AND is_deleted = 0
)
  AND (
    SELECT COUNT(*)
    FROM t_operation_slot
    WHERE is_deleted = 0
  ) < 2;
