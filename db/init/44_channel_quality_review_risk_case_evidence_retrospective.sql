-- V41: channel-quality risk-case evidence, close snapshot, governance facts and retrospective.
ALTER TABLE t_channel_quality_review_risk_case
    ADD COLUMN v41_close_snapshot_id BIGINT NULL AFTER created_by_uid,
    ADD COLUMN legacy_closed_without_snapshot TINYINT NOT NULL DEFAULT 0 AFTER v41_close_snapshot_id;

UPDATE t_channel_quality_review_risk_case
SET legacy_closed_without_snapshot = 1
WHERE status = 'CLOSED';

ALTER TABLE t_channel_quality_review_risk_case
    ADD UNIQUE KEY uk_quality_review_risk_case_v41_close_snapshot (v41_close_snapshot_id),
    ADD KEY idx_quality_review_risk_case_v41_snapshot (v41_close_snapshot_id, id),
    ADD CONSTRAINT chk_quality_review_risk_case_v41_close_snapshot CHECK (
        (
            status <> 'CLOSED'
            AND v41_close_snapshot_id IS NULL
            AND legacy_closed_without_snapshot = 0
        )
        OR (
            status = 'CLOSED'
            AND (
                (v41_close_snapshot_id IS NOT NULL AND legacy_closed_without_snapshot = 0)
                OR (v41_close_snapshot_id IS NULL AND legacy_closed_without_snapshot = 1)
            )
        )
    );

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_governance (
    case_id                         BIGINT        NOT NULL,
    batch_id                        BIGINT        NOT NULL,
    domain                          TINYINT       NOT NULL,
    governance_version              INT           NOT NULL DEFAULT 0,
    current_resolution_revision_id  BIGINT        NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (case_id),
    KEY idx_quality_review_risk_case_governance_domain (domain, update_time, case_id),
    KEY idx_quality_review_risk_case_governance_batch (batch_id, case_id),
    CONSTRAINT chk_quality_review_risk_case_governance_identity CHECK (
        case_id > 0
        AND batch_id > 0
        AND domain BETWEEN 1 AND 5
        AND governance_version >= 0
        AND (current_resolution_revision_id IS NULL OR current_resolution_revision_id > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='V41 channel-quality risk-case governance aggregate';

-- Every pre-V41 case receives an empty governance aggregate. Historical V40
-- notes never become V41 resolutions, evidence, snapshots, or retrospectives.
INSERT INTO t_channel_quality_review_risk_case_governance (
    case_id,
    batch_id,
    domain,
    governance_version,
    current_resolution_revision_id
)
SELECT
    c.id,
    c.batch_id,
    c.domain,
    0,
    NULL
FROM t_channel_quality_review_risk_case c;

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_resolution_revision (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    revision_no                     INT           NOT NULL,
    outcome_type                    VARCHAR(40)   NOT NULL,
    content_recovery_state          VARCHAR(24)   NOT NULL,
    recovery_scope                  VARCHAR(500)  NOT NULL,
    residual_risk_level             VARCHAR(16)   NOT NULL,
    summary                         VARCHAR(1000) NOT NULL,
    created_by_uid                  BIGINT        NOT NULL,
    command_id                      VARCHAR(64)   NOT NULL,
    command_fingerprint             CHAR(64)      NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_resolution_revision_no (case_id, revision_no),
    UNIQUE KEY uk_quality_review_risk_case_resolution_command (case_id, command_id),
    KEY idx_quality_review_risk_case_resolution_timeline (case_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_resolution_identity CHECK (
        id > 0
        AND case_id > 0
        AND revision_no > 0
        AND created_by_uid > 0
        AND CHAR_LENGTH(TRIM(command_id)) BETWEEN 1 AND 64
        AND command_fingerprint REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_quality_review_risk_case_resolution_shape CHECK (
        outcome_type IN (
            'RECOVERY_CONFIRMED', 'PARTIAL_RECOVERY', 'RISK_CONTAINED',
            'FALSE_POSITIVE_CONFIRMED', 'RISK_ACCEPTED'
        )
        AND content_recovery_state IN ('NOT_APPLICABLE', 'NONE', 'PARTIAL', 'FULL')
        AND residual_risk_level IN ('NONE', 'LOW', 'MEDIUM', 'HIGH')
        AND CHAR_LENGTH(TRIM(recovery_scope)) BETWEEN 2 AND 500
        AND CHAR_LENGTH(TRIM(summary)) BETWEEN 2 AND 1000
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Append-only V41 risk-case resolution revisions';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_resolution_root_cause (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    resolution_revision_id          BIGINT        NOT NULL,
    cause_role                      VARCHAR(16)   NOT NULL,
    category                        VARCHAR(40)   NOT NULL,
    sequence_no                     TINYINT       NOT NULL,
    note                            VARCHAR(500)  NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_root_cause_sequence (
        resolution_revision_id, cause_role, sequence_no
    ),
    KEY idx_quality_review_risk_case_root_cause_case (case_id, resolution_revision_id, id),
    CONSTRAINT chk_quality_review_risk_case_root_cause_identity CHECK (
        id > 0
        AND case_id > 0
        AND resolution_revision_id > 0
        AND (
            (cause_role = 'PRIMARY' AND sequence_no = 0)
            OR (cause_role = 'CONTRIBUTING' AND sequence_no BETWEEN 1 AND 5)
        )
        AND category IN (
            'PROCESS_GAP', 'CONTENT_DEFECT', 'CAPACITY_CONSTRAINT', 'POLICY_AMBIGUITY',
            'TOOLING_FAILURE', 'COMMUNICATION_GAP', 'DATA_QUALITY', 'EXTERNAL_DEPENDENCY',
            'UNKNOWN'
        )
        AND CHAR_LENGTH(TRIM(note)) BETWEEN 2 AND 500
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Append-only V41 risk-case resolution root causes';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_action_reference (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    reference_type                  VARCHAR(40)   NOT NULL,
    reference_key                   VARCHAR(160)  NOT NULL,
    observed_version                INT           NULL,
    occurred_at                     DATETIME(3)   NOT NULL,
    summary                         VARCHAR(500)  NOT NULL,
    reference_fingerprint           CHAR(64)      NOT NULL,
    correction_of_reference_id      BIGINT        NULL,
    created_by_uid                  BIGINT        NOT NULL,
    command_id                      VARCHAR(64)   NOT NULL,
    command_fingerprint             CHAR(64)      NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_action_command (case_id, command_id),
    UNIQUE KEY uk_quality_review_risk_case_action_fingerprint (case_id, reference_fingerprint),
    UNIQUE KEY uk_quality_review_risk_case_action_correction (case_id, correction_of_reference_id),
    KEY idx_quality_review_risk_case_action_timeline (case_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_action_identity CHECK (
        id > 0
        AND case_id > 0
        AND created_by_uid > 0
        AND (observed_version IS NULL OR observed_version >= 0)
        AND (correction_of_reference_id IS NULL OR correction_of_reference_id > 0)
        AND CHAR_LENGTH(TRIM(command_id)) BETWEEN 1 AND 64
        AND reference_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND command_fingerprint REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_quality_review_risk_case_action_shape CHECK (
        reference_type IN (
            'V39_COORDINATION_EVENT', 'V40_CASE_EVENT', 'MAINTENANCE_TASK',
            'CONTENT_REVISION', 'GOVERNANCE_DECISION', 'EXTERNAL_TICKET'
        )
        AND CHAR_LENGTH(TRIM(reference_key)) BETWEEN 1 AND 160
        AND CHAR_LENGTH(TRIM(summary)) BETWEEN 2 AND 500
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='V41 controlled references to risk-case actions';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_evidence_entry (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    evidence_type                   VARCHAR(40)   NOT NULL,
    assertion_type                  VARCHAR(40)   NOT NULL,
    subject_type                    VARCHAR(40)   NOT NULL,
    subject_ref                     VARCHAR(160)  NOT NULL,
    source_type                     VARCHAR(40)   NOT NULL,
    source_ref                      VARCHAR(160)  NOT NULL,
    source_version                  INT           NULL,
    observed_at                     DATETIME(3)   NOT NULL,
    summary                         VARCHAR(1000) NOT NULL,
    evidence_digest                 CHAR(64)      NOT NULL,
    correction_of_entry_id          BIGINT        NULL,
    created_by_uid                  BIGINT        NOT NULL,
    command_id                      VARCHAR(64)   NOT NULL,
    command_fingerprint             CHAR(64)      NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_evidence_command (case_id, command_id),
    UNIQUE KEY uk_quality_review_risk_case_evidence_digest (case_id, evidence_digest),
    UNIQUE KEY uk_quality_review_risk_case_evidence_correction (case_id, correction_of_entry_id),
    KEY idx_quality_review_risk_case_evidence_timeline (case_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_evidence_identity CHECK (
        id > 0
        AND case_id > 0
        AND created_by_uid > 0
        AND (source_version IS NULL OR source_version >= 0)
        AND (correction_of_entry_id IS NULL OR correction_of_entry_id > 0)
        AND CHAR_LENGTH(TRIM(command_id)) BETWEEN 1 AND 64
        AND evidence_digest REGEXP '^[0-9a-f]{64}$'
        AND command_fingerprint REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_quality_review_risk_case_evidence_shape CHECK (
        evidence_type IN (
            'CONTENT_STATE_OBSERVATION', 'QUALITY_RECHECK', 'TASK_DELIVERY_RECEIPT',
            'COORDINATION_CONFIRMATION', 'POLICY_DECISION', 'EXTERNAL_CONFIRMATION'
        )
        AND assertion_type IN (
            'SUPPORTS_RECOVERY', 'REFUTES_RECOVERY', 'SUPPORTS_PARTIAL_RECOVERY',
            'SUPPORTS_CONTAINMENT', 'SUPPORTS_FALSE_POSITIVE',
            'SUPPORTS_RISK_ACCEPTANCE', 'UNCERTAIN'
        )
        AND CHAR_LENGTH(TRIM(subject_type)) BETWEEN 1 AND 40
        AND CHAR_LENGTH(TRIM(subject_ref)) BETWEEN 1 AND 160
        AND CHAR_LENGTH(TRIM(source_type)) BETWEEN 1 AND 40
        AND CHAR_LENGTH(TRIM(source_ref)) BETWEEN 1 AND 160
        AND CHAR_LENGTH(TRIM(summary)) BETWEEN 2 AND 1000
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='V41 controlled risk-case evidence without raw private payloads';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_close_snapshot (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    batch_id                        BIGINT        NOT NULL,
    domain                          TINYINT       NOT NULL,
    resolution_revision_id          BIGINT        NOT NULL,
    closed_case_version             INT           NOT NULL,
    closed_coordination_version     INT           NOT NULL,
    closed_governance_version       INT           NOT NULL,
    outcome_type                    VARCHAR(40)   NOT NULL,
    content_recovery_state          VARCHAR(24)   NOT NULL,
    primary_root_cause              VARCHAR(40)   NOT NULL,
    residual_risk_level             VARCHAR(16)   NOT NULL,
    action_reference_count          INT           NOT NULL,
    evidence_count                  INT           NOT NULL,
    payload_schema_version          INT           NOT NULL,
    canonical_payload               JSON          NOT NULL,
    snapshot_digest                 CHAR(64)      NOT NULL,
    closed_by_uid                   BIGINT        NOT NULL,
    command_id                      VARCHAR(64)   NOT NULL,
    command_fingerprint             CHAR(64)      NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_close_snapshot_case (case_id),
    UNIQUE KEY uk_quality_review_risk_case_close_snapshot_command (case_id, command_id),
    UNIQUE KEY uk_quality_review_risk_case_close_snapshot_digest (snapshot_digest),
    KEY idx_quality_review_risk_case_close_snapshot_domain (domain, create_time, id),
    CONSTRAINT chk_quality_review_risk_case_close_snapshot_identity CHECK (
        id > 0
        AND case_id > 0
        AND batch_id > 0
        AND domain BETWEEN 1 AND 5
        AND resolution_revision_id > 0
        AND closed_case_version > 0
        AND closed_coordination_version >= 0
        AND closed_governance_version >= 0
        AND action_reference_count > 0
        AND evidence_count > 0
        AND payload_schema_version = 1
        AND closed_by_uid > 0
        AND CHAR_LENGTH(TRIM(command_id)) BETWEEN 1 AND 64
        AND snapshot_digest REGEXP '^[0-9a-f]{64}$'
        AND command_fingerprint REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_quality_review_risk_case_close_snapshot_shape CHECK (
        outcome_type IN (
            'RECOVERY_CONFIRMED', 'PARTIAL_RECOVERY', 'RISK_CONTAINED',
            'FALSE_POSITIVE_CONFIRMED', 'RISK_ACCEPTED'
        )
        AND content_recovery_state IN ('NOT_APPLICABLE', 'NONE', 'PARTIAL', 'FULL')
        AND residual_risk_level IN ('NONE', 'LOW', 'MEDIUM', 'HIGH')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Immutable V41 risk-case close snapshot';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_close_check (
    id                              BIGINT        NOT NULL,
    snapshot_id                     BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    provider_code                   VARCHAR(64)   NOT NULL,
    provider_version                INT           NOT NULL,
    requirement_level               VARCHAR(16)   NOT NULL,
    result                          VARCHAR(24)   NOT NULL,
    reason_code                     VARCHAR(64)   NOT NULL,
    summary                         VARCHAR(500)  NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_close_check_provider (snapshot_id, provider_code),
    KEY idx_quality_review_risk_case_close_check_case (case_id, id),
    CONSTRAINT chk_quality_review_risk_case_close_check_identity CHECK (
        id > 0
        AND snapshot_id > 0
        AND case_id > 0
        AND provider_version > 0
        AND CHAR_LENGTH(TRIM(provider_code)) BETWEEN 1 AND 64
        AND CHAR_LENGTH(TRIM(reason_code)) BETWEEN 1 AND 64
        AND CHAR_LENGTH(TRIM(summary)) BETWEEN 2 AND 500
    ),
    CONSTRAINT chk_quality_review_risk_case_close_check_shape CHECK (
        requirement_level IN ('BLOCKING', 'ADVISORY')
        AND result IN ('PASS', 'NOT_APPLICABLE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Immutable V41 close-check results; blocking failures are never persisted';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_retrospective (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    close_snapshot_id               BIGINT        NULL,
    domain                          TINYINT       NOT NULL,
    status                          VARCHAR(24)   NOT NULL,
    owner_uid                       BIGINT        NULL,
    retrospective_version           INT           NOT NULL DEFAULT 0,
    legacy_baseline                 TINYINT       NOT NULL DEFAULT 0,
    started_at                      DATETIME(3)   NULL,
    completed_at                    DATETIME(3)   NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_retrospective_case (case_id),
    UNIQUE KEY uk_quality_review_risk_case_retrospective_snapshot (close_snapshot_id),
    KEY idx_quality_review_risk_case_retrospective_workspace (domain, status, update_time, id),
    CONSTRAINT chk_quality_review_risk_case_retrospective_identity CHECK (
        id > 0
        AND case_id > 0
        AND domain BETWEEN 1 AND 5
        AND retrospective_version >= 0
        AND legacy_baseline IN (0, 1)
        AND (close_snapshot_id IS NULL OR close_snapshot_id > 0)
        AND (owner_uid IS NULL OR owner_uid > 0)
    ),
    CONSTRAINT chk_quality_review_risk_case_retrospective_shape CHECK (
        status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')
        AND (
            (legacy_baseline = 0 AND close_snapshot_id IS NOT NULL)
            OR (legacy_baseline = 1 AND close_snapshot_id IS NULL)
        )
        AND (
            (status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL)
            OR (status = 'IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL)
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='V41 risk-case learning retrospective aggregate';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_retrospective_event (
    id                              BIGINT        NOT NULL,
    retrospective_id                BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    operator_uid                    BIGINT        NOT NULL,
    event_type                      VARCHAR(40)   NOT NULL,
    previous_status                 VARCHAR(24)   NULL,
    status                          VARCHAR(24)   NOT NULL,
    owner_uid                       BIGINT        NULL,
    learning_category               VARCHAR(40)   NULL,
    finding_summary                 VARCHAR(1000) NULL,
    prevention_action_summary       VARCHAR(1000) NULL,
    retrospective_version           INT           NOT NULL,
    command_id                      VARCHAR(64)   NOT NULL,
    command_fingerprint             CHAR(64)      NOT NULL,
    note                            VARCHAR(1000) NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_retro_event_command (retrospective_id, command_id),
    KEY idx_quality_review_risk_case_retro_event_timeline (retrospective_id, id DESC),
    KEY idx_quality_review_risk_case_retro_event_case (case_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_retro_event_identity CHECK (
        id > 0
        AND retrospective_id > 0
        AND case_id > 0
        AND operator_uid > 0
        AND retrospective_version > 0
        AND (owner_uid IS NULL OR owner_uid > 0)
        AND CHAR_LENGTH(TRIM(command_id)) BETWEEN 1 AND 64
        AND command_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND CHAR_LENGTH(TRIM(note)) BETWEEN 2 AND 1000
    ),
    CONSTRAINT chk_quality_review_risk_case_retro_event_shape CHECK (
        (
            event_type = 'RETROSPECTIVE_CREATED'
            AND previous_status IS NULL
            AND status = 'PENDING'
            AND owner_uid IS NOT NULL
            AND learning_category IS NULL
            AND finding_summary IS NULL
            AND prevention_action_summary IS NULL
        )
        OR (
            event_type = 'RETROSPECTIVE_OWNER_ASSIGNED'
            AND previous_status IN ('PENDING', 'IN_PROGRESS')
            AND status = previous_status
            AND owner_uid IS NOT NULL
            AND learning_category IS NULL
            AND finding_summary IS NULL
            AND prevention_action_summary IS NULL
        )
        OR (
            event_type = 'RETROSPECTIVE_STARTED'
            AND previous_status = 'PENDING'
            AND status = 'IN_PROGRESS'
            AND owner_uid IS NULL
            AND learning_category IS NULL
            AND finding_summary IS NULL
            AND prevention_action_summary IS NULL
        )
        OR (
            event_type = 'RETROSPECTIVE_FINDING_RECORDED'
            AND previous_status = 'IN_PROGRESS'
            AND status = 'IN_PROGRESS'
            AND owner_uid IS NULL
            AND learning_category IN (
                'PROCESS', 'QUALITY', 'CAPACITY', 'POLICY', 'TOOLING', 'COMMUNICATION',
                'DATA', 'EXTERNAL', 'OTHER'
            )
            AND CHAR_LENGTH(TRIM(finding_summary)) BETWEEN 2 AND 1000
            AND CHAR_LENGTH(TRIM(prevention_action_summary)) BETWEEN 2 AND 1000
        )
        OR (
            event_type = 'RETROSPECTIVE_COMPLETED'
            AND previous_status = 'IN_PROGRESS'
            AND status = 'COMPLETED'
            AND owner_uid IS NULL
            AND learning_category IS NULL
            AND finding_summary IS NULL
            AND prevention_action_summary IS NULL
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Append-only V41 risk-case retrospective history';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_recurrence_link (
    id                              BIGINT        NOT NULL,
    current_case_id                 BIGINT        NOT NULL,
    previous_case_id                BIGINT        NOT NULL,
    domain                          TINYINT       NOT NULL,
    relation_type                   VARCHAR(40)   NOT NULL,
    root_cause_category             VARCHAR(40)   NOT NULL,
    note                            VARCHAR(500)  NOT NULL,
    linked_by_uid                   BIGINT        NOT NULL,
    command_id                      VARCHAR(64)   NOT NULL,
    command_fingerprint             CHAR(64)      NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_recurrence_pair (current_case_id, previous_case_id),
    UNIQUE KEY uk_quality_review_risk_case_recurrence_command (current_case_id, command_id),
    KEY idx_quality_review_risk_case_recurrence_previous (previous_case_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_recurrence_identity CHECK (
        id > 0
        AND current_case_id > 0
        AND previous_case_id > 0
        AND current_case_id <> previous_case_id
        AND domain BETWEEN 1 AND 5
        AND linked_by_uid > 0
        AND CHAR_LENGTH(TRIM(command_id)) BETWEEN 1 AND 64
        AND command_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND CHAR_LENGTH(TRIM(note)) BETWEEN 2 AND 500
    ),
    CONSTRAINT chk_quality_review_risk_case_recurrence_shape CHECK (
        relation_type IN (
            'SAME_ROOT_CAUSE', 'SAME_CHANNEL_PATTERN', 'SAME_BATCH_PATTERN', 'MANUAL_RELATED'
        )
        AND root_cause_category IN (
            'PROCESS_GAP', 'CONTENT_DEFECT', 'CAPACITY_CONSTRAINT', 'POLICY_AMBIGUITY',
            'TOOLING_FAILURE', 'COMMUNICATION_GAP', 'DATA_QUALITY', 'EXTERNAL_DEPENDENCY',
            'UNKNOWN'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='V41 directed links from current cases to earlier closed cases';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_governance_milestone (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    batch_id                        BIGINT        NOT NULL,
    domain                          TINYINT       NOT NULL,
    milestone_code                  VARCHAR(40)   NOT NULL,
    occurred_at                     DATETIME(3)   NOT NULL,
    source_type                     VARCHAR(40)   NOT NULL,
    source_id                       BIGINT        NOT NULL,
    owner_uid                       BIGINT        NULL,
    responsibility_scope            VARCHAR(24)   NOT NULL,
    responsibility_epoch            INT           NULL,
    case_status_after               VARCHAR(32)   NULL,
    required_action                 VARCHAR(24)   NOT NULL,
    retrospective_id                BIGINT        NULL,
    contract_version                VARCHAR(32)   NOT NULL,
    fact_version                    INT           NOT NULL,
    close_snapshot_case_id          BIGINT GENERATED ALWAYS AS (
        CASE WHEN milestone_code = 'CLOSE_SNAPSHOT_GENERATED' THEN case_id ELSE NULL END
    ) STORED,
    retrospective_pending_case_id   BIGINT GENERATED ALWAYS AS (
        CASE WHEN milestone_code = 'RETROSPECTIVE_PENDING' THEN case_id ELSE NULL END
    ) STORED,
    retrospective_completed_case_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN milestone_code = 'RETROSPECTIVE_COMPLETED' THEN case_id ELSE NULL END
    ) STORED,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_milestone_source (
        source_type, source_id, milestone_code
    ),
    UNIQUE KEY uk_quality_review_risk_case_milestone_close_snapshot (close_snapshot_case_id),
    UNIQUE KEY uk_quality_review_risk_case_milestone_retro_pending (retrospective_pending_case_id),
    UNIQUE KEY uk_quality_review_risk_case_milestone_retro_completed (retrospective_completed_case_id),
    KEY idx_quality_review_risk_case_milestone_v42 (domain, milestone_code, occurred_at, id),
    KEY idx_quality_review_risk_case_milestone_owner (case_id, responsibility_scope, responsibility_epoch, id),
    CONSTRAINT chk_quality_review_risk_case_milestone_identity CHECK (
        id > 0
        AND case_id > 0
        AND batch_id > 0
        AND domain BETWEEN 1 AND 5
        AND source_id > 0
        AND fact_version = 1
        AND contract_version = 'V41_FACT_V1'
        AND (owner_uid IS NULL OR owner_uid > 0)
        AND (responsibility_epoch IS NULL OR responsibility_epoch > 0)
        AND (retrospective_id IS NULL OR retrospective_id > 0)
    ),
    CONSTRAINT chk_quality_review_risk_case_milestone_shape CHECK (
        milestone_code IN (
            'OWNER_ASSIGNED', 'OWNER_ACKNOWLEDGED', 'PLAN_RECORDED',
            'CLOSE_SNAPSHOT_GENERATED', 'RETROSPECTIVE_PENDING',
            'RETROSPECTIVE_OWNER_ASSIGNED', 'RETROSPECTIVE_COMPLETED'
        )
        AND source_type IN ('V40_CASE_EVENT', 'V41_CLOSE_SNAPSHOT', 'V41_RETROSPECTIVE')
        AND responsibility_scope IN ('CASE_OWNER', 'RETROSPECTIVE', 'CASE')
        AND required_action IN ('ACKNOWLEDGE_CASE', 'RECORD_PLAN', 'NONE')
        AND (
            (
                milestone_code = 'OWNER_ASSIGNED'
                AND owner_uid IS NOT NULL
                AND responsibility_scope = 'CASE_OWNER'
                AND responsibility_epoch IS NOT NULL
                AND case_status_after IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')
                AND required_action IN ('ACKNOWLEDGE_CASE', 'RECORD_PLAN', 'NONE')
                AND retrospective_id IS NULL
                AND source_type = 'V40_CASE_EVENT'
            )
            OR (
                milestone_code IN ('OWNER_ACKNOWLEDGED', 'PLAN_RECORDED')
                AND owner_uid IS NOT NULL
                AND responsibility_scope = 'CASE_OWNER'
                AND responsibility_epoch IS NOT NULL
                AND (
                    (milestone_code = 'OWNER_ACKNOWLEDGED'
                        AND case_status_after = 'ACKNOWLEDGED')
                    OR (milestone_code = 'PLAN_RECORDED'
                        AND case_status_after = 'IN_PROGRESS')
                )
                AND required_action = 'NONE'
                AND retrospective_id IS NULL
                AND source_type = 'V40_CASE_EVENT'
            )
            OR (
                milestone_code = 'RETROSPECTIVE_PENDING'
                AND owner_uid IS NOT NULL
                AND responsibility_scope = 'RETROSPECTIVE'
                AND responsibility_epoch IS NOT NULL
                AND case_status_after IS NULL
                AND required_action = 'NONE'
                AND retrospective_id IS NOT NULL
                AND source_type = 'V41_RETROSPECTIVE'
            )
            OR (
                milestone_code = 'RETROSPECTIVE_OWNER_ASSIGNED'
                AND owner_uid IS NOT NULL
                AND responsibility_scope = 'RETROSPECTIVE'
                AND responsibility_epoch IS NOT NULL
                AND case_status_after IS NULL
                AND required_action = 'NONE'
                AND retrospective_id IS NOT NULL
                AND source_type = 'V41_RETROSPECTIVE'
            )
            OR (
                milestone_code = 'RETROSPECTIVE_COMPLETED'
                AND owner_uid IS NOT NULL
                AND responsibility_scope = 'RETROSPECTIVE'
                AND responsibility_epoch IS NOT NULL
                AND case_status_after IS NULL
                AND required_action = 'NONE'
                AND retrospective_id IS NOT NULL
                AND source_type = 'V41_RETROSPECTIVE'
            )
            OR (
                milestone_code = 'CLOSE_SNAPSHOT_GENERATED'
                AND owner_uid IS NULL
                AND responsibility_scope = 'CASE'
                AND responsibility_epoch IS NULL
                AND case_status_after IS NULL
                AND required_action = 'NONE'
                AND retrospective_id IS NULL
                AND source_type = 'V41_CLOSE_SNAPSHOT'
            )
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Immutable V41 governance facts exported as V41_FACT_V1';

-- Historical case-owner facts are reconstructed only from V40 lifecycle
-- events. The window order is stable even when multiple events share a time.
WITH valid_v40_governance_events AS (
    SELECT
        e.id,
        e.case_id,
        e.batch_id,
        e.event_type,
        e.status,
        e.owner_uid,
        e.create_time
    FROM t_channel_quality_review_risk_case_event e
    WHERE (
        e.event_type = 'OWNER_ASSIGNED'
        AND e.previous_status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')
        AND e.status = e.previous_status
        AND e.owner_uid IS NOT NULL
    )
    OR (
        e.event_type = 'OWNER_ACKNOWLEDGED'
        AND e.previous_status = 'OPEN'
        AND e.status = 'ACKNOWLEDGED'
        AND e.owner_uid IS NULL
    )
    OR (
        e.event_type = 'PLAN_RECORDED'
        AND e.previous_status = 'ACKNOWLEDGED'
        AND e.status = 'IN_PROGRESS'
        AND e.owner_uid IS NULL
    )
),
ordered_v40_governance_events AS (
    SELECT
        e.id,
        e.case_id,
        e.batch_id,
        e.event_type,
        e.status,
        e.owner_uid,
        e.create_time,
        SUM(
            CASE WHEN e.event_type = 'OWNER_ASSIGNED' THEN 1 ELSE 0 END
        ) OVER (
            PARTITION BY e.case_id
            ORDER BY e.create_time ASC, e.id ASC
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS responsibility_epoch
    FROM valid_v40_governance_events e
)
INSERT INTO t_channel_quality_review_risk_case_governance_milestone (
    id,
    case_id,
    batch_id,
    domain,
    milestone_code,
    occurred_at,
    source_type,
    source_id,
    owner_uid,
    responsibility_scope,
    responsibility_epoch,
    case_status_after,
    required_action,
    retrospective_id,
    contract_version,
    fact_version
)
SELECT
    e.id,
    c.id,
    c.batch_id,
    c.domain,
    e.event_type,
    e.create_time,
    'V40_CASE_EVENT',
    e.id,
    CASE
        WHEN e.event_type = 'OWNER_ASSIGNED' THEN e.owner_uid
        WHEN e.event_type IN ('OWNER_ACKNOWLEDGED', 'PLAN_RECORDED')
            THEN owner_assignment.owner_uid
        ELSE NULL
    END,
    CASE
        WHEN e.event_type IN ('OWNER_ASSIGNED', 'OWNER_ACKNOWLEDGED', 'PLAN_RECORDED')
            THEN 'CASE_OWNER'
        ELSE 'CASE'
    END,
    CASE
        WHEN e.event_type IN ('OWNER_ASSIGNED', 'OWNER_ACKNOWLEDGED', 'PLAN_RECORDED')
            THEN e.responsibility_epoch
        ELSE NULL
    END,
    CASE
        WHEN e.event_type IN ('OWNER_ASSIGNED', 'OWNER_ACKNOWLEDGED', 'PLAN_RECORDED')
            THEN e.status
        ELSE NULL
    END,
    CASE
        WHEN e.event_type = 'OWNER_ASSIGNED' AND e.status = 'OPEN'
            THEN 'ACKNOWLEDGE_CASE'
        WHEN e.event_type = 'OWNER_ASSIGNED' AND e.status = 'ACKNOWLEDGED'
            THEN 'RECORD_PLAN'
        ELSE 'NONE'
    END,
    NULL,
    'V41_FACT_V1',
    1
FROM ordered_v40_governance_events e
INNER JOIN t_channel_quality_review_risk_case c
    ON c.id = e.case_id
    AND c.batch_id = e.batch_id
LEFT JOIN ordered_v40_governance_events owner_assignment
    ON owner_assignment.event_type = 'OWNER_ASSIGNED'
    AND owner_assignment.case_id = e.case_id
    AND owner_assignment.responsibility_epoch = e.responsibility_epoch
WHERE e.event_type = 'OWNER_ASSIGNED'
   OR (
        e.event_type IN ('OWNER_ACKNOWLEDGED', 'PLAN_RECORDED')
        AND e.responsibility_epoch > 0
        AND owner_assignment.owner_uid IS NOT NULL
   );
