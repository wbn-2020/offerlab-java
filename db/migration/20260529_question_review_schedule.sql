-- 20260529_question_review_schedule.sql
-- Non-destructive migration for explicit personal question review scheduling.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260529_review_add_column_if_missing$$
DROP PROCEDURE IF EXISTS v20260529_review_add_index_if_missing$$

CREATE PROCEDURE v20260529_review_add_column_if_missing(
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

CREATE PROCEDURE v20260529_review_add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL v20260529_review_add_column_if_missing('t_user_question_progress', 'next_review_at',
    'ALTER TABLE t_user_question_progress ADD COLUMN next_review_at DATETIME(3) NULL AFTER star_story');

CALL v20260529_review_add_column_if_missing('t_user_question_progress', 'last_reviewed_at',
    'ALTER TABLE t_user_question_progress ADD COLUMN last_reviewed_at DATETIME(3) NULL AFTER next_review_at');

CALL v20260529_review_add_column_if_missing('t_user_question_progress', 'review_count',
    'ALTER TABLE t_user_question_progress ADD COLUMN review_count INT NOT NULL DEFAULT 0 AFTER last_reviewed_at');

CALL v20260529_review_add_column_if_missing('t_user_question_progress', 'review_interval_days',
    'ALTER TABLE t_user_question_progress ADD COLUMN review_interval_days INT NOT NULL DEFAULT 1 AFTER review_count');

CALL v20260529_review_add_index_if_missing('t_user_question_progress', 'idx_uid_next_review',
    'ALTER TABLE t_user_question_progress ADD KEY idx_uid_next_review (uid, next_review_at)');

CALL v20260529_review_add_index_if_missing('t_user_question_progress', 'idx_uid_status_next_review',
    'ALTER TABLE t_user_question_progress ADD KEY idx_uid_status_next_review (uid, progress_status, next_review_at)');

DROP PROCEDURE IF EXISTS v20260529_review_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260529_review_add_index_if_missing;
