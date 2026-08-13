-- V26: real fulfillment for the bounded AI content-assist entitlement.
-- This migration stores only controlled references and normalized replay data. It never stores raw drafts or prompts.
SET NAMES utf8mb4;

ALTER TABLE t_virtual_benefit_entitlement_usage
    ADD COLUMN benefit_code VARCHAR(64) NOT NULL DEFAULT 'LEGACY_MANUAL' AFTER user_id,
    ADD COLUMN consumer_code VARCHAR(64) NOT NULL DEFAULT 'LEGACY_MANUAL' AFTER benefit_code,
    ADD COLUMN usage_status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED' AFTER request_fingerprint,
    ADD COLUMN source_type VARCHAR(32) NULL AFTER usage_status,
    ADD COLUMN source_ref VARCHAR(128) NULL AFTER source_type,
    ADD COLUMN expires_at DATETIME(3) NULL AFTER source_ref,
    ADD COLUMN confirmed_at DATETIME(3) NULL AFTER expires_at,
    ADD COLUMN released_at DATETIME(3) NULL AFTER confirmed_at,
    ADD COLUMN failure_code VARCHAR(64) NULL AFTER released_at;

UPDATE t_virtual_benefit_entitlement_usage
SET benefit_code = COALESCE((
        SELECT e.benefit_code
        FROM t_virtual_benefit_entitlement e
        WHERE e.id = t_virtual_benefit_entitlement_usage.entitlement_id
    ), 'LEGACY_MANUAL'),
    consumer_code = CONCAT('LEGACY_MANUAL:', entitlement_id),
    usage_status = 'CONFIRMED',
    confirmed_at = COALESCE(confirmed_at, create_time)
WHERE consumer_code = 'LEGACY_MANUAL';

ALTER TABLE t_virtual_benefit_entitlement_usage
    ADD UNIQUE KEY uk_virtual_entitlement_usage_request (user_id, consumer_code, idempotency_key),
    ADD KEY idx_virtual_entitlement_usage_recovery (usage_status, expires_at, id),
    ADD KEY idx_virtual_entitlement_usage_user_time (user_id, consumer_code, create_time, id),
    ADD CONSTRAINT chk_virtual_entitlement_usage_status
        CHECK (usage_status IN ('RESERVED', 'CONFIRMED', 'RELEASED'));

CREATE TABLE t_content_assist_enhanced_request (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    uid                 BIGINT        NOT NULL,
    consumer_code       VARCHAR(64)   NOT NULL,
    idempotency_key     VARCHAR(96)   NOT NULL,
    request_fingerprint CHAR(64)      NOT NULL,
    content_hash        CHAR(64)      NOT NULL,
    content_length      INT           NOT NULL,
    usage_id            BIGINT        NULL,
    request_status      VARCHAR(16)   NOT NULL,
    provider            VARCHAR(32)   NULL,
    result_json         JSON          NULL,
    prompt_tokens       INT           NOT NULL DEFAULT 0,
    completion_tokens   INT           NOT NULL DEFAULT 0,
    estimated_cost_micros BIGINT      NOT NULL DEFAULT 0,
    error_code          VARCHAR(64)   NULL,
    completed_at        DATETIME(3)   NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_content_assist_enhanced_request (uid, consumer_code, idempotency_key),
    KEY idx_content_assist_enhanced_cleanup (request_status, completed_at, id),
    KEY idx_content_assist_enhanced_recovery (request_status, update_time, id),
    KEY idx_content_assist_enhanced_usage (usage_id),
    CONSTRAINT chk_content_assist_enhanced_status
        CHECK (request_status IN ('RUNNING', 'SUCCEEDED', 'FALLBACK', 'FAILED')),
    CONSTRAINT chk_content_assist_enhanced_content_length CHECK (content_length >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Short-lived replay metadata for paid content-assist enhancement';

UPDATE t_virtual_benefit_catalog
SET enabled = 0,
    action_reason = 'V26: disabled until an explainable collection consumer exists',
    update_time = CURRENT_TIMESTAMP(3)
WHERE benefit_code = 'COLLECTION_ORGANIZATION_QUOTA';
