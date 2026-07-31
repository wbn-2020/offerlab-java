SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260707_contact_request_create_table_if_missing $$
CREATE PROCEDURE v20260707_contact_request_create_table_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_contact_request'
    ) THEN
        CREATE TABLE t_int_contact_request (
            id                    BIGINT       NOT NULL PRIMARY KEY,
            requester_uid         BIGINT       NOT NULL,
            receiver_uid          BIGINT       NOT NULL,
            source_type           VARCHAR(32)  NOT NULL,
            source_id             BIGINT       NULL,
            scene                 VARCHAR(32)  NOT NULL,
            message_preview       VARCHAR(500) NOT NULL,
            request_status        VARCHAR(32)  NOT NULL COMMENT 'PENDING / ACCEPTED / REJECTED / IGNORED / REPORTED / CANCELLED / EXPIRED',
            receiver_action_time  DATETIME(3)  NULL,
            expire_time           DATETIME(3)  NULL,
            report_id             BIGINT       NULL,
            dedup_key             VARCHAR(128) NOT NULL,
            create_time           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            update_time           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
            is_deleted            TINYINT      NOT NULL DEFAULT 0,
            UNIQUE KEY uk_contact_request_dedup (dedup_key),
            KEY idx_contact_request_receiver_status (receiver_uid, request_status, update_time),
            KEY idx_contact_request_requester_status (requester_uid, request_status, update_time),
            KEY idx_contact_request_pair_status (requester_uid, receiver_uid, request_status, expire_time),
            KEY idx_contact_request_requester_day (requester_uid, is_deleted, create_time)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='contact requests';
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_contact_request_add_column_if_missing $$
CREATE PROCEDURE v20260707_contact_request_add_column_if_missing(
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_contact_request'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_contact_request'
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_contact_request_add_index_if_missing $$
CREATE PROCEDURE v20260707_contact_request_add_index_if_missing(
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_contact_request'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_contact_request'
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_contact_request_modify_column_if_exists $$
CREATE PROCEDURE v20260707_contact_request_modify_column_if_exists(
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_int_contact_request'
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260707_contact_request_precheck $$
CREATE PROCEDURE v20260707_contact_request_precheck()
BEGIN
    UPDATE t_int_contact_request
    SET dedup_key = CONCAT('legacy:', id),
        update_time = CURRENT_TIMESTAMP(3)
    WHERE dedup_key IS NULL
       OR dedup_key = '';

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT dedup_key
            FROM t_int_contact_request
            GROUP BY dedup_key
            HAVING COUNT(*) > 1
            LIMIT 1
        ) duplicates
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'duplicate t_int_contact_request.dedup_key rows must be repaired before adding uk_contact_request_dedup';
    END IF;
END $$

DELIMITER ;

CALL v20260707_contact_request_create_table_if_missing();

CALL v20260707_contact_request_add_column_if_missing('requester_uid',
    'ALTER TABLE t_int_contact_request ADD COLUMN requester_uid BIGINT NOT NULL DEFAULT 0 AFTER id');
CALL v20260707_contact_request_add_column_if_missing('receiver_uid',
    'ALTER TABLE t_int_contact_request ADD COLUMN receiver_uid BIGINT NOT NULL DEFAULT 0 AFTER requester_uid');
CALL v20260707_contact_request_add_column_if_missing('source_type',
    'ALTER TABLE t_int_contact_request ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''PROFILE'' AFTER receiver_uid');
CALL v20260707_contact_request_add_column_if_missing('source_id',
    'ALTER TABLE t_int_contact_request ADD COLUMN source_id BIGINT NULL AFTER source_type');
CALL v20260707_contact_request_add_column_if_missing('scene',
    'ALTER TABLE t_int_contact_request ADD COLUMN scene VARCHAR(32) NOT NULL DEFAULT ''PROFILE'' AFTER source_id');
CALL v20260707_contact_request_add_column_if_missing('message_preview',
    'ALTER TABLE t_int_contact_request ADD COLUMN message_preview VARCHAR(500) NOT NULL DEFAULT '''' AFTER scene');
CALL v20260707_contact_request_add_column_if_missing('request_status',
    'ALTER TABLE t_int_contact_request ADD COLUMN request_status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' AFTER message_preview');
CALL v20260707_contact_request_add_column_if_missing('receiver_action_time',
    'ALTER TABLE t_int_contact_request ADD COLUMN receiver_action_time DATETIME(3) NULL AFTER request_status');
CALL v20260707_contact_request_add_column_if_missing('expire_time',
    'ALTER TABLE t_int_contact_request ADD COLUMN expire_time DATETIME(3) NULL AFTER receiver_action_time');
CALL v20260707_contact_request_add_column_if_missing('report_id',
    'ALTER TABLE t_int_contact_request ADD COLUMN report_id BIGINT NULL AFTER expire_time');
CALL v20260707_contact_request_add_column_if_missing('dedup_key',
    'ALTER TABLE t_int_contact_request ADD COLUMN dedup_key VARCHAR(128) NULL AFTER report_id');
CALL v20260707_contact_request_add_column_if_missing('create_time',
    'ALTER TABLE t_int_contact_request ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER dedup_key');
CALL v20260707_contact_request_add_column_if_missing('update_time',
    'ALTER TABLE t_int_contact_request ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');
CALL v20260707_contact_request_add_column_if_missing('is_deleted',
    'ALTER TABLE t_int_contact_request ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER update_time');

CALL v20260707_contact_request_precheck();

CALL v20260707_contact_request_modify_column_if_exists('dedup_key',
    'ALTER TABLE t_int_contact_request MODIFY COLUMN dedup_key VARCHAR(128) NOT NULL');
CALL v20260707_contact_request_modify_column_if_exists('request_status',
    'ALTER TABLE t_int_contact_request MODIFY COLUMN request_status VARCHAR(32) NOT NULL COMMENT ''PENDING / ACCEPTED / REJECTED / IGNORED / REPORTED / CANCELLED / EXPIRED''');

CALL v20260707_contact_request_add_index_if_missing('uk_contact_request_dedup',
    'ALTER TABLE t_int_contact_request ADD UNIQUE KEY uk_contact_request_dedup (dedup_key)');
CALL v20260707_contact_request_add_index_if_missing('idx_contact_request_receiver_status',
    'ALTER TABLE t_int_contact_request ADD KEY idx_contact_request_receiver_status (receiver_uid, request_status, update_time)');
CALL v20260707_contact_request_add_index_if_missing('idx_contact_request_requester_status',
    'ALTER TABLE t_int_contact_request ADD KEY idx_contact_request_requester_status (requester_uid, request_status, update_time)');
CALL v20260707_contact_request_add_index_if_missing('idx_contact_request_pair_status',
    'ALTER TABLE t_int_contact_request ADD KEY idx_contact_request_pair_status (requester_uid, receiver_uid, request_status, expire_time)');
CALL v20260707_contact_request_add_index_if_missing('idx_contact_request_requester_day',
    'ALTER TABLE t_int_contact_request ADD KEY idx_contact_request_requester_day (requester_uid, is_deleted, create_time)');

DROP PROCEDURE IF EXISTS v20260707_contact_request_create_table_if_missing;
DROP PROCEDURE IF EXISTS v20260707_contact_request_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260707_contact_request_add_index_if_missing;
DROP PROCEDURE IF EXISTS v20260707_contact_request_modify_column_if_exists;
DROP PROCEDURE IF EXISTS v20260707_contact_request_precheck;
