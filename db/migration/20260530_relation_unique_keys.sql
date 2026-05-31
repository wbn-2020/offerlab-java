-- Collapse relation uniqueness to one row per business pair.
-- Review duplicate rows before running this migration in production.
SET NAMES utf8mb4;
USE offerlab;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_rel_drop_index_if_exists $$
CREATE PROCEDURE v20260530_rel_drop_index_if_exists(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' DROP INDEX ', p_index);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260530_rel_add_index_if_missing $$
CREATE PROCEDURE v20260530_rel_add_index_if_missing(
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

DROP PROCEDURE IF EXISTS v20260530_rel_assert_no_duplicates $$
CREATE PROCEDURE v20260530_rel_assert_no_duplicates()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT user_id, target_type, target_id
            FROM t_int_like
            GROUP BY user_id, target_type, target_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) dup
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'duplicate t_int_like rows must be reviewed before changing uk_user_target';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT user_id, post_id
            FROM t_int_favorite
            GROUP BY user_id, post_id
            HAVING COUNT(*) > 1
            LIMIT 1
        ) dup
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'duplicate t_int_favorite rows must be reviewed before changing uk_user_post';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT from_uid, to_uid
            FROM t_user_follow
            GROUP BY from_uid, to_uid
            HAVING COUNT(*) > 1
            LIMIT 1
        ) dup
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'duplicate t_user_follow rows must be reviewed before changing uk_from_to';
    END IF;
END $$

DELIMITER ;

CALL v20260530_rel_assert_no_duplicates();

CALL v20260530_rel_drop_index_if_exists('t_int_like', 'uk_user_target');
CALL v20260530_rel_add_index_if_missing('t_int_like', 'uk_user_target',
    'ALTER TABLE t_int_like ADD UNIQUE KEY uk_user_target (user_id, target_type, target_id)');

CALL v20260530_rel_drop_index_if_exists('t_int_favorite', 'uk_user_post');
CALL v20260530_rel_add_index_if_missing('t_int_favorite', 'uk_user_post',
    'ALTER TABLE t_int_favorite ADD UNIQUE KEY uk_user_post (user_id, post_id)');

CALL v20260530_rel_drop_index_if_exists('t_user_follow', 'uk_from_to');
CALL v20260530_rel_add_index_if_missing('t_user_follow', 'uk_from_to',
    'ALTER TABLE t_user_follow ADD UNIQUE KEY uk_from_to (from_uid, to_uid)');

DROP PROCEDURE IF EXISTS v20260530_rel_drop_index_if_exists;
DROP PROCEDURE IF EXISTS v20260530_rel_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260530_rel_assert_no_duplicates;
