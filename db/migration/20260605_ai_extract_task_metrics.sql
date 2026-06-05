-- 20260605_ai_extract_task_metrics.sql
-- Non-destructive migration for AI extraction observability metrics.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260605_add_ai_extract_task_metric_column_if_missing$$

CREATE PROCEDURE v20260605_add_ai_extract_task_metric_column_if_missing(
    IN p_column_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 't_ai_extract_task'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL v20260605_add_ai_extract_task_metric_column_if_missing('provider',
    'ALTER TABLE t_ai_extract_task ADD COLUMN provider VARCHAR(32) NULL COMMENT ''deepseek / rules / none'' AFTER question_count');

CALL v20260605_add_ai_extract_task_metric_column_if_missing('fallback_used',
    'ALTER TABLE t_ai_extract_task ADD COLUMN fallback_used TINYINT NOT NULL DEFAULT 0 AFTER provider');

CALL v20260605_add_ai_extract_task_metric_column_if_missing('duration_ms',
    'ALTER TABLE t_ai_extract_task ADD COLUMN duration_ms BIGINT NOT NULL DEFAULT 0 AFTER fallback_used');

CALL v20260605_add_ai_extract_task_metric_column_if_missing('prompt_tokens',
    'ALTER TABLE t_ai_extract_task ADD COLUMN prompt_tokens INT NOT NULL DEFAULT 0 AFTER duration_ms');

CALL v20260605_add_ai_extract_task_metric_column_if_missing('completion_tokens',
    'ALTER TABLE t_ai_extract_task ADD COLUMN completion_tokens INT NOT NULL DEFAULT 0 AFTER prompt_tokens');

CALL v20260605_add_ai_extract_task_metric_column_if_missing('estimated_cost_micros',
    'ALTER TABLE t_ai_extract_task ADD COLUMN estimated_cost_micros BIGINT NOT NULL DEFAULT 0 AFTER completion_tokens');

CALL v20260605_add_ai_extract_task_metric_column_if_missing('error_code',
    'ALTER TABLE t_ai_extract_task ADD COLUMN error_code VARCHAR(64) NULL AFTER estimated_cost_micros');

DROP PROCEDURE IF EXISTS v20260605_add_ai_extract_task_metric_column_if_missing;
