-- 20260529_moderation_keyword_hit.sql
-- Non-destructive table for sensitive keyword hit logging.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_moderation_keyword_hit (
    id              BIGINT        NOT NULL PRIMARY KEY,
    scope           VARCHAR(32)   NOT NULL DEFAULT 'ALL',
    uid             BIGINT        NULL,
    keyword_id      BIGINT        NULL,
    keyword         VARCHAR(128)  NOT NULL,
    action          VARCHAR(16)   NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK / REVIEW',
    content_summary VARCHAR(200)  NOT NULL DEFAULT '',
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_scope_time (scope, create_time),
    KEY idx_uid_time (uid, create_time),
    KEY idx_keyword_time (keyword, create_time),
    KEY idx_action_time (action, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Moderation keyword hit log';
