-- 20260529_question_canonical_index.sql
-- Non-destructive migration for cross-post question deduplication lookup.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260529_add_index_if_missing$$

CREATE PROCEDURE v20260529_add_index_if_missing(
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

CALL v20260529_add_index_if_missing('t_interview_question', 'idx_normalized_status',
    'ALTER TABLE t_interview_question ADD KEY idx_normalized_status (normalized_hash, status, source_post_id)');

DROP PROCEDURE IF EXISTS v20260529_add_index_if_missing;
