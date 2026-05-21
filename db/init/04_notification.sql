-- 04_notification.sql
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_notif_message;
CREATE TABLE t_notif_message (
    id              BIGINT       NOT NULL PRIMARY KEY,
    receiver_uid    BIGINT       NOT NULL,
    sender_uid      BIGINT       NULL,
    notif_type      TINYINT      NOT NULL COMMENT '1点赞 2评论 3@ 4新粉丝 5系统',
    target_type     TINYINT      NULL,
    target_id       BIGINT       NULL,
    content_json    JSON         NOT NULL,
    is_read         TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_receiver_unread (receiver_uid, is_read, create_time),
    KEY idx_receiver_type   (receiver_uid, notif_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知';
