-- 04_notification.sql
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_notif_message;
CREATE TABLE t_notif_message (
    id              BIGINT       NOT NULL PRIMARY KEY,
    receiver_uid    BIGINT       NOT NULL,
    sender_uid      BIGINT       NULL,
    notif_type      TINYINT      NOT NULL COMMENT '1 like 2 comment 3 favorite 4 follower 5 system 6 mention',
    target_type     TINYINT      NULL,
    target_id       BIGINT       NULL,
    content_json    JSON         NOT NULL,
    dedup_key       VARCHAR(255) NOT NULL,
    is_read         TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_notif_dedup (dedup_key),
    KEY idx_receiver_unread (receiver_uid, is_read, create_time),
    KEY idx_receiver_type   (receiver_uid, notif_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='notifications';

DROP TABLE IF EXISTS t_notif_retry_task;
CREATE TABLE t_notif_retry_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    dedup_key       VARCHAR(255) NOT NULL,
    scene           VARCHAR(64)  NOT NULL,
    receiver_uid    BIGINT       NOT NULL,
    sender_uid      BIGINT       NOT NULL DEFAULT 0,
    notif_type      TINYINT      NOT NULL COMMENT '1 like 2 comment 3 favorite 4 follower 5 system 6 mention',
    target_type     TINYINT      NULL,
    target_id       BIGINT       NULL,
    content_json    JSON         NOT NULL,
    task_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 pending 1 done 2 failed 3 running',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3)  NULL,
    lock_owner      VARCHAR(128) NULL,
    lock_until      DATETIME(3)  NULL,
    last_error      VARCHAR(500) NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_notif_retry_dedup (dedup_key),
    KEY idx_notif_retry_due (task_status, next_retry_time),
    KEY idx_notif_retry_lock (lock_owner, lock_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='notification retry tasks';
