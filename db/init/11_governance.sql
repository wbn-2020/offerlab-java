-- 11_governance.sql
-- Audit log and lightweight content governance tables.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_admin_audit_log (
    id              BIGINT        NOT NULL PRIMARY KEY,
    operator_uid    BIGINT        NULL,
    action          VARCHAR(64)   NOT NULL,
    resource_type   VARCHAR(64)   NOT NULL,
    resource_id     VARCHAR(64)   NULL,
    before_json     JSON          NULL,
    after_json      JSON          NULL,
    remark          VARCHAR(1000) NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_operator_time (operator_uid, create_time),
    KEY idx_action_time (action, create_time),
    KEY idx_resource_time (resource_type, resource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Admin audit log';

CREATE TABLE IF NOT EXISTS t_moderation_keyword (
    id              BIGINT       NOT NULL PRIMARY KEY,
    keyword         VARCHAR(128) NOT NULL,
    match_type      VARCHAR(16)  NOT NULL DEFAULT 'CONTAINS' COMMENT 'CONTAINS / EXACT',
    action          VARCHAR(16)  NOT NULL DEFAULT 'BLOCK' COMMENT 'BLOCK / REVIEW',
    scope           VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL / POST / COMMENT / REPORT',
    enabled         TINYINT      NOT NULL DEFAULT 1,
    remark          VARCHAR(200) NOT NULL DEFAULT '',
    operator_uid    BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_keyword_scope (keyword, scope),
    KEY idx_scope_enabled (scope, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Moderation keywords';

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

CREATE TABLE IF NOT EXISTS t_user_moderation_state (
    uid             BIGINT       NOT NULL PRIMARY KEY,
    muted_until     DATETIME(3)  NULL,
    banned_until    DATETIME(3)  NULL,
    reason          VARCHAR(500) NOT NULL DEFAULT '',
    operator_uid    BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_muted_until (muted_until),
    KEY idx_banned_until (banned_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User moderation state';
