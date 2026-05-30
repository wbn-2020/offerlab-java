-- 20260528_mock_interview_snapshot.sql
-- Non-destructive migration for preserving mock interview question text after question re-extraction.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260528_add_mock_answer_column_if_missing$$

CREATE PROCEDURE v20260528_add_mock_answer_column_if_missing(
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

CALL v20260528_add_mock_answer_column_if_missing('question_text_snapshot',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN question_text_snapshot TEXT NULL AFTER sequence_no');

CALL v20260528_add_mock_answer_column_if_missing('answer_hint_snapshot',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN answer_hint_snapshot TEXT NULL AFTER question_text_snapshot');

CALL v20260528_add_mock_answer_column_if_missing('company_snapshot',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN company_snapshot VARCHAR(128) NULL AFTER answer_hint_snapshot');

CALL v20260528_add_mock_answer_column_if_missing('position_snapshot',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN position_snapshot VARCHAR(128) NULL AFTER company_snapshot');

CALL v20260528_add_mock_answer_column_if_missing('round_snapshot',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN round_snapshot VARCHAR(64) NULL AFTER position_snapshot');

CALL v20260528_add_mock_answer_column_if_missing('difficulty_snapshot',
    'ALTER TABLE t_mock_interview_answer ADD COLUMN difficulty_snapshot VARCHAR(16) NULL AFTER round_snapshot');

DROP PROCEDURE IF EXISTS v20260528_add_mock_answer_column_if_missing;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260528_add_mock_session_column_if_missing$$

CREATE PROCEDURE v20260528_add_mock_session_column_if_missing(
    IN p_column_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 't_mock_interview_session'
          AND column_name = p_column_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL v20260528_add_mock_session_column_if_missing('focus_tag',
    'ALTER TABLE t_mock_interview_session ADD COLUMN focus_tag VARCHAR(64) NULL AFTER difficulty');

DROP PROCEDURE IF EXISTS v20260528_add_mock_session_column_if_missing;
