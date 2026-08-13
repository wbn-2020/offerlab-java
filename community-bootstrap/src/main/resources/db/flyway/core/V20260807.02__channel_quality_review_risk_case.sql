-- V40: channel-quality risk-case workbench and immutable recovery lifecycle events.
CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case (
    id                              BIGINT        NOT NULL,
    batch_id                        BIGINT        NOT NULL,
    domain                          TINYINT       NOT NULL,
    trigger_type                    VARCHAR(32)   NOT NULL,
    risk_event_id                   BIGINT        NULL,
    risk_code                       VARCHAR(32)   NULL,
    due_state                       VARCHAR(32)   NULL,
    status                          VARCHAR(32)   NOT NULL,
    owner_uid                       BIGINT        NULL,
    case_version                    INT           NOT NULL DEFAULT 0,
    opened_coordination_version     INT           NOT NULL,
    created_by_uid                  BIGINT        NOT NULL,
    active_batch_id                 BIGINT GENERATED ALWAYS AS (
        CASE WHEN status <> 'CLOSED' THEN batch_id ELSE NULL END
    ) STORED,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quality_review_risk_case_event (risk_event_id),
    UNIQUE KEY uk_quality_review_risk_case_active_batch (active_batch_id),
    KEY idx_quality_review_risk_case_domain_queue (domain, status, update_time, id),
    KEY idx_quality_review_risk_case_batch (batch_id, update_time, id),
    CONSTRAINT chk_quality_review_risk_case_domain CHECK (
        domain BETWEEN 1 AND 5
    ),
    CONSTRAINT chk_quality_review_risk_case_identity CHECK (
        id > 0
        AND batch_id > 0
        AND created_by_uid > 0
        AND case_version >= 0
        AND opened_coordination_version >= 0
        AND (risk_event_id IS NULL OR risk_event_id > 0)
        AND (owner_uid IS NULL OR owner_uid > 0)
    ),
    CONSTRAINT chk_quality_review_risk_case_trigger CHECK (
        (
            trigger_type = 'RISK_EVENT'
            AND risk_event_id IS NOT NULL
            AND risk_code IN (
                'BLOCKER', 'CAPACITY_RISK', 'REVIEW_DELAY', 'OVERDUE_ESCALATION'
            )
            AND due_state IS NULL
        )
        OR (
            trigger_type = 'DUE_STATE'
            AND risk_event_id IS NULL
            AND risk_code IS NULL
            AND due_state IN ('DUE_SOON', 'OVERDUE')
        )
    ),
    CONSTRAINT chk_quality_review_risk_case_status CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT chk_quality_review_risk_case_owner_status CHECK (
        status = 'OPEN'
        OR owner_uid IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Channel-quality risk-case coordination and recovery state';

CREATE TABLE IF NOT EXISTS t_channel_quality_review_risk_case_event (
    id                              BIGINT        NOT NULL,
    case_id                         BIGINT        NOT NULL,
    batch_id                        BIGINT        NOT NULL,
    operator_uid                    BIGINT        NOT NULL,
    event_type                      VARCHAR(40)   NOT NULL,
    previous_status                 VARCHAR(32)   NULL,
    status                          VARCHAR(32)   NOT NULL,
    owner_uid                       BIGINT        NULL,
    observed_coordination_version   INT           NOT NULL,
    case_version                    INT           NOT NULL,
    note                            VARCHAR(500)  NOT NULL,
    create_time                     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_quality_review_risk_case_event_timeline (case_id, id DESC),
    KEY idx_quality_review_risk_case_event_batch (batch_id, id DESC),
    CONSTRAINT chk_quality_review_risk_case_event_identity CHECK (
        id > 0
        AND case_id > 0
        AND batch_id > 0
        AND operator_uid > 0
        AND observed_coordination_version >= 0
        AND case_version > 0
        AND (owner_uid IS NULL OR owner_uid > 0)
    ),
    CONSTRAINT chk_quality_review_risk_case_event_note CHECK (
        CHAR_LENGTH(TRIM(note)) BETWEEN 2 AND 500
    ),
    CONSTRAINT chk_quality_review_risk_case_event_shape CHECK (
        (
            event_type = 'CASE_OPENED'
            AND previous_status IS NULL
            AND status = 'OPEN'
            AND owner_uid IS NULL
            AND case_version = 1
        )
        OR (
            event_type = 'OWNER_ASSIGNED'
            AND previous_status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS')
            AND status = previous_status
            AND owner_uid IS NOT NULL
        )
        OR (
            event_type = 'OWNER_ACKNOWLEDGED'
            AND previous_status = 'OPEN'
            AND status = 'ACKNOWLEDGED'
            AND owner_uid IS NULL
        )
        OR (
            event_type = 'PLAN_RECORDED'
            AND previous_status = 'ACKNOWLEDGED'
            AND status = 'IN_PROGRESS'
            AND owner_uid IS NULL
        )
        OR (
            event_type = 'PROGRESS_RECORDED'
            AND previous_status = 'IN_PROGRESS'
            AND status = 'IN_PROGRESS'
            AND owner_uid IS NULL
        )
        OR (
            event_type = 'RESOLUTION_SUBMITTED'
            AND previous_status = 'IN_PROGRESS'
            AND status = 'RESOLVED'
            AND owner_uid IS NULL
        )
        OR (
            event_type = 'CASE_CLOSED'
            AND previous_status = 'RESOLVED'
            AND status = 'CLOSED'
            AND owner_uid IS NULL
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Immutable channel-quality risk-case lifecycle history';
