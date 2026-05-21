-- 06_infra.sql
-- 基础设施：事务消息表
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_outbox_message;
CREATE TABLE t_outbox_message (
    id              BIGINT       NOT NULL PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    BIGINT       NOT NULL,
    topic           VARCHAR(64)  NOT NULL,
    payload         JSON         NOT NULL,
    msg_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发 1已发 2失败',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3)  NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status_time (msg_status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务消息';
