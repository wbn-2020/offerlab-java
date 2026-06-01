-- 20260601_mock_interview_ai_review_status.sql
-- Non-destructive migration for async mock interview AI review state.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260601_add_mock_ai_review_state_column_if_missing$$

CREATE PROCEDURE v20260601_add_mock_ai_review_state_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260601_add_mock_ai_review_state_index_if_missing$$

CREATE PROCEDURE v20260601_add_mock_ai_review_state_index_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 't_mock_interview_answer'
          AND index_name = 'idx_ai_review_status'
    ) THEN
        ALTER TABLE t_mock_interview_answer ADD INDEX idx_ai_review_status (uid, session_id, ai_review_status);
    END IF;
END$$

DELIMITER ;

CALL v20260601_add_mock_ai_review_state_column_if_missing('ai_review_status',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_status VARCHAR(16) NOT NULL DEFAULT ''NOT_REQUESTED'' AFTER ai_reviewed');

CALL v20260601_add_mock_ai_review_state_column_if_missing('ai_review_error',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN ai_review_error VARCHAR(500) NULL AFTER ai_review_status');

CALL v20260601_add_mock_ai_review_state_index_if_missing();

DROP PROCEDURE IF EXISTS v20260601_add_mock_ai_review_state_column_if_missing;
DROP PROCEDURE IF EXISTS v20260601_add_mock_ai_review_state_index_if_missing;
