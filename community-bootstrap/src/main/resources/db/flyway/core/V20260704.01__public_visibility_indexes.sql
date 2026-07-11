SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260704_add_index_if_missing $$
CREATE PROCEDURE v20260704_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260704_add_index_if_missing('t_post_main', 'idx_post_public_time_id',
    'ALTER TABLE t_post_main ADD KEY idx_post_public_time_id (is_deleted, post_status, visibility, create_time, id)');

CALL v20260704_add_index_if_missing('t_post_main', 'idx_post_public_author_time',
    'ALTER TABLE t_post_main ADD KEY idx_post_public_author_time (is_deleted, post_status, visibility, author_id, create_time, id)');

CALL v20260704_add_index_if_missing('t_tag', 'idx_tag_public_lookup',
    'ALTER TABLE t_tag ADD KEY idx_tag_public_lookup (is_deleted, tag_status, merge_target_id, id)');

DROP PROCEDURE IF EXISTS v20260704_add_index_if_missing;
