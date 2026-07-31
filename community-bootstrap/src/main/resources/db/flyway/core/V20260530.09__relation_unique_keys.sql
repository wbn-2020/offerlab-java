-- Collapse relation uniqueness to one row per business pair.
-- Review duplicate rows before running this migration in production.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260530_rel_ensure_unique_index $$
CREATE PROCEDURE v20260530_rel_ensure_unique_index(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_expected_columns VARCHAR(255),
    IN p_add_clause TEXT
)
BEGIN
    DECLARE v_actual_columns VARCHAR(255);
    DECLARE v_non_unique INT DEFAULT NULL;

    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','),
           MIN(NON_UNIQUE)
      INTO v_actual_columns, v_non_unique
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table
       AND INDEX_NAME = p_index;

    IF v_actual_columns IS NULL THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_add_clause);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    ELSEIF v_non_unique <> 0 OR v_actual_columns <> p_expected_columns THEN
        -- MySQL 8 atomic DDL keeps the old unique key in place unless the
        -- replacement key is created successfully in the same ALTER.
        SET @ddl = CONCAT(
            'ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`, ADD ', p_add_clause
        );
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

CALL v20260530_rel_ensure_unique_index(
    't_int_like',
    'uk_user_target',
    'user_id,target_type,target_id',
    'UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`)'
);

CALL v20260530_rel_ensure_unique_index(
    't_int_favorite',
    'uk_user_post',
    'user_id,post_id',
    'UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)'
);

CALL v20260530_rel_ensure_unique_index(
    't_user_follow',
    'uk_from_to',
    'from_uid,to_uid',
    'UNIQUE KEY `uk_from_to` (`from_uid`, `to_uid`)'
);

DROP PROCEDURE IF EXISTS v20260530_rel_ensure_unique_index;
DROP PROCEDURE IF EXISTS v20260530_rel_assert_no_duplicates;
