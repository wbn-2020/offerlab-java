CREATE TABLE IF NOT EXISTS t_channel_quality_v45_rule_definition (
    id BIGINT NOT NULL,
    rule_code VARCHAR(80) NOT NULL,
    version_no INT NOT NULL,
    ast_schema_version VARCHAR(48) NOT NULL,
    canonical_ast_json JSON NOT NULL,
    ast_hash CHAR(71) NOT NULL,
    action_type VARCHAR(48) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    created_by_uid BIGINT NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_v45_rule_code_version (rule_code, version_no),
    UNIQUE KEY uk_quality_v45_rule_hash (ast_hash),
    CONSTRAINT chk_quality_v45_rule_shape CHECK (
        id > 0 AND CHAR_LENGTH(TRIM(rule_code)) BETWEEN 2 AND 80 AND version_no > 0
        AND ast_schema_version = 'V45_RULE_AST_V1'
        AND ast_hash REGEXP '^sha256:[0-9a-f]{64}$'
        AND action_type IN (
            'RECOMMEND_PLAYBOOK', 'QUEUE_GOVERNANCE_REMINDER',
            'ESCALATE_GOVERNANCE_ATTENTION', 'OPEN_BATCH_COORDINATION'
        )
        AND (
            (action_type IN ('RECOMMEND_PLAYBOOK', 'QUEUE_GOVERNANCE_REMINDER')
                AND risk_level = 'LOW')
            OR (action_type IN ('ESCALATE_GOVERNANCE_ATTENTION', 'OPEN_BATCH_COORDINATION')
                AND risk_level = 'MEDIUM')
        )
        AND enabled IN (0, 1) AND created_by_uid > 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_v45_approval (
    id BIGINT NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    action_type VARCHAR(48) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_by_uid BIGINT NOT NULL,
    decided_by_uid BIGINT NULL,
    decision_reason VARCHAR(500) NULL,
    decided_at DATETIME(3) NULL,
    approval_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_v45_approval_request (request_id),
    CONSTRAINT chk_quality_v45_approval_shape CHECK (
        id > 0 AND CHAR_LENGTH(TRIM(request_id)) BETWEEN 8 AND 96
        AND action_type IN (
            'RECOMMEND_PLAYBOOK', 'QUEUE_GOVERNANCE_REMINDER',
            'ESCALATE_GOVERNANCE_ATTENTION', 'OPEN_BATCH_COORDINATION'
        )
        AND risk_level IN ('LOW', 'MEDIUM')
        AND status IN ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')
        AND requested_by_uid > 0
        AND (decided_by_uid IS NULL OR decided_by_uid > 0)
        AND ((status IN ('APPROVED', 'REJECTED') AND decided_by_uid IS NOT NULL
                AND decided_at IS NOT NULL) OR status NOT IN ('APPROVED', 'REJECTED'))
        AND approval_version >= 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_v45_quota_counter (
    id BIGINT NOT NULL,
    quota_key VARCHAR(160) NOT NULL,
    action_type VARCHAR(48) NOT NULL,
    window_start VARCHAR(32) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    limit_count INT NOT NULL,
    quota_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_v45_quota_window (quota_key, action_type, window_start),
    CONSTRAINT chk_quality_v45_quota_shape CHECK (
        id > 0 AND CHAR_LENGTH(TRIM(quota_key)) BETWEEN 2 AND 160
        AND action_type IN (
            'RECOMMEND_PLAYBOOK', 'QUEUE_GOVERNANCE_REMINDER',
            'ESCALATE_GOVERNANCE_ATTENTION', 'OPEN_BATCH_COORDINATION'
        )
        AND used_count >= 0 AND limit_count > 0 AND used_count <= limit_count
        AND quota_version >= 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_v45_kill_switch (
    id BIGINT NOT NULL,
    switch_key VARCHAR(80) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    reason VARCHAR(500) NOT NULL,
    updated_by_uid BIGINT NOT NULL,
    switch_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_v45_kill_switch_key (switch_key),
    CONSTRAINT chk_quality_v45_kill_switch_shape CHECK (
        id > 0 AND switch_key = 'V45_AUTOMATION'
        AND enabled IN (0, 1)
        AND CHAR_LENGTH(TRIM(reason)) BETWEEN 2 AND 500
        AND updated_by_uid > 0 AND switch_version >= 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_v45_action_ledger (
    id BIGINT NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    action_type VARCHAR(48) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    dry_run TINYINT NOT NULL DEFAULT 0,
    side_effect_created TINYINT NOT NULL DEFAULT 0,
    decision_reason VARCHAR(500) NOT NULL,
    requested_by_uid BIGINT NOT NULL,
    decided_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_v45_ledger_request (request_id),
    UNIQUE KEY uk_quality_v45_ledger_idempotency (idempotency_key),
    KEY idx_quality_v45_ledger_action_time (action_type, decided_at),
    CONSTRAINT chk_quality_v45_ledger_shape CHECK (
        id > 0 AND CHAR_LENGTH(TRIM(request_id)) BETWEEN 8 AND 96
        AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 8 AND 96
        AND action_type IN (
            'RECOMMEND_PLAYBOOK', 'QUEUE_GOVERNANCE_REMINDER',
            'ESCALATE_GOVERNANCE_ATTENTION', 'OPEN_BATCH_COORDINATION'
        )
        AND risk_level IN ('LOW', 'MEDIUM')
        AND decision IN (
            'DRY_RUN', 'PENDING_APPROVAL', 'ACCEPTED', 'REJECTED',
            'BLOCKED_KILL_SWITCH', 'BLOCKED_QUOTA', 'REPLAYED', 'INVALID'
        )
        AND dry_run IN (0, 1) AND side_effect_created IN (0, 1)
        AND (dry_run = 0 OR side_effect_created = 0)
        AND requested_by_uid > 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_v45_outbox (
    id BIGINT NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(96) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_v45_outbox_request (request_id),
    UNIQUE KEY uk_quality_v45_outbox_idempotency (idempotency_key),
    CONSTRAINT chk_quality_v45_outbox_shape CHECK (
        id > 0 AND CHAR_LENGTH(TRIM(request_id)) BETWEEN 8 AND 96
        AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 8 AND 96
        AND CHAR_LENGTH(TRIM(event_type)) BETWEEN 2 AND 80
        AND CHAR_LENGTH(TRIM(aggregate_type)) BETWEEN 2 AND 80
        AND CHAR_LENGTH(TRIM(aggregate_id)) BETWEEN 1 AND 96
        AND status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'FAILED')
        AND attempt_count >= 0
    )
);

CREATE TABLE IF NOT EXISTS t_channel_quality_v45_idempotency (
    requested_by_uid BIGINT NOT NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    request_fingerprint CHAR(71) NOT NULL,
    result_json JSON NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (requested_by_uid, idempotency_key),
    CONSTRAINT chk_quality_v45_idempotency_shape CHECK (
        requested_by_uid > 0
        AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 8 AND 96
        AND request_fingerprint REGEXP '^sha256:[0-9a-f]{64}$'
    )
);
