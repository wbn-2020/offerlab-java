SET NAMES utf8mb4;
USE offerlab;

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
            request_status        VARCHAR(32)  NOT NULL,
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
            KEY idx_contact_request_pair_status (requester_uid, receiver_uid, request_status, expire_time)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='contact requests';
    END IF;
END $$

DELIMITER ;

CALL v20260707_contact_request_create_table_if_missing();

DROP PROCEDURE IF EXISTS v20260707_contact_request_create_table_if_missing;
