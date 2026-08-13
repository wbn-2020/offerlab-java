CREATE TABLE t_channel_quality_governance_case_fact (
    case_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    domain TINYINT NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    risk_category VARCHAR(48) NULL,
    case_opened_at DATETIME(3) NOT NULL,
    first_acknowledged_at DATETIME(3) NULL,
    first_planned_at DATETIME(3) NULL,
    resolution_submitted_at DATETIME(3) NULL,
    close_snapshot_id BIGINT NULL,
    closed_at DATETIME(3) NULL,
    root_cause_category VARCHAR(48) NULL,
    resolution_outcome VARCHAR(48) NULL,
    recovery_verification_status VARCHAR(32) NULL,
    v40_case_version INT NOT NULL,
    v40_event_watermark BIGINT NOT NULL,
    v40_stage_hash CHAR(64) NOT NULL,
    v41_governance_fact_version INT NULL,
    v41_snapshot_digest CHAR(64) NULL,
    fact_version INT NOT NULL,
    fact_hash CHAR(64) NOT NULL,
    projection_generation BIGINT NOT NULL,
    projected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_quality_governance_case_fact_snapshot (close_snapshot_id),
    KEY idx_quality_governance_case_fact_opened (domain, case_opened_at, case_id),
    KEY idx_quality_governance_case_fact_closed (domain, closed_at, case_id),
    KEY idx_quality_governance_case_fact_trigger (domain, trigger_type, case_opened_at),
    KEY idx_quality_governance_case_fact_risk (domain, risk_category, case_opened_at),
    KEY idx_quality_governance_case_fact_root_cause (domain, root_cause_category, closed_at),
    CONSTRAINT chk_quality_governance_case_fact_shape CHECK (
        batch_id > 0
        AND domain BETWEEN 1 AND 5
        AND trigger_type IN ('RISK_EVENT', 'DUE_STATE')
        AND v40_case_version >= 0
        AND v40_event_watermark >= 0
        AND CHAR_LENGTH(v40_stage_hash) = 64
        AND (v41_governance_fact_version IS NULL OR v41_governance_fact_version >= 0)
        AND fact_version >= 1
        AND CHAR_LENGTH(fact_hash) = 64
        AND projection_generation > 0
        AND (first_acknowledged_at IS NULL OR first_acknowledged_at >= case_opened_at)
        AND (first_planned_at IS NULL OR first_planned_at >= case_opened_at)
        AND (resolution_submitted_at IS NULL OR resolution_submitted_at >= case_opened_at)
        AND (
            (close_snapshot_id IS NULL AND closed_at IS NULL
                AND v41_governance_fact_version IS NULL AND v41_snapshot_digest IS NULL)
            OR (close_snapshot_id IS NOT NULL AND close_snapshot_id > 0 AND closed_at IS NOT NULL
                AND v41_governance_fact_version IS NOT NULL
                AND CHAR_LENGTH(v41_snapshot_digest) = 64
                AND closed_at >= case_opened_at
                AND (resolution_submitted_at IS NULL OR closed_at >= resolution_submitted_at))
        )
    )
);

CREATE TABLE t_channel_quality_governance_recurrence_fact (
    parent_case_id BIGINT NOT NULL,
    child_case_id BIGINT NOT NULL,
    parent_closed_at DATETIME(3) NOT NULL,
    child_opened_at DATETIME(3) NOT NULL,
    linked_at DATETIME(3) NOT NULL,
    source_version INT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    projection_generation BIGINT NOT NULL,
    PRIMARY KEY (parent_case_id, child_case_id),
    KEY idx_quality_governance_recurrence_parent (parent_closed_at, parent_case_id),
    KEY idx_quality_governance_recurrence_child (child_opened_at, child_case_id),
    KEY idx_quality_governance_recurrence_linked (linked_at, parent_case_id),
    CONSTRAINT chk_quality_governance_recurrence_fact_shape CHECK (
        parent_case_id > 0 AND child_case_id > 0 AND parent_case_id <> child_case_id
        AND child_opened_at > parent_closed_at AND linked_at >= parent_closed_at
        AND source_version >= 0 AND CHAR_LENGTH(source_hash) = 64 AND projection_generation > 0
    )
);

