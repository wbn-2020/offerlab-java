-- Add durable retry tasks for notification creation failures.
-- Non-destructive: creates a new compensation table only.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_notif_retry_task (
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
