-- 20260529_question_structured_fields.sql
-- Non-destructive migration for richer AI extracted interview question structure.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260529_struct_add_column_if_missing$$

CREATE PROCEDURE v20260529_struct_add_column_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL v20260529_struct_add_column_if_missing('t_interview_question', 'exam_point',
    'ALTER TABLE t_interview_question ADD COLUMN exam_point VARCHAR(255) NULL AFTER answer_hint');

CALL v20260529_struct_add_column_if_missing('t_interview_question', 'reference_answer',
    'ALTER TABLE t_interview_question ADD COLUMN reference_answer TEXT NULL AFTER exam_point');

CALL v20260529_struct_add_column_if_missing('t_interview_question', 'source_snippet',
    'ALTER TABLE t_interview_question ADD COLUMN source_snippet TEXT NULL AFTER reference_answer');

CALL v20260529_struct_add_column_if_missing('t_interview_question', 'quality_reason',
    'ALTER TABLE t_interview_question ADD COLUMN quality_reason VARCHAR(500) NULL AFTER source_snippet');

DROP PROCEDURE IF EXISTS v20260529_struct_add_column_if_missing;
