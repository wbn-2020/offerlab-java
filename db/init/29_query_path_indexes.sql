-- Add the approved composite indexes for content review and revisit query paths.
-- Safe to run repeatedly: each index is created only when its table exists and
-- the index name is missing.

DROP PROCEDURE IF EXISTS v20260726_query_path_add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE v20260726_query_path_add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
        LIMIT 1
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260726_query_path_add_index_if_missing(
    't_int_content_suggestion',
    'idx_content_suggestion_author_resolution_time',
    'ALTER TABLE t_int_content_suggestion ADD KEY idx_content_suggestion_author_resolution_time (post_author_id, resolution, update_time, id)'
);

CALL v20260726_query_path_add_index_if_missing(
    't_post_knowledge_relation',
    'idx_post_knowledge_relation_review_time',
    'ALTER TABLE t_post_knowledge_relation ADD KEY idx_post_knowledge_relation_review_time (review_status, is_deleted, update_time, id)'
);

CALL v20260726_query_path_add_index_if_missing(
    't_int_user_revisit_item',
    'idx_revisit_user_source_status_time',
    'ALTER TABLE t_int_user_revisit_item ADD KEY idx_revisit_user_source_status_time (uid, source_type, revisit_status, update_time, id)'
);

DROP PROCEDURE IF EXISTS v20260726_query_path_add_index_if_missing;
