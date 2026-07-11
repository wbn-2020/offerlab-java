-- 20260707_contact_request_settings.sql
-- Non-destructive migration for user contact request settings.
SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260707_contact_request_require_table $$
CREATE PROCEDURE v20260707_contact_request_require_table(
    IN p_table_name VARCHAR(128)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'required table for contact request settings migration is missing';
    END IF;
END$$

DROP PROCEDURE IF EXISTS v20260707_contact_request_add_column_if_missing $$
CREATE PROCEDURE v20260707_contact_request_add_column_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    )
    AND NOT EXISTS (
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

DROP PROCEDURE IF EXISTS v20260707_contact_request_add_index_if_missing $$
CREATE PROCEDURE v20260707_contact_request_add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    )
    AND NOT EXISTS (
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

DROP PROCEDURE IF EXISTS v20260707_contact_request_modify_column_if_exists $$
CREATE PROCEDURE v20260707_contact_request_modify_column_if_exists(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF EXISTS (
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

CALL v20260707_contact_request_require_table('t_user_privacy_setting');
CALL v20260707_contact_request_add_column_if_missing('t_user_privacy_setting', 'accept_contact_request',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN accept_contact_request TINYINT NOT NULL DEFAULT 1 COMMENT ''1 accept, 0 closed'' AFTER mention_notification');
CALL v20260707_contact_request_add_column_if_missing('t_user_privacy_setting', 'contact_request_policy',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN contact_request_policy VARCHAR(16) NOT NULL DEFAULT ''following'' COMMENT ''all / following / mutual / off; application dictionary owner: ContactRequestSettingsService'' AFTER accept_contact_request');
CALL v20260707_contact_request_add_column_if_missing('t_user_privacy_setting', 'contact_request_daily_limit',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN contact_request_daily_limit INT NULL COMMENT ''NULL means no explicit daily limit; application range 1..1000'' AFTER contact_request_policy');
CALL v20260707_contact_request_modify_column_if_exists('t_user_privacy_setting', 'accept_contact_request',
    'ALTER TABLE t_user_privacy_setting MODIFY COLUMN accept_contact_request TINYINT NOT NULL DEFAULT 1 COMMENT ''1 accept, 0 closed''');
CALL v20260707_contact_request_modify_column_if_exists('t_user_privacy_setting', 'contact_request_policy',
    'ALTER TABLE t_user_privacy_setting MODIFY COLUMN contact_request_policy VARCHAR(16) NOT NULL DEFAULT ''following'' COMMENT ''all / following / mutual / off; application dictionary owner: ContactRequestSettingsService''');
CALL v20260707_contact_request_modify_column_if_exists('t_user_privacy_setting', 'contact_request_daily_limit',
    'ALTER TABLE t_user_privacy_setting MODIFY COLUMN contact_request_daily_limit INT NULL COMMENT ''NULL means no explicit daily limit; application range 1..1000''');
CALL v20260707_contact_request_add_index_if_missing('t_user_privacy_setting', 'idx_contact_request_policy',
    'ALTER TABLE t_user_privacy_setting ADD KEY idx_contact_request_policy (contact_request_policy)');

DROP PROCEDURE IF EXISTS v20260707_contact_request_require_table;
DROP PROCEDURE IF EXISTS v20260707_contact_request_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260707_contact_request_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260707_contact_request_modify_column_if_exists;
