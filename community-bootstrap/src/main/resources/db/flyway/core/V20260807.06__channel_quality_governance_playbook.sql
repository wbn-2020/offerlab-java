CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook (
    id BIGINT NOT NULL,
    playbook_code VARCHAR(80) NOT NULL,
    latest_version_no INT NOT NULL DEFAULT 0,
    playbook_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_playbook_code (playbook_code),
    CONSTRAINT chk_quality_governance_playbook_shape CHECK (
        id > 0 AND CHAR_LENGTH(TRIM(playbook_code)) BETWEEN 2 AND 80
        AND latest_version_no >= 0 AND playbook_version >= 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook_version (
    id BIGINT NOT NULL,
    playbook_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    content_schema_version VARCHAR(48) NOT NULL,
    content_summary VARCHAR(500) NOT NULL,
    canonical_content_json JSON NOT NULL,
    content_hash CHAR(71) NOT NULL,
    published_at DATETIME(3) NULL,
    retired_at DATETIME(3) NULL,
    created_by_uid BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_playbook_version_no (playbook_id, version_no),
    UNIQUE KEY uk_quality_governance_playbook_version_hash (content_hash),
    KEY idx_quality_governance_playbook_version_status (status, playbook_id, version_no),
    CONSTRAINT chk_quality_governance_playbook_version_shape CHECK (
        id > 0 AND playbook_id > 0 AND version_no > 0
        AND status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
        AND content_schema_version = 'V44_PLAYBOOK_CONTENT_V1'
        AND CHAR_LENGTH(TRIM(content_summary)) BETWEEN 2 AND 500
        AND content_hash REGEXP '^sha256:[0-9a-f]{64}$'
        AND created_by_uid > 0
        AND ((status = 'PUBLISHED' AND published_at IS NOT NULL) OR status <> 'PUBLISHED')
        AND ((status = 'RETIRED' AND retired_at IS NOT NULL) OR status <> 'RETIRED')
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook_event (
    id BIGINT NOT NULL,
    playbook_id BIGINT NOT NULL,
    playbook_version_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    operator_uid BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_quality_governance_playbook_event_timeline (playbook_id, id DESC),
    CONSTRAINT chk_quality_governance_playbook_event_shape CHECK (
        id > 0 AND playbook_id > 0 AND operator_uid > 0
        AND CHAR_LENGTH(TRIM(reason)) BETWEEN 2 AND 500
        AND event_type IN ('PLAYBOOK_CREATED', 'VERSION_DRAFTED', 'VERSION_PUBLISHED', 'VERSION_RETIRED')
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook (
    id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    playbook_id BIGINT NOT NULL,
    playbook_version_id BIGINT NOT NULL,
    source_content_hash CHAR(71) NOT NULL,
    snapshot_summary VARCHAR(500) NOT NULL,
    snapshot_content_json JSON NOT NULL,
    snapshot_hash CHAR(71) NOT NULL,
    applicability_result VARCHAR(32) NOT NULL,
    applicability_reason_codes JSON NOT NULL,
    observed_case_version INT NOT NULL,
    observed_governance_fact_version INT NOT NULL,
    observed_governance_snapshot_etag CHAR(71) NOT NULL,
    binding_status VARCHAR(16) NOT NULL,
    completion_evaluation_hash CHAR(71) NULL,
    binding_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_playbook_version (case_id, playbook_version_id),
    KEY idx_quality_review_risk_case_playbook_active (case_id, playbook_id, binding_status),
    CONSTRAINT chk_quality_review_risk_case_playbook_shape CHECK (
        id > 0 AND case_id > 0 AND playbook_id > 0 AND playbook_version_id > 0
        AND source_content_hash REGEXP '^sha256:[0-9a-f]{64}$'
        AND snapshot_hash REGEXP '^sha256:[0-9a-f]{64}$'
        AND CHAR_LENGTH(TRIM(snapshot_summary)) BETWEEN 2 AND 500
        AND applicability_result IN ('MATCHED', 'PARTIAL', 'NOT_MATCHED', 'INSUFFICIENT_CONTEXT')
        AND observed_case_version >= 0 AND observed_governance_fact_version >= 0
        AND observed_governance_snapshot_etag REGEXP '^sha256:[0-9a-f]{64}$'
        AND binding_status IN ('PROPOSED', 'ACCEPTED', 'COMPLETED', 'WAIVED')
        AND binding_version >= 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook_check (
    id BIGINT NOT NULL,
    case_playbook_id BIGINT NOT NULL,
    check_key VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    instruction VARCHAR(500) NOT NULL,
    evidence_requirement VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    observed_fact_version INT NULL,
    observed_snapshot_etag CHAR(71) NULL,
    verified_by_uid BIGINT NULL,
    verification_note VARCHAR(500) NULL,
    waiver_reason VARCHAR(500) NULL,
    check_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_playbook_check (case_playbook_id, check_key),
    CONSTRAINT chk_quality_review_risk_case_playbook_check_shape CHECK (
        id > 0 AND case_playbook_id > 0 AND CHAR_LENGTH(TRIM(check_key)) BETWEEN 2 AND 80
        AND state IN ('PENDING', 'PASSED', 'WAIVED')
        AND evidence_requirement IN ('NONE', 'REFERENCE_REQUIRED')
        AND check_version >= 0
        AND (verified_by_uid IS NULL OR verified_by_uid > 0)
        AND (observed_snapshot_etag IS NULL OR observed_snapshot_etag REGEXP '^sha256:[0-9a-f]{64}$')
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook_check_evidence (
    check_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    created_by_uid BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (check_id, evidence_id),
    KEY idx_quality_review_risk_case_playbook_check_evidence_evidence (evidence_id, check_id),
    CONSTRAINT chk_quality_review_risk_case_playbook_check_evidence_shape CHECK (
        check_id > 0 AND evidence_id > 0 AND created_by_uid > 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook_event (
    id BIGINT NOT NULL,
    case_playbook_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_status VARCHAR(16) NULL,
    status VARCHAR(16) NOT NULL,
    operator_uid BIGINT NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    reason VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_playbook_event_idempotency (case_id, idempotency_key),
    KEY idx_quality_review_risk_case_playbook_event_timeline (case_playbook_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_playbook_event_shape CHECK (
        id > 0 AND case_playbook_id > 0 AND case_id > 0 AND operator_uid > 0
        AND status IN ('PROPOSED', 'ACCEPTED', 'COMPLETED', 'WAIVED')
        AND event_type IN ('BOUND', 'ACCEPTED', 'CHECK_PASSED', 'CHECK_WAIVED', 'COMPLETED', 'WAIVED')
        AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 8 AND 160
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook_command (
    operator_uid BIGINT NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint CHAR(71) NOT NULL,
    result_json JSON NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (operator_uid, idempotency_key),
    CONSTRAINT chk_quality_governance_playbook_command_shape CHECK (
        operator_uid > 0
        AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 8 AND 160
        AND request_fingerprint REGEXP '^sha256:[0-9a-f]{64}$'
    )
);
