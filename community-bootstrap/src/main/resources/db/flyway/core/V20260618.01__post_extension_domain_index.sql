-- Domain generated column for post extension hot filters and analytics.
-- Non-destructive migration: adds a virtual generated column and index if missing.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260618_add_post_extension_domain_column_if_missing $$
CREATE PROCEDURE v20260618_add_post_extension_domain_column_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_post_extension'
          AND COLUMN_NAME = 'domain'
    ) THEN
        ALTER TABLE t_post_extension
            ADD COLUMN domain TINYINT GENERATED ALWAYS AS (
                CASE JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.domain'))
                    WHEN '2' THEN 2
                    WHEN '3' THEN 3
                    WHEN '4' THEN 4
                    WHEN '5' THEN 5
                    ELSE 1
                END
            ) VIRTUAL AFTER interview_result;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260618_add_post_extension_domain_index_if_missing $$
CREATE PROCEDURE v20260618_add_post_extension_domain_index_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_post_extension'
          AND INDEX_NAME = 'idx_post_extension_domain_post'
    ) THEN
        ALTER TABLE t_post_extension
            ADD KEY idx_post_extension_domain_post (domain, post_id);
    END IF;
END $$

DELIMITER ;

CALL v20260618_add_post_extension_domain_column_if_missing();
CALL v20260618_add_post_extension_domain_index_if_missing();

DROP PROCEDURE IF EXISTS v20260618_add_post_extension_domain_column_if_missing;
DROP PROCEDURE IF EXISTS v20260618_add_post_extension_domain_index_if_missing;
