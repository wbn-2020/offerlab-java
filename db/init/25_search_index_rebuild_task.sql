SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_search_index_rebuild_task (
    task_id          VARCHAR(64)  NOT NULL,
    task_type        VARCHAR(32)  NOT NULL,
    task_status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    operator_uid     BIGINT       NULL,
    checkpoint_id   BIGINT       NOT NULL DEFAULT 0,
    indexed_count   INT          NOT NULL DEFAULT 0,
    failed_count    INT          NOT NULL DEFAULT 0,
    total_count     INT          NOT NULL DEFAULT 0,
    index_name      VARCHAR(128) NULL,
    last_error      VARCHAR(1000) NULL,
    lock_owner      VARCHAR(255) NULL,
    lock_until      DATETIME(3)  NULL,
    heartbeat_time  DATETIME(3)  NULL,
    started_at      DATETIME(3)  NULL,
    finished_at     DATETIME(3)  NULL,
    active_key      TINYINT GENERATED ALWAYS AS (
        CASE WHEN task_status IN ('PENDING', 'RUNNING') THEN 1 ELSE NULL END
    ) STORED,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_search_index_rebuild_active (active_key),
    KEY idx_search_index_rebuild_status_time (task_status, create_time, task_id),
    KEY idx_search_index_rebuild_lease (task_status, lock_until, task_id),
    CONSTRAINT chk_search_index_rebuild_status
        CHECK (task_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Persistent Elasticsearch post index rebuild task';
