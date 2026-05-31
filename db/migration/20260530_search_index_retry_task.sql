-- Add durable retry tasks for Elasticsearch post index sync failures.
-- Non-destructive: creates a new compensation table only.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_search_index_retry_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    dedup_key       VARCHAR(128) NOT NULL,
    post_id         BIGINT       NOT NULL,
    operation       VARCHAR(16)  NOT NULL COMMENT 'INDEX or DELETE',
    task_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 pending 1 done 2 failed 3 running',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3)  NULL,
    lock_owner      VARCHAR(128) NULL,
    lock_until      DATETIME(3)  NULL,
    last_error      VARCHAR(500) NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_search_index_retry_dedup (dedup_key),
    KEY idx_search_index_retry_due (task_status, next_retry_time),
    KEY idx_search_index_retry_lock (lock_owner, lock_until),
    KEY idx_search_index_retry_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='search index retry tasks';
