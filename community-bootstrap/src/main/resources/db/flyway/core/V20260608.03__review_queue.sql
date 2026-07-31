-- 20260608_review_queue.sql
-- Non-destructive migration for the unified community review queue.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_review_queue (
    id              BIGINT        NOT NULL PRIMARY KEY,
    source_type     VARCHAR(32)   NOT NULL,
    source_id       BIGINT        NULL,
    title           VARCHAR(200)  NOT NULL,
    summary         VARCHAR(1000) NULL,
    risk_level      VARCHAR(16)   NOT NULL DEFAULT 'medium',
    queue_status    VARCHAR(16)   NOT NULL DEFAULT 'pending',
    assignee_uid    BIGINT        NULL,
    creator_uid     BIGINT        NULL,
    priority        INT           NOT NULL DEFAULT 0,
    due_time        DATETIME(3)   NULL,
    handled_time    DATETIME(3)   NULL,
    handle_result   VARCHAR(32)   NULL,
    handle_note     VARCHAR(500)  NULL,
    ext_json        JSON          NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_review_queue_source (source_type, source_id),
    KEY idx_review_queue_status_priority (queue_status, priority, create_time),
    KEY idx_review_queue_source_status (source_type, queue_status),
    KEY idx_review_queue_assignee_status (assignee_uid, queue_status),
    KEY idx_review_queue_risk_status (risk_level, queue_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unified review queue';
