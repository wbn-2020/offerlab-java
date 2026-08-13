CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook (
    id BIGINT NOT NULL,
    playbook_code VARCHAR(80) NOT NULL,
    latest_version_no INT NOT NULL DEFAULT 0,
    playbook_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_playbook_code (playbook_code)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook_version (
    id BIGINT NOT NULL, playbook_id BIGINT NOT NULL, version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL, content_schema_version VARCHAR(48) NOT NULL,
    content_summary VARCHAR(500) NOT NULL, canonical_content_json JSON NOT NULL,
    content_hash CHAR(71) NOT NULL, published_at DATETIME(3) NULL, retired_at DATETIME(3) NULL,
    created_by_uid BIGINT NOT NULL, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_quality_governance_playbook_version_no (playbook_id, version_no),
    UNIQUE KEY uk_quality_governance_playbook_version_hash (content_hash),
    KEY idx_quality_governance_playbook_version_status (status, playbook_id, version_no)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook_event (
    id BIGINT NOT NULL, playbook_id BIGINT NOT NULL, playbook_version_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL, operator_uid BIGINT NOT NULL, reason VARCHAR(500) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook (
    id BIGINT NOT NULL, case_id BIGINT NOT NULL, playbook_id BIGINT NOT NULL,
    playbook_version_id BIGINT NOT NULL, source_content_hash CHAR(71) NOT NULL,
    snapshot_summary VARCHAR(500) NOT NULL, snapshot_content_json JSON NOT NULL,
    snapshot_hash CHAR(71) NOT NULL, applicability_result VARCHAR(32) NOT NULL,
    applicability_reason_codes JSON NOT NULL, observed_case_version INT NOT NULL,
    observed_governance_fact_version INT NOT NULL, observed_governance_snapshot_etag CHAR(71) NOT NULL,
    binding_status VARCHAR(16) NOT NULL, completion_evaluation_hash CHAR(71) NULL,
    binding_version INT NOT NULL DEFAULT 0, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_quality_review_risk_case_playbook_version (case_id, playbook_version_id)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook_check (
    id BIGINT NOT NULL, case_playbook_id BIGINT NOT NULL, check_key VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL, instruction VARCHAR(500) NOT NULL,
    evidence_requirement VARCHAR(32) NOT NULL, state VARCHAR(16) NOT NULL,
    observed_fact_version INT NULL, observed_snapshot_etag CHAR(71) NULL,
    verified_by_uid BIGINT NULL, verification_note VARCHAR(500) NULL, waiver_reason VARCHAR(500) NULL,
    check_version INT NOT NULL DEFAULT 0, create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_quality_review_risk_case_playbook_check (case_playbook_id, check_key)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook_check_evidence (
    check_id BIGINT NOT NULL, evidence_id BIGINT NOT NULL, created_by_uid BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (check_id, evidence_id),
    KEY idx_quality_review_risk_case_playbook_check_evidence_evidence (evidence_id, check_id)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_playbook_event (
    id BIGINT NOT NULL, case_playbook_id BIGINT NOT NULL, case_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL, previous_status VARCHAR(16) NULL, status VARCHAR(16) NOT NULL,
    operator_uid BIGINT NOT NULL, idempotency_key VARCHAR(160) NOT NULL, reason VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_playbook_event_idempotency (case_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS t_channel_quality_governance_playbook_command (
    operator_uid BIGINT NOT NULL, idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint CHAR(71) NOT NULL, result_json JSON NOT NULL,
    completed_at DATETIME(3) NOT NULL, PRIMARY KEY (operator_uid, idempotency_key)
);