CREATE TABLE t_channel_quality_governance_review_fact (
    task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    batch_id BIGINT NOT NULL,
    domain TINYINT NOT NULL,
    reviewed_at DATETIME(3) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    source_version INT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    projection_generation BIGINT NOT NULL,
    PRIMARY KEY (task_id, attempt_no),
    KEY idx_quality_governance_review_fact_time (domain, reviewed_at, task_id),
    KEY idx_quality_governance_review_fact_decision (domain, decision, reviewed_at),
    CONSTRAINT chk_quality_governance_review_fact_shape CHECK (
        task_id > 0 AND attempt_no > 0 AND batch_id > 0 AND domain BETWEEN 1 AND 5
        AND decision IN ('APPROVED', 'REJECTED', 'CLOSED')
        AND source_version >= 0 AND CHAR_LENGTH(source_hash) = 64 AND projection_generation > 0
    )
);

CREATE TABLE t_channel_quality_governance_batch_fact (
    batch_id BIGINT NOT NULL,
    domain TINYINT NOT NULL,
    batch_created_at DATETIME(3) NOT NULL,
    dispatched_task_count INT NOT NULL,
    withdrawn_task_count INT NOT NULL DEFAULT 0,
    withdraw_event_id BIGINT NULL,
    withdrawn_at DATETIME(3) NULL,
    source_version INT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    projection_generation BIGINT NOT NULL,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_quality_governance_batch_fact_withdraw_event (withdraw_event_id),
    KEY idx_quality_governance_batch_fact_created (domain, batch_created_at, batch_id),
    KEY idx_quality_governance_batch_fact_withdrawn (domain, withdrawn_at, batch_id),
    CONSTRAINT chk_quality_governance_batch_fact_shape CHECK (
        domain BETWEEN 1 AND 5 AND dispatched_task_count BETWEEN 1 AND 20
        AND withdrawn_task_count BETWEEN 0 AND dispatched_task_count
        AND (
            (withdraw_event_id IS NULL AND withdrawn_at IS NULL)
            OR (withdraw_event_id IS NOT NULL AND withdraw_event_id > 0
                AND withdrawn_at IS NOT NULL AND withdrawn_at >= batch_created_at)
        )
        AND source_version >= 0 AND CHAR_LENGTH(source_hash) = 64 AND projection_generation > 0
    )
);

CREATE TABLE t_channel_quality_governance_todo_fact (
    todo_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    domain TINYINT NOT NULL,
    todo_type VARCHAR(32) NOT NULL,
    responsibility_epoch INT NOT NULL,
    anchor_at DATETIME(3) NOT NULL,
    effective_deadline_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    terminal_status VARCHAR(16) NOT NULL,
    terminal_reason VARCHAR(40) NULL,
    successful_reminder_count INT NOT NULL DEFAULT 0,
    first_successful_reminder_at DATETIME(3) NULL,
    source_version INT NOT NULL,
    source_hash CHAR(64) NOT NULL,
    projection_generation BIGINT NOT NULL,
    projected_at DATETIME(3) NOT NULL,
    PRIMARY KEY (todo_id),
    KEY idx_quality_governance_todo_fact_due (domain, effective_deadline_at, todo_id),
    KEY idx_quality_governance_todo_fact_type (domain, todo_type, effective_deadline_at),
    CONSTRAINT chk_quality_governance_todo_fact_shape CHECK (
        case_id > 0 AND domain BETWEEN 1 AND 5 AND responsibility_epoch > 0
        AND todo_type IN ('ACKNOWLEDGE_CASE', 'RECORD_PLAN', 'COMPLETE_RETROSPECTIVE')
        AND effective_deadline_at > anchor_at
        AND terminal_status IN ('OPEN', 'COMPLETED', 'INVALIDATED')
        AND successful_reminder_count >= 0
        AND (first_successful_reminder_at IS NULL OR first_successful_reminder_at >= anchor_at)
        AND (
            (terminal_status = 'OPEN' AND completed_at IS NULL AND terminal_reason IS NULL)
            OR (terminal_status = 'COMPLETED' AND completed_at IS NOT NULL
                AND terminal_reason IN ('OWNER_ACKNOWLEDGED', 'PLAN_RECORDED', 'RETROSPECTIVE_COMPLETED'))
            OR (terminal_status = 'INVALIDATED' AND completed_at IS NULL
                AND terminal_reason IN ('ASSIGNEE_CHANGED', 'SOURCE_TERMINATED', 'RECONCILIATION_OBSOLETE'))
        )
        AND source_version >= 0 AND CHAR_LENGTH(source_hash) = 64 AND projection_generation > 0
    )
);

