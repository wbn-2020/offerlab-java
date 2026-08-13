CREATE TABLE t_channel_quality_governance_todo (
    id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    retrospective_id BIGINT NULL,
    domain TINYINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    source_scope VARCHAR(24) NOT NULL,
    responsibility_epoch INT NOT NULL,
    source_fact_id BIGINT NOT NULL,
    assignee_uid BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    anchor_at DATETIME(3) NOT NULL,
    due_at DATETIME(3) NOT NULL,
    policy_source VARCHAR(24) NOT NULL,
    policy_key VARCHAR(48) NOT NULL,
    policy_version VARCHAR(16) NOT NULL,
    sla_minutes INT NOT NULL,
    completion_fact_id BIGINT NULL,
    completion_reason VARCHAR(32) NULL,
    completed_at DATETIME(3) NULL,
    close_reason VARCHAR(32) NULL,
    closed_at DATETIME(3) NULL,
    escalation_level VARCHAR(32) NOT NULL DEFAULT 'NONE',
    last_escalation_event_id BIGINT NULL,
    escalated_at DATETIME(3) NULL,
    todo_version INT NOT NULL DEFAULT 0,
    active_marker TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN 1 ELSE NULL END) STORED,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_todo_source (source_fact_id, task_type),
    UNIQUE KEY uk_quality_governance_todo_active (case_id, task_type, active_marker),
    KEY idx_quality_governance_todo_assignee (assignee_uid, status, due_at, id),
    KEY idx_quality_governance_todo_domain (domain, status, due_at, id),
    KEY idx_quality_governance_todo_escalation (domain, escalation_level, status, due_at, id),
    KEY idx_quality_governance_todo_case (case_id, create_time, id),
    KEY idx_quality_governance_todo_due (status, due_at, id),
    CONSTRAINT chk_quality_governance_todo_shape CHECK (
        domain BETWEEN 1 AND 5
        AND task_type IN ('ACKNOWLEDGE_CASE', 'RECORD_PLAN', 'COMPLETE_RETROSPECTIVE')
        AND source_scope IN ('CASE_OWNER', 'RETROSPECTIVE')
        AND responsibility_epoch > 0
        AND source_fact_id > 0
        AND assignee_uid > 0
        AND status IN ('OPEN', 'COMPLETED', 'CLOSED')
        AND due_at > anchor_at
        AND policy_source = 'SYSTEM_DEFAULT'
        AND policy_key IN ('ACK_CASE_4H', 'RECORD_PLAN_24H', 'COMPLETE_RETROSPECTIVE_72H')
        AND policy_version = 'V42_1'
        AND sla_minutes > 0
        AND escalation_level IN ('NONE', 'CHANNEL_ATTENTION', 'GOVERNANCE_ATTENTION')
        AND todo_version >= 0
        AND (
            (task_type = 'COMPLETE_RETROSPECTIVE' AND source_scope = 'RETROSPECTIVE' AND retrospective_id IS NOT NULL)
            OR (task_type IN ('ACKNOWLEDGE_CASE', 'RECORD_PLAN') AND source_scope = 'CASE_OWNER' AND retrospective_id IS NULL)
        )
        AND (
            (status = 'OPEN' AND completion_fact_id IS NULL AND completion_reason IS NULL
                AND completed_at IS NULL AND close_reason IS NULL AND closed_at IS NULL)
            OR (status = 'COMPLETED' AND completion_fact_id IS NOT NULL
                AND completion_reason IN ('OWNER_ACKNOWLEDGED', 'PLAN_RECORDED', 'RETROSPECTIVE_COMPLETED')
                AND completed_at IS NOT NULL AND close_reason IS NULL AND closed_at IS NOT NULL)
            OR (status = 'CLOSED' AND completion_fact_id IS NULL AND completion_reason IS NULL
                AND completed_at IS NULL
                AND close_reason IN ('ASSIGNEE_CHANGED', 'SOURCE_TERMINATED', 'RECONCILIATION_OBSOLETE')
                AND closed_at IS NOT NULL)
        )
    )
);

