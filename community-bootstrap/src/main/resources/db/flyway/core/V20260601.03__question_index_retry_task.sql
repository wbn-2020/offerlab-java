-- 20260601_question_index_retry_task.sql
-- Durable retry queue for question Elasticsearch incremental indexing.
-- Review before running on an existing database.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_question_index_retry_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    dedup_key       VARCHAR(128) NOT NULL,
    question_id     BIGINT       NOT NULL,
    operation       VARCHAR(16)  NOT NULL COMMENT 'INDEX / DELETE',
    task_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 pending, 1 done, 2 failed, 3 running',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3)  NULL,
    lock_owner      VARCHAR(128) NULL,
    lock_until      DATETIME(3)  NULL,
    last_error      VARCHAR(500) NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_question_index_retry_dedup (dedup_key),
    KEY idx_question_index_retry_due (task_status, next_retry_time),
    KEY idx_question_index_retry_claim (task_status, next_retry_time, create_time, id),
    KEY idx_question_index_retry_lock (lock_owner, lock_until),
    KEY idx_question_index_retry_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Question ES index retry task';
