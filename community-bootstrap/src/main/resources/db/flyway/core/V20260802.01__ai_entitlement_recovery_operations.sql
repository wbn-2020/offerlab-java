-- V27: recovery reads and idempotent operations reconciliation for the bounded AI entitlement.
-- This migration stores only operator request fingerprints and normalized aggregate results.
-- It must never store raw drafts, prompts, idempotency keys from end users, or model responses.
SET NAMES utf8mb4;

ALTER TABLE t_content_assist_enhanced_request
    ADD KEY idx_content_assist_enhanced_recent (uid, consumer_code, update_time, id);

CREATE TABLE t_content_assist_enhanced_reconcile_request (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    operator_uid        BIGINT        NOT NULL,
    idempotency_key     VARCHAR(96)   NOT NULL,
    request_fingerprint CHAR(64)      NOT NULL,
    request_status      VARCHAR(16)   NOT NULL,
    result_json         JSON          NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_content_assist_enhanced_reconcile_request (operator_uid, idempotency_key),
    KEY idx_content_assist_enhanced_reconcile_recent (operator_uid, update_time, id),
    CONSTRAINT chk_content_assist_enhanced_reconcile_status
        CHECK (request_status IN ('RUNNING', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent operations reconcile requests for AI entitlement fulfillment';