CREATE TABLE t_channel_quality_governance_todo_event (
    id BIGINT NOT NULL,
    todo_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    previous_status VARCHAR(16) NULL,
    status VARCHAR(16) NOT NULL,
    assignee_uid BIGINT NOT NULL,
    responsibility_epoch INT NOT NULL,
    source_fact_id BIGINT NOT NULL,
    reason VARCHAR(40) NULL,
    previous_escalation_level VARCHAR(32) NULL,
    escalation_level VARCHAR(32) NULL,
    todo_version INT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_todo_event_source (source_fact_id, event_type, todo_id),
    KEY idx_quality_governance_todo_event_todo (todo_id, id DESC),
    KEY idx_quality_governance_todo_event_case (case_id, id DESC),
    CONSTRAINT chk_quality_governance_todo_event_shape CHECK (
        event_type IN ('TODO_CREATED', 'TODO_COMPLETED', 'TODO_CLOSED', 'TODO_ESCALATED')
        AND status IN ('OPEN', 'COMPLETED', 'CLOSED')
        AND assignee_uid > 0 AND responsibility_epoch > 0 AND source_fact_id > 0 AND todo_version >= 0
    )
);

CREATE TABLE t_channel_quality_governance_reminder_intent (
    id BIGINT NOT NULL,
    todo_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    assignee_uid BIGINT NOT NULL,
    reminder_kind VARCHAR(16) NOT NULL,
    sequence_no INT NOT NULL,
    schedule_version VARCHAR(16) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    deliver_before DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    disposition VARCHAR(48) NULL,
    next_attempt_at DATETIME(3) NULL,
    notification_dedup_key VARCHAR(160) NOT NULL,
    intent_version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_reminder_stage (todo_id, schedule_version, reminder_kind, sequence_no),
    UNIQUE KEY uk_quality_governance_reminder_dedup (notification_dedup_key),
    KEY idx_quality_governance_reminder_due (status, next_attempt_at, scheduled_at, id),
    KEY idx_quality_governance_reminder_assignee (assignee_uid, create_time, id),
    KEY idx_quality_governance_reminder_todo (todo_id, id DESC),
    CONSTRAINT chk_quality_governance_reminder_shape CHECK (
        assignee_uid > 0 AND sequence_no > 0 AND schedule_version = 'V42_1'
        AND reminder_kind IN ('DUE_SOON', 'DUE', 'OVERDUE')
        AND status IN ('PLANNED', 'OUTBOXED', 'DEFERRED', 'DELIVERED', 'SUPPRESSED', 'FAILED', 'CANCELLED')
        AND deliver_before > scheduled_at AND intent_version >= 0
    )
);

CREATE TABLE t_channel_quality_governance_reminder_attempt (
    id BIGINT NOT NULL,
    reminder_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    request_event_key VARCHAR(160) NOT NULL,
    outcome VARCHAR(48) NOT NULL,
    policy_decision VARCHAR(16) NOT NULL,
    retry_after DATETIME(3) NULL,
    notification_id BIGINT NULL,
    error_code VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_governance_reminder_attempt_number (reminder_id, attempt_no),
    UNIQUE KEY uk_quality_governance_reminder_attempt_event_outcome (request_event_key, outcome),
    KEY idx_quality_governance_reminder_attempt_reminder (reminder_id, id DESC),
    KEY idx_quality_governance_reminder_attempt_outcome (outcome, occurred_at, id),
    CONSTRAINT chk_quality_governance_reminder_attempt_shape CHECK (
        reminder_id > 0
        AND attempt_no > 0
        AND CHAR_LENGTH(TRIM(request_event_key)) > 0
        AND outcome IN (
            'DELIVERED_NEW',
            'DELIVERED_EXISTING',
            'DEFERRED_QUIET_HOURS',
            'SUPPRESSED_GLOBAL_SYSTEM_DISABLED',
            'SUPPRESSED_GOVERNANCE_DISABLED',
            'SUPPRESSED_RECIPIENT_INELIGIBLE',
            'SUPPRESSED_TODO_TERMINAL',
            'SUPPRESSED_STALE_REQUEST',
            'POLICY_UNAVAILABLE',
            'RETRY_PENDING',
            'FAILED_PERMANENT',
            'SKIPPED_LATE_MATERIALIZATION',
            'SUPERSEDED_BY_LATER_STAGE'
        )
        AND policy_decision IN ('ALLOW', 'DENY', 'DEFER', 'UNAVAILABLE', 'NOT_EVALUATED')
        AND (retry_after IS NULL OR retry_after >= occurred_at)
        AND (notification_id IS NULL OR notification_id > 0)
        AND (error_code IS NULL OR CHAR_LENGTH(TRIM(error_code)) > 0)
    )
);

