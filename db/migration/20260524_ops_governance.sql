-- 20260524_ops_governance.sql
-- Non-destructive migration for existing OfferLab databases.
-- Review before running. It only creates tables or indexes when missing.
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

CREATE TABLE IF NOT EXISTS t_user_prep_target (
    id              BIGINT       NOT NULL PRIMARY KEY,
    uid             BIGINT       NOT NULL,
    target_type     VARCHAR(16)  NOT NULL COMMENT 'company / position / tag',
    target_value    VARCHAR(128) NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_target (uid, target_type, target_value),
    KEY idx_uid_time (uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User prep target';

DELIMITER $$

CREATE PROCEDURE add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_ddl TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_index_if_missing('t_post_report', 'idx_post_reporter_status',
    'ALTER TABLE t_post_report ADD KEY idx_post_reporter_status (post_id, reporter_uid, report_status)');
CALL add_index_if_missing('t_comment_report', 'idx_comment_reporter_status',
    'ALTER TABLE t_comment_report ADD KEY idx_comment_reporter_status (comment_id, reporter_uid, report_status)');
CALL add_index_if_missing('t_interview_question', 'idx_status_time',
    'ALTER TABLE t_interview_question ADD KEY idx_status_time (status, create_time)');
CALL add_index_if_missing('t_interview_question', 'idx_company_time',
    'ALTER TABLE t_interview_question ADD KEY idx_company_time (company, create_time)');
CALL add_index_if_missing('t_interview_question', 'idx_position_time',
    'ALTER TABLE t_interview_question ADD KEY idx_position_time (position, create_time)');

DROP PROCEDURE add_index_if_missing;
