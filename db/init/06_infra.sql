-- 06_infra.sql
-- Infrastructure: transactional outbox table.
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_outbox_message;
CREATE TABLE t_outbox_message (
    id              BIGINT       NOT NULL PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    topic           VARCHAR(64)  NOT NULL,
    payload         JSON         NOT NULL,
    msg_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 pending 1 sent 2 failed 3 sending',
    retry_count     INT          NOT NULL DEFAULT 0,
    lock_owner      VARCHAR(128) NULL COMMENT 'scheduler instance that claimed the message',
    lock_until      DATETIME(3)  NULL COMMENT 'claim lease expiry',
    next_retry_time DATETIME(3)  NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status_time (msg_status, next_retry_time),
    KEY idx_lock_owner (lock_owner, lock_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='transactional outbox messages';