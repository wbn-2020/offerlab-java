SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_expert_cert_application (
    id                        BIGINT       NOT NULL PRIMARY KEY,
    applicant_uid             BIGINT       NOT NULL,
    domain                    TINYINT      NOT NULL,
    status                    INT          NOT NULL DEFAULT 10,
    evidence_summary          VARCHAR(500) NOT NULL,
    evidence_links_json       TEXT         NULL,
    eligibility_passed        TINYINT      NOT NULL DEFAULT 0,
    eligibility_summary       VARCHAR(500) NULL,
    eligibility_snapshot_json TEXT         NULL,
    risk_acknowledged         TINYINT      NOT NULL DEFAULT 0,
    risk_warning              VARCHAR(500) NULL,
    reviewer_uid              BIGINT       NULL,
    review_note               VARCHAR(500) NULL,
    review_time               DATETIME(3)  NULL,
    revoked_by                BIGINT       NULL,
    revoke_note               VARCHAR(500) NULL,
    revoked_time              DATETIME(3)  NULL,
    create_time               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted                TINYINT      NOT NULL DEFAULT 0,
    active_guard              TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND status IN (10, 20) THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_expert_cert_active_guard (applicant_uid, domain, active_guard),
    KEY idx_expert_cert_applicant_domain (applicant_uid, domain, status, update_time),
    KEY idx_expert_cert_review_queue (domain, status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Expert certification pilot applications';

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260624_expert_cert_add_column_if_missing $$
CREATE PROCEDURE v20260624_expert_cert_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DROP PROCEDURE IF EXISTS v20260624_expert_cert_add_index_if_missing $$
CREATE PROCEDURE v20260624_expert_cert_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'id',
    'ALTER TABLE t_expert_cert_application ADD COLUMN id BIGINT NOT NULL FIRST');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'applicant_uid',
    'ALTER TABLE t_expert_cert_application ADD COLUMN applicant_uid BIGINT NOT NULL AFTER id');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'domain',
    'ALTER TABLE t_expert_cert_application ADD COLUMN domain TINYINT NOT NULL AFTER applicant_uid');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'status',
    'ALTER TABLE t_expert_cert_application ADD COLUMN status INT NOT NULL DEFAULT 10 AFTER domain');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'evidence_summary',
    'ALTER TABLE t_expert_cert_application ADD COLUMN evidence_summary VARCHAR(500) NOT NULL AFTER status');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'evidence_links_json',
    'ALTER TABLE t_expert_cert_application ADD COLUMN evidence_links_json TEXT NULL AFTER evidence_summary');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'eligibility_passed',
    'ALTER TABLE t_expert_cert_application ADD COLUMN eligibility_passed TINYINT NOT NULL DEFAULT 0 AFTER evidence_links_json');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'eligibility_summary',
    'ALTER TABLE t_expert_cert_application ADD COLUMN eligibility_summary VARCHAR(500) NULL AFTER eligibility_passed');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'eligibility_snapshot_json',
    'ALTER TABLE t_expert_cert_application ADD COLUMN eligibility_snapshot_json TEXT NULL AFTER eligibility_summary');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'risk_acknowledged',
    'ALTER TABLE t_expert_cert_application ADD COLUMN risk_acknowledged TINYINT NOT NULL DEFAULT 0 AFTER eligibility_snapshot_json');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'risk_warning',
    'ALTER TABLE t_expert_cert_application ADD COLUMN risk_warning VARCHAR(500) NULL AFTER risk_acknowledged');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'reviewer_uid',
    'ALTER TABLE t_expert_cert_application ADD COLUMN reviewer_uid BIGINT NULL AFTER risk_warning');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'review_note',
    'ALTER TABLE t_expert_cert_application ADD COLUMN review_note VARCHAR(500) NULL AFTER reviewer_uid');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'review_time',
    'ALTER TABLE t_expert_cert_application ADD COLUMN review_time DATETIME(3) NULL AFTER review_note');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'revoked_by',
    'ALTER TABLE t_expert_cert_application ADD COLUMN revoked_by BIGINT NULL AFTER review_time');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'revoke_note',
    'ALTER TABLE t_expert_cert_application ADD COLUMN revoke_note VARCHAR(500) NULL AFTER revoked_by');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'revoked_time',
    'ALTER TABLE t_expert_cert_application ADD COLUMN revoked_time DATETIME(3) NULL AFTER revoke_note');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'create_time',
    'ALTER TABLE t_expert_cert_application ADD COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER revoked_time');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'update_time',
    'ALTER TABLE t_expert_cert_application ADD COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER create_time');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'is_deleted',
    'ALTER TABLE t_expert_cert_application ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER update_time');
CALL v20260624_expert_cert_add_column_if_missing('t_expert_cert_application', 'active_guard',
    'ALTER TABLE t_expert_cert_application ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND status IN (10, 20) THEN 1 ELSE NULL END) STORED AFTER is_deleted');

CALL v20260624_expert_cert_add_index_if_missing('t_expert_cert_application', 'uk_expert_cert_active_guard',
    'ALTER TABLE t_expert_cert_application ADD UNIQUE KEY uk_expert_cert_active_guard (applicant_uid, domain, active_guard)');
CALL v20260624_expert_cert_add_index_if_missing('t_expert_cert_application', 'idx_expert_cert_applicant_domain',
    'ALTER TABLE t_expert_cert_application ADD KEY idx_expert_cert_applicant_domain (applicant_uid, domain, status, update_time)');
CALL v20260624_expert_cert_add_index_if_missing('t_expert_cert_application', 'idx_expert_cert_review_queue',
    'ALTER TABLE t_expert_cert_application ADD KEY idx_expert_cert_review_queue (domain, status, update_time, id)');

DROP PROCEDURE IF EXISTS v20260624_expert_cert_add_column_if_missing;
DROP PROCEDURE IF EXISTS v20260624_expert_cert_add_index_if_missing;