CREATE TABLE t_channel_quality_governance_metric_daily (
    projection_generation BIGINT NOT NULL,
    metric_code VARCHAR(48) NOT NULL,
    bucket_date DATE NOT NULL,
    domain TINYINT NOT NULL,
    dimension_type VARCHAR(40) NOT NULL,
    dimension_value VARCHAR(64) NOT NULL,
    numerator BIGINT NULL,
    denominator BIGINT NULL,
    sample_count BIGINT NOT NULL,
    value_sum_seconds BIGINT NULL,
    availability_status VARCHAR(16) NOT NULL,
    definition_version VARCHAR(16) NOT NULL,
    PRIMARY KEY (projection_generation, metric_code, bucket_date, domain, dimension_type, dimension_value),
    KEY idx_quality_governance_metric_daily_query (metric_code, bucket_date, domain),
    CONSTRAINT chk_quality_governance_metric_daily_shape CHECK (
        projection_generation > 0
        AND metric_code IN (
            'CASE_OPENED_COUNT', 'CASE_CLOSED_COUNT', 'GOVERNANCE_OVERDUE_RATE',
            'REMINDER_COVERAGE_RATE', 'POST_REMINDER_COMPLETION_RATE',
            'TASK_REWORK_RATE', 'BATCH_WITHDRAW_RATE', 'RISK_RECURRENCE_RATE'
        )
        AND domain BETWEEN 0 AND 5
        AND dimension_type IN (
            'NONE', 'TRIGGER_TYPE', 'RISK_CATEGORY', 'ROOT_CAUSE_CATEGORY',
            'RESOLUTION_OUTCOME', 'RECOVERY_VERIFICATION_STATUS'
        )
        AND CHAR_LENGTH(TRIM(dimension_value)) > 0
        AND (numerator IS NULL OR numerator >= 0)
        AND (denominator IS NULL OR denominator >= 0)
        AND sample_count >= 0
        AND (value_sum_seconds IS NULL OR value_sum_seconds >= 0)
        AND availability_status IN ('AVAILABLE', 'UNAVAILABLE', 'SUPPRESSED')
        AND definition_version = 'V43_1'
    )
);

CREATE TABLE t_channel_quality_governance_projection_cursor (
    source_type VARCHAR(32) NOT NULL,
    cursor_time DATETIME(3) NULL,
    cursor_id BIGINT NULL,
    covered_through DATETIME(3) NULL,
    backlog_count BIGINT NOT NULL DEFAULT 0,
    last_success_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    projection_generation BIGINT NOT NULL,
    cursor_version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (source_type),
    CONSTRAINT chk_quality_governance_projection_cursor_shape CHECK (
        source_type IN (
            'V38_BATCH', 'V38_ATTEMPT', 'V39_EVENT', 'V40_CASE_EVENT',
            'V41_CLOSE_SNAPSHOT', 'V41_RECURRENCE', 'V42_TODO', 'V42_REMINDER'
        )
        AND ((cursor_time IS NULL AND cursor_id IS NULL) OR (cursor_time IS NOT NULL AND cursor_id IS NOT NULL))
        AND backlog_count >= 0 AND projection_generation >= 0 AND cursor_version >= 0
    )
);

