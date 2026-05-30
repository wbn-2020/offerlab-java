-- 20260530_mock_interview_ai_review.sql
-- Non-destructive migration for optional AI/rule review of mock interview answers.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_add_mock_ai_review_column_if_missing$$

CREATE PROCEDURE v20260530_add_mock_ai_review_column_if_missing(
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

CALL v20260530_add_mock_ai_review_column_if_missing('ai_reviewed',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_reviewed TINYINT NOT NULL DEFAULT 0 AFTER score');

CALL v20260530_add_mock_ai_review_column_if_missing('ai_score',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_score INT NULL AFTER ai_reviewed');

CALL v20260530_add_mock_ai_review_column_if_missing('ai_completeness',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_completeness VARCHAR(300) NULL AFTER ai_score');

CALL v20260530_add_mock_ai_review_column_if_missing('ai_project_expression',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_project_expression VARCHAR(300) NULL AFTER ai_completeness');

CALL v20260530_add_mock_ai_review_column_if_missing('ai_follow_up_suggestion',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_follow_up_suggestion VARCHAR(300) NULL AFTER ai_project_expression');

CALL v20260530_add_mock_ai_review_column_if_missing('ai_review_provider',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_provider VARCHAR(32) NULL AFTER ai_follow_up_suggestion');

DROP PROCEDURE IF EXISTS v20260530_add_mock_ai_review_column_if_missing;
