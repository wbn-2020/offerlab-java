-- V39: manual channel-quality dispatch-batch coordination and immutable events.
ALTER TABLE t_collab_content_maintenance_dispatch_batch
    ADD COLUMN effective_due_at DATETIME(3) NULL COMMENT 'V39 effective execution deadline',
    ADD COLUMN coordination_version INT NOT NULL DEFAULT 0 COMMENT 'V39 optimistic coordination version';

UPDATE t_collab_content_maintenance_dispatch_batch
SET effective_due_at = due_at
WHERE effective_due_at IS NULL;

ALTER TABLE t_collab_content_maintenance_dispatch_batch
    ADD CONSTRAINT chk_maintenance_dispatch_batch_v39_coordination_version CHECK (
        coordination_version >= 0
    ),
    ADD CONSTRAINT chk_maintenance_dispatch_batch_v39_effective_due CHECK (
        (effective_due_at IS NULL AND due_at IS NULL)
        OR (
            effective_due_at IS NOT NULL
            AND due_at IS NOT NULL
            AND effective_due_at >= due_at
        )
    );

ALTER TABLE t_collab_content_maintenance_task
    DROP CHECK chk_maintenance_task_v38_close_reason,
    ADD CONSTRAINT chk_maintenance_task_v39_close_reason CHECK (
        close_reason_code IS NULL OR close_reason_code IN (
            'OUT_OF_SCOPE', 'DUPLICATE', 'NO_LONGER_RELEVANT',
            'AUTHOR_UNRESPONSIVE', 'BATCH_WITHDRAWN', 'OTHER'
        )
    );

CREATE TABLE IF NOT EXISTS t_collab_content_maintenance_revision_gate (
    source_type         VARCHAR(32)   NOT NULL,
    source_post_id      BIGINT        NOT NULL,
    source_ref_id       BIGINT        NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (source_type, source_post_id, source_ref_id),
    CONSTRAINT chk_maintenance_revision_gate_source CHECK (
        source_type = 'CHANNEL_HEALTH'
    ),
    CONSTRAINT chk_maintenance_revision_gate_identity CHECK (
        source_post_id > 0 AND source_ref_id > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Transaction gate for channel-quality revision disposition and dispatch';

CREATE TABLE IF NOT EXISTS t_collab_content_maintenance_dispatch_batch_event (
    id                      BIGINT        NOT NULL,
    batch_id                BIGINT        NOT NULL,
    operator_uid            BIGINT        NOT NULL,
    event_type              VARCHAR(40)   NOT NULL,
    previous_due_at         DATETIME(3)   NULL,
    effective_due_at        DATETIME(3)   NULL,
    previous_assignee_uid   BIGINT        NULL,
    replacement_assignee_uid BIGINT       NULL,
    risk_code               VARCHAR(32)   NULL,
    withdraw_reason_code    VARCHAR(32)   NULL,
    affected_task_count     TINYINT       NOT NULL,
    note                    VARCHAR(500)  NOT NULL,
    coordination_version    INT           NOT NULL,
    withdrawn_batch_id      BIGINT GENERATED ALWAYS AS (
        CASE WHEN event_type = 'OPEN_TASKS_WITHDRAWN' THEN batch_id ELSE NULL END
    ) STORED,
    create_time             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_maintenance_dispatch_batch_event_timeline (batch_id, id DESC),
    UNIQUE KEY uk_maintenance_dispatch_batch_event_withdrawn (withdrawn_batch_id),
    CONSTRAINT chk_maintenance_dispatch_batch_event_identity CHECK (
        id > 0
        AND batch_id > 0
        AND operator_uid > 0
        AND coordination_version > 0
        AND affected_task_count >= 0
        AND (previous_assignee_uid IS NULL OR previous_assignee_uid > 0)
        AND (replacement_assignee_uid IS NULL OR replacement_assignee_uid > 0)
    ),
    CONSTRAINT chk_maintenance_dispatch_batch_event_note CHECK (
        CHAR_LENGTH(TRIM(note)) BETWEEN 2 AND 500
    ),
    CONSTRAINT chk_maintenance_dispatch_batch_event_shape CHECK (
        (
            event_type = 'DEADLINE_EXTENDED'
            AND previous_due_at IS NOT NULL
            AND effective_due_at IS NOT NULL
            AND effective_due_at > previous_due_at
            AND previous_assignee_uid IS NULL
            AND replacement_assignee_uid IS NULL
            AND risk_code IS NULL
            AND withdraw_reason_code IS NULL
            AND affected_task_count > 0
        )
        OR (
            event_type = 'ACTIVE_TASKS_REASSIGNED'
            AND previous_due_at IS NULL
            AND effective_due_at IS NULL
            AND replacement_assignee_uid IS NOT NULL
            AND (
                previous_assignee_uid IS NULL
                OR previous_assignee_uid <> replacement_assignee_uid
            )
            AND risk_code IS NULL
            AND withdraw_reason_code IS NULL
            AND affected_task_count > 0
        )
        OR (
            event_type = 'RISK_NOTE_ADDED'
            AND previous_due_at IS NULL
            AND effective_due_at IS NULL
            AND previous_assignee_uid IS NULL
            AND replacement_assignee_uid IS NULL
            AND risk_code IN ('BLOCKER', 'CAPACITY_RISK', 'REVIEW_DELAY', 'OVERDUE_ESCALATION')
            AND withdraw_reason_code IS NULL
            AND affected_task_count = 0
        )
        OR (
            event_type = 'OPEN_TASKS_WITHDRAWN'
            AND previous_due_at IS NULL
            AND effective_due_at IS NULL
            AND previous_assignee_uid IS NULL
            AND replacement_assignee_uid IS NULL
            AND risk_code IS NULL
            AND withdraw_reason_code IN ('SCOPE_INVALID', 'DUPLICATE_SCOPE', 'PRIORITY_REPLACED', 'OTHER')
            AND affected_task_count > 0
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Immutable manual coordination history for quality review dispatch batches';