CREATE TABLE t_channel_quality_governance_dirty_bucket (
    id BIGINT NOT NULL,
    metric_family VARCHAR(32) NOT NULL,
    domain TINYINT NOT NULL,
    bucket_date DATE NOT NULL,
    first_source_type VARCHAR(32) NOT NULL,
    first_source_fact_id BIGINT NOT NULL,
    last_source_type VARCHAR(32) NOT NULL,
    last_source_fact_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_dirty_bucket (metric_family, domain, bucket_date),
    KEY idx_quality_governance_dirty_bucket_status (status, bucket_date, id),
    CONSTRAINT chk_quality_governance_dirty_bucket_shape CHECK (
        metric_family IN ('FUNNEL', 'DURATION', 'OVERDUE', 'REMINDER', 'REWORK', 'WITHDRAW', 'RECURRENCE')
        AND domain BETWEEN 0 AND 5 AND first_source_fact_id > 0 AND last_source_fact_id > 0
        AND status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED') AND attempt_count >= 0
    )
);

CREATE TABLE t_channel_quality_governance_projection_generation (
    id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    scope_from DATETIME(3) NULL,
    scope_to DATETIME(3) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    validation_digest CHAR(64) NULL,
    is_active TINYINT NOT NULL DEFAULT 0,
    active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN is_active = 1 THEN 1 ELSE NULL END) STORED,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_projection_generation_active (active_marker),
    KEY idx_quality_governance_projection_generation_status (status, create_time, id),
    CONSTRAINT chk_quality_governance_projection_generation_shape CHECK (
        status IN ('BUILDING', 'READY', 'REBUILDING', 'FAILED', 'COMPLETED')
        AND is_active IN (0, 1)
        AND ((scope_from IS NULL AND scope_to IS NULL) OR (scope_from IS NOT NULL AND scope_to IS NOT NULL AND scope_to > scope_from))
        AND ((started_at IS NULL AND completed_at IS NULL) OR (started_at IS NOT NULL AND (completed_at IS NULL OR completed_at >= started_at)))
        AND (validation_digest IS NULL OR CHAR_LENGTH(validation_digest) = 64)
        AND (is_active = 0 OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND validation_digest IS NOT NULL))
    )
);

CREATE TABLE t_channel_quality_governance_rebuild_request (
    id BIGINT NOT NULL,
    operator_uid BIGINT NOT NULL,
    idempotency_key VARCHAR(96) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    dry_run TINYINT NOT NULL,
    scope_from DATETIME(3) NULL,
    scope_to DATETIME(3) NULL,
    result_summary VARCHAR(512) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_rebuild_idempotency (operator_uid, idempotency_key),
    CONSTRAINT chk_quality_governance_rebuild_request_shape CHECK (
        operator_uid > 0 AND CHAR_LENGTH(TRIM(idempotency_key)) BETWEEN 1 AND 96
        AND CHAR_LENGTH(request_fingerprint) = 64
        AND status IN ('REQUESTED', 'DRY_RUN_READY', 'RUNNING', 'VALIDATING', 'COMPLETED', 'FAILED')
        AND dry_run IN (0, 1)
        AND ((scope_from IS NULL AND scope_to IS NULL) OR (scope_from IS NOT NULL AND scope_to IS NOT NULL AND scope_to > scope_from))
    )
);

CREATE TABLE t_channel_quality_governance_projection_issue (
    id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_fact_key CHAR(64) NOT NULL,
    issue_type VARCHAR(48) NOT NULL,
    detected_at DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    projection_generation BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_projection_issue (source_type, source_fact_key, issue_type),
    KEY idx_quality_governance_projection_issue_status (status, detected_at, id),
    CONSTRAINT chk_quality_governance_projection_issue_shape CHECK (
        source_type IN (
            'V38_BATCH', 'V38_ATTEMPT', 'V39_EVENT', 'V40_CASE_EVENT',
            'V41_CLOSE_SNAPSHOT', 'V41_RECURRENCE', 'V42_TODO', 'V42_REMINDER'
        )
        AND CHAR_LENGTH(source_fact_key) = 64
        AND issue_type IN (
            'SOURCE_IMMUTABILITY_VIOLATION', 'INVALID_SEQUENCE', 'ORPHAN_REFERENCE',
            'CONTRACT_UNAVAILABLE', 'LOW_VERSION_IGNORED'
        )
        AND status IN ('OPEN', 'RESOLVED', 'IGNORED') AND projection_generation >= 0
    )
);
