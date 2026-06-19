-- Domain moderators for OfferLab comprehensive community governance.
-- Non-destructive migration: creates the association table if it is missing.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_domain_moderator (
    id              BIGINT      NOT NULL PRIMARY KEY,
    uid             BIGINT      NOT NULL,
    domain          TINYINT     NOT NULL COMMENT '1 tech, 2 career, 3 reading, 4 lifestyle, 5 investment',
    enabled         TINYINT     NOT NULL DEFAULT 1,
    created_by      BIGINT      NULL,
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_domain_moderator_uid_domain (uid, domain),
    KEY idx_domain_moderator_domain_enabled (domain, enabled),
    KEY idx_domain_moderator_uid_enabled (uid, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Domain-scoped community moderators';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260617_add_column_if_missing $$
CREATE PROCEDURE v20260617_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260617_add_index_if_missing $$
CREATE PROCEDURE v20260617_add_index_if_missing(
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

DROP PROCEDURE IF EXISTS v20260617_add_domain_moderator_pk_if_missing $$
CREATE PROCEDURE v20260617_add_domain_moderator_pk_if_missing()
BEGIN
    DECLARE v_primary_key_count INT DEFAULT 0;
    DECLARE v_null_id_count INT DEFAULT 0;
    DECLARE v_duplicate_id_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_primary_key_count
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_domain_moderator'
       AND INDEX_NAME = 'PRIMARY'
       AND COLUMN_NAME = 'id';

    SELECT COUNT(*)
      INTO v_null_id_count
      FROM t_domain_moderator
     WHERE id IS NULL;

    SELECT COUNT(*)
      INTO v_duplicate_id_count
      FROM (
        SELECT id
          FROM t_domain_moderator
         WHERE id IS NOT NULL
         GROUP BY id
        HAVING COUNT(*) > 1
      ) duplicated_ids;

    IF v_primary_key_count = 0
       AND v_null_id_count = 0
       AND v_duplicate_id_count = 0 THEN
        ALTER TABLE t_domain_moderator ADD PRIMARY KEY (id);
    END IF;
END $$

DELIMITER ;

CALL v20260617_add_column_if_missing('t_domain_moderator', 'id',
    'ALTER TABLE t_domain_moderator ADD COLUMN id BIGINT NOT NULL PRIMARY KEY FIRST');
CALL v20260617_add_column_if_missing('t_domain_moderator', 'uid',
    'ALTER TABLE t_domain_moderator ADD COLUMN uid BIGINT NOT NULL AFTER id');
CALL v20260617_add_column_if_missing('t_domain_moderator', 'domain',
    'ALTER TABLE t_domain_moderator ADD COLUMN domain TINYINT NOT NULL COMMENT ''1 tech, 2 career, 3 reading, 4 lifestyle, 5 investment'' AFTER uid');
CALL v20260617_add_column_if_missing('t_domain_moderator', 'enabled',
    'ALTER TABLE t_domain_moderator ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 AFTER domain');
CALL v20260617_add_column_if_missing('t_domain_moderator', 'created_by',
    'ALTER TABLE t_domain_moderator ADD COLUMN created_by BIGINT NULL AFTER enabled');
CALL v20260617_add_column_if_missing('t_domain_moderator', 'create_time',
    'ALTER TABLE t_domain_moderator ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER created_by');
CALL v20260617_add_column_if_missing('t_domain_moderator', 'update_time',
    'ALTER TABLE t_domain_moderator ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');

CALL v20260617_add_domain_moderator_pk_if_missing();

CALL v20260617_add_index_if_missing('t_domain_moderator', 'uk_domain_moderator_uid_domain',
    'ALTER TABLE t_domain_moderator ADD UNIQUE KEY uk_domain_moderator_uid_domain (uid, domain)');
CALL v20260617_add_index_if_missing('t_domain_moderator', 'idx_domain_moderator_domain_enabled',
    'ALTER TABLE t_domain_moderator ADD KEY idx_domain_moderator_domain_enabled (domain, enabled)');
CALL v20260617_add_index_if_missing('t_domain_moderator', 'idx_domain_moderator_uid_enabled',
    'ALTER TABLE t_domain_moderator ADD KEY idx_domain_moderator_uid_enabled (uid, enabled)');

DROP PROCEDURE IF EXISTS v20260617_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260617_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260617_add_domain_moderator_pk_if_missing;
