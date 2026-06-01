-- 20260601_question_index_task.sql
-- Durable task state for question index rebuild operations.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_question_index_task (
    task_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
    task_type     VARCHAR(32)  NOT NULL,
    task_status   VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    operator_uid  BIGINT       NULL,
    accepted      TINYINT      NOT NULL DEFAULT 0,
    indexed       INT          NOT NULL DEFAULT 0,
    failed        INT          NOT NULL DEFAULT 0,
    total         INT          NOT NULL DEFAULT 0,
    index_name    VARCHAR(128) NULL,
    message       VARCHAR(500) NULL,
    create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_question_index_task_status_time (task_status, update_time),
    KEY idx_question_index_task_operator_time (operator_uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Question index rebuild task';
