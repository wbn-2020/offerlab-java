SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_growth_event (
    id              BIGINT        NOT NULL PRIMARY KEY,
    event_type      VARCHAR(32)   NOT NULL,
    uid             BIGINT        NULL,
    domain          TINYINT       NULL,
    content_id      BIGINT        NULL,
    target_type     VARCHAR(32)   NULL,
    target_value    VARCHAR(128)  NULL,
    source_page     VARCHAR(128)  NULL,
    ext_json        JSON          NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_growth_event_type_time (event_type, create_time),
    KEY idx_growth_event_domain_time (domain, create_time),
    KEY idx_growth_event_uid_time (uid, create_time),
    KEY idx_growth_event_content_time (content_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community growth event log';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260623_growth_event_add_column_if_missing $$
CREATE PROCEDURE v20260623_growth_event_add_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260623_growth_event_add_index_if_missing $$
CREATE PROCEDURE v20260623_growth_event_add_index_if_missing(
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

DROP PROCEDURE IF EXISTS v20260623_growth_event_add_primary_if_missing $$
CREATE PROCEDURE v20260623_growth_event_add_primary_if_missing()
BEGIN
    DECLARE v_primary_key_count INT DEFAULT 0;
    DECLARE v_null_id_count INT DEFAULT 0;
    DECLARE v_duplicate_id_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO v_primary_key_count
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_growth_event'
       AND INDEX_NAME = 'PRIMARY'
       AND COLUMN_NAME = 'id';

    SELECT COUNT(*)
      INTO v_null_id_count
      FROM t_growth_event
     WHERE id IS NULL;

    SELECT COUNT(*)
      INTO v_duplicate_id_count
      FROM (
        SELECT id
        FROM t_growth_event
        WHERE id IS NOT NULL
        GROUP BY id
        HAVING COUNT(*) > 1
      ) duplicated_ids;

    IF v_primary_key_count = 0
       AND v_null_id_count = 0
       AND v_duplicate_id_count = 0 THEN
        ALTER TABLE t_growth_event ADD PRIMARY KEY (id);
    END IF;
END $$

DELIMITER ;

CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'id',
    'ALTER TABLE t_growth_event ADD COLUMN id BIGINT NOT NULL FIRST');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'event_type',
    'ALTER TABLE t_growth_event ADD COLUMN event_type VARCHAR(32) NOT NULL AFTER id');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'uid',
    'ALTER TABLE t_growth_event ADD COLUMN uid BIGINT NULL AFTER event_type');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'domain',
    'ALTER TABLE t_growth_event ADD COLUMN domain TINYINT NULL AFTER uid');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'content_id',
    'ALTER TABLE t_growth_event ADD COLUMN content_id BIGINT NULL AFTER domain');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'target_type',
    'ALTER TABLE t_growth_event ADD COLUMN target_type VARCHAR(32) NULL AFTER content_id');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'target_value',
    'ALTER TABLE t_growth_event ADD COLUMN target_value VARCHAR(128) NULL AFTER target_type');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'source_page',
    'ALTER TABLE t_growth_event ADD COLUMN source_page VARCHAR(128) NULL AFTER target_value');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'ext_json',
    'ALTER TABLE t_growth_event ADD COLUMN ext_json JSON NULL AFTER source_page');
CALL v20260623_growth_event_add_column_if_missing('t_growth_event', 'create_time',
    'ALTER TABLE t_growth_event ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER ext_json');

CALL v20260623_growth_event_add_primary_if_missing();

CALL v20260623_growth_event_add_index_if_missing('t_growth_event', 'idx_growth_event_type_time',
    'ALTER TABLE t_growth_event ADD KEY idx_growth_event_type_time (event_type, create_time)');
CALL v20260623_growth_event_add_index_if_missing('t_growth_event', 'idx_growth_event_domain_time',
    'ALTER TABLE t_growth_event ADD KEY idx_growth_event_domain_time (domain, create_time)');
CALL v20260623_growth_event_add_index_if_missing('t_growth_event', 'idx_growth_event_uid_time',
    'ALTER TABLE t_growth_event ADD KEY idx_growth_event_uid_time (uid, create_time)');
CALL v20260623_growth_event_add_index_if_missing('t_growth_event', 'idx_growth_event_content_time',
    'ALTER TABLE t_growth_event ADD KEY idx_growth_event_content_time (content_id, create_time)');

DROP PROCEDURE IF EXISTS v20260623_growth_event_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260623_growth_event_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260623_growth_event_add_primary_if_missing;