CREATE TABLE t_channel_quality_governance_todo_checkpoint (
    case_id BIGINT NOT NULL,
    domain TINYINT NOT NULL,
    last_source_fact_id BIGINT NOT NULL,
    last_source_occurred_at DATETIME(3) NOT NULL,
    source_contract_version VARCHAR(24) NOT NULL,
    projection_version VARCHAR(16) NOT NULL,
    health_status VARCHAR(16) NOT NULL,
    checkpoint_version INT NOT NULL DEFAULT 0,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (case_id),
    KEY idx_quality_governance_checkpoint_health (health_status, update_time),
    CONSTRAINT chk_quality_governance_checkpoint_shape CHECK (
        domain BETWEEN 1 AND 5 AND last_source_fact_id > 0
        AND source_contract_version = 'V41_FACT_V1' AND projection_version = 'V42_1'
        AND health_status IN ('READY', 'STALE', 'BLOCKED') AND checkpoint_version >= 0
    )
);

DELIMITER $$

DROP PROCEDURE IF EXISTS v20260807_governance_todo_add_column_if_missing $$
CREATE PROCEDURE v20260807_governance_todo_add_column_if_missing(
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

DROP PROCEDURE IF EXISTS v20260807_governance_todo_add_check_if_missing $$
CREATE PROCEDURE v20260807_governance_todo_add_check_if_missing(
    IN p_table VARCHAR(64),
    IN p_constraint VARCHAR(64),
    IN p_sql TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND CONSTRAINT_NAME = p_constraint
          AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260807_governance_todo_add_column_if_missing('t_user_privacy_setting', 'governance_reminder_notification',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN governance_reminder_notification TINYINT NOT NULL DEFAULT 1 AFTER contact_request_daily_limit');

CALL v20260807_governance_todo_add_column_if_missing('t_user_privacy_setting', 'governance_reminder_quiet_start_minute',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN governance_reminder_quiet_start_minute INT NULL AFTER governance_reminder_notification');

CALL v20260807_governance_todo_add_column_if_missing('t_user_privacy_setting', 'governance_reminder_quiet_end_minute',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN governance_reminder_quiet_end_minute INT NULL AFTER governance_reminder_quiet_start_minute');

CALL v20260807_governance_todo_add_column_if_missing('t_user_privacy_setting', 'governance_reminder_time_zone',
    'ALTER TABLE t_user_privacy_setting ADD COLUMN governance_reminder_time_zone VARCHAR(64) NULL AFTER governance_reminder_quiet_end_minute');

CALL v20260807_governance_todo_add_check_if_missing('t_user_privacy_setting',
    'chk_user_privacy_governance_reminder_quiet_window',
    'ALTER TABLE t_user_privacy_setting ADD CONSTRAINT chk_user_privacy_governance_reminder_quiet_window CHECK ((governance_reminder_notification IN (0, 1)) AND ((governance_reminder_quiet_start_minute IS NULL AND governance_reminder_quiet_end_minute IS NULL AND governance_reminder_time_zone IS NULL) OR (governance_reminder_quiet_start_minute BETWEEN 0 AND 1439 AND governance_reminder_quiet_end_minute BETWEEN 0 AND 1439 AND CHAR_LENGTH(TRIM(governance_reminder_time_zone)) BETWEEN 1 AND 64)))');

DROP PROCEDURE IF EXISTS v20260807_governance_todo_add_check_if_missing;
DROP PROCEDURE IF EXISTS v20260807_governance_todo_add_column_if_missing;
