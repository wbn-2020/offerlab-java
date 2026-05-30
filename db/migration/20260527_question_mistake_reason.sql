-- 20260527_question_mistake_reason.sql
-- Non-destructive migration for adding lightweight review reason tags.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260527_add_column_if_missing$$
DROP PROCEDURE IF EXISTS v20260527_add_index_if_missing$$

CREATE PROCEDURE v20260527_add_column_if_missing(
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

CREATE PROCEDURE v20260527_add_index_if_missing(
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

CALL v20260527_add_column_if_missing('t_user_question_progress', 'mistake_reason',
    'ALTER TABLE t_user_question_progress ADD COLUMN mistake_reason VARCHAR(32) NULL COMMENT ''concept / project / memory / expression / careless / other'' AFTER note');

CALL v20260527_add_column_if_missing('t_user_question_progress', 'answer_draft',
    'ALTER TABLE t_user_question_progress ADD COLUMN answer_draft TEXT NULL AFTER mistake_reason');

CALL v20260527_add_column_if_missing('t_user_question_progress', 'star_story',
    'ALTER TABLE t_user_question_progress ADD COLUMN star_story TEXT NULL AFTER answer_draft');

CALL v20260527_add_index_if_missing('t_user_question_progress', 'idx_uid_update_time',
    'ALTER TABLE t_user_question_progress ADD KEY idx_uid_update_time (uid, update_time)');

CALL v20260527_add_index_if_missing('t_user_question_progress', 'idx_uid_reason_time',
    'ALTER TABLE t_user_question_progress ADD KEY idx_uid_reason_time (uid, mistake_reason, update_time)');

CALL v20260527_add_index_if_missing('t_user_question_progress', 'idx_uid_status_question_time',
    'ALTER TABLE t_user_question_progress ADD KEY idx_uid_status_question_time (uid, progress_status, question_id, update_time)');

CALL v20260527_add_index_if_missing('t_user_question_progress', 'idx_uid_reason_question_time',
    'ALTER TABLE t_user_question_progress ADD KEY idx_uid_reason_question_time (uid, mistake_reason, question_id, update_time)');

DROP PROCEDURE IF EXISTS v20260527_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260527_add_index_if_missing;
