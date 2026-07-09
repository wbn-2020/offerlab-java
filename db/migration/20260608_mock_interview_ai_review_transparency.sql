-- Non-destructive migration for mock interview AI review transparency fields.
-- Review before running on an existing database.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260608_add_mock_ai_review_transparency_column_if_missing$$

CREATE PROCEDURE v20260608_add_mock_ai_review_transparency_column_if_missing(
    IN p_column_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 't_mock_interview_answer'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_task_id',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_task_id VARCHAR(64) NULL AFTER ai_review_provider');

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_fallback_used',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_fallback_used TINYINT NOT NULL DEFAULT 0 AFTER ai_review_task_id');

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_duration_ms',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_duration_ms BIGINT NOT NULL DEFAULT 0 AFTER ai_review_fallback_used');

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_prompt_tokens',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_prompt_tokens INT NOT NULL DEFAULT 0 AFTER ai_review_duration_ms');

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_completion_tokens',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_completion_tokens INT NOT NULL DEFAULT 0 AFTER ai_review_prompt_tokens');

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_estimated_cost_micros',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_estimated_cost_micros BIGINT NOT NULL DEFAULT 0 AFTER ai_review_completion_tokens');

CALL v20260608_add_mock_ai_review_transparency_column_if_missing('ai_review_error_code',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_error_code VARCHAR(64) NULL AFTER ai_review_estimated_cost_micros');

DROP PROCEDURE IF EXISTS v20260608_add_mock_ai_review_transparency_column_if_missing;
