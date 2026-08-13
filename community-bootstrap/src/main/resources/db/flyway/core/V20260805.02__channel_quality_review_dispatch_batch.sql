-- V38: channel-quality review dispatch batches and immutable maintenance delivery attempts.
CREATE TABLE IF NOT EXISTS t_collab_content_maintenance_dispatch_batch (
    id                  BIGINT        NOT NULL,
    domain              TINYINT       NOT NULL,
    source_type         VARCHAR(32)   NOT NULL,
    name                VARCHAR(160)  NOT NULL,
    priority            VARCHAR(16)   NOT NULL,
    due_at              DATETIME(3)   NULL,
    assignee_uid        BIGINT        NOT NULL,
    created_by_uid      BIGINT        NOT NULL,
    candidate_count     TINYINT        NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_maintenance_dispatch_batch_domain (domain, create_time, id),
    KEY idx_maintenance_dispatch_batch_assignee (assignee_uid, due_at, id),
    CONSTRAINT chk_maintenance_dispatch_batch_domain CHECK (domain BETWEEN 1 AND 5),
    CONSTRAINT chk_maintenance_dispatch_batch_source CHECK (source_type = 'CHANNEL_HEALTH'),
    CONSTRAINT chk_maintenance_dispatch_batch_name CHECK (
        CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160
    ),
    CONSTRAINT chk_maintenance_dispatch_batch_priority CHECK (
        priority IN ('HIGH', 'MEDIUM', 'LOW')
    ),
    CONSTRAINT chk_maintenance_dispatch_batch_actors CHECK (
        assignee_uid > 0 AND created_by_uid > 0
    ),
    CONSTRAINT chk_maintenance_dispatch_batch_candidate_count CHECK (
        candidate_count BETWEEN 1 AND 20
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Atomic channel-quality candidate dispatch batches';

ALTER TABLE t_collab_content_maintenance_task
    ADD COLUMN dispatch_batch_id     BIGINT        NULL COMMENT 'V38 channel-quality dispatch batch id',
    ADD COLUMN task_priority         VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW execution priority',
    ADD COLUMN due_at                DATETIME(3)   NULL COMMENT 'Optional execution deadline',
    ADD COLUMN current_attempt_no    INT           NOT NULL DEFAULT 0 COMMENT 'Latest immutable delivery attempt number',
    ADD COLUMN terminal_outcome_code VARCHAR(32)   NULL COMMENT 'Safe completed-task outcome summary',
    ADD COLUMN close_reason_code     VARCHAR(32)   NULL COMMENT 'Structured reason for a closed task',
    ADD KEY idx_maintenance_batch_status (dispatch_batch_id, task_status, id),
    ADD KEY idx_maintenance_domain_status_due (domain, task_status, due_at, id),
    ADD CONSTRAINT chk_maintenance_task_v38_dispatch_batch CHECK (
        dispatch_batch_id IS NULL
        OR (dispatch_batch_id > 0 AND source_type = 'CHANNEL_HEALTH')
    ),
    ADD CONSTRAINT chk_maintenance_task_v38_priority CHECK (
        task_priority IN ('HIGH', 'MEDIUM', 'LOW')
    ),
    ADD CONSTRAINT chk_maintenance_task_v38_attempt_no CHECK (
        current_attempt_no >= 0
    ),
    ADD CONSTRAINT chk_maintenance_task_v38_terminal_outcome CHECK (
        terminal_outcome_code IS NULL OR terminal_outcome_code = 'VERIFIED_DELIVERY'
    ),
    ADD CONSTRAINT chk_maintenance_task_v38_close_reason CHECK (
        close_reason_code IS NULL OR close_reason_code IN (
            'OUT_OF_SCOPE', 'DUPLICATE', 'NO_LONGER_RELEVANT',
            'AUTHOR_UNRESPONSIVE', 'OTHER'
        )
    ),
    ADD CONSTRAINT chk_maintenance_task_v38_terminal_state CHECK (
        (terminal_outcome_code IS NULL OR task_status = 'COMPLETED')
        AND (close_reason_code IS NULL OR task_status = 'CLOSED')
        AND (terminal_outcome_code IS NULL OR close_reason_code IS NULL)
    );

CREATE TABLE IF NOT EXISTS t_collab_content_maintenance_task_attempt (
    id                  BIGINT        NOT NULL,
    task_id             BIGINT        NOT NULL,
    attempt_no          INT           NOT NULL,
    delivery_type       VARCHAR(24)   NOT NULL,
    delivery_ref_id     BIGINT        NOT NULL,
    delivery_post_id    BIGINT        NULL,
    note                VARCHAR(1000) NOT NULL,
    submitted_by_uid    BIGINT        NOT NULL,
    submitted_at        DATETIME(3)   NOT NULL,
    decision            VARCHAR(24)   NULL,
    reason_code         VARCHAR(32)   NULL,
    review_note         VARCHAR(1000) NULL,
    reviewed_by_uid     BIGINT        NULL,
    reviewed_at         DATETIME(3)   NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_task_attempt (task_id, attempt_no),
    KEY idx_maintenance_task_attempt_timeline (task_id, attempt_no DESC),
    CONSTRAINT chk_maintenance_task_attempt_identity CHECK (
        task_id > 0
        AND attempt_no > 0
        AND submitted_by_uid > 0
    ),
    CONSTRAINT chk_maintenance_task_attempt_delivery CHECK (
        (delivery_type IN ('POST', 'QUESTION')
            AND delivery_ref_id > 0
            AND delivery_post_id = delivery_ref_id)
        OR (delivery_type = 'SERIES'
            AND delivery_ref_id > 0
            AND delivery_post_id IS NULL)
    ),
    CONSTRAINT chk_maintenance_task_attempt_note CHECK (
        CHAR_LENGTH(TRIM(note)) BETWEEN 1 AND 1000
    ),
    CONSTRAINT chk_maintenance_task_attempt_decision CHECK (
        decision IS NULL OR decision IN ('APPROVED', 'REJECTED', 'CLOSED')
    ),
    CONSTRAINT chk_maintenance_task_attempt_reason CHECK (
        (decision IS NULL AND reason_code IS NULL)
        OR (decision = 'APPROVED' AND reason_code IN (
            'QUALITY_VERIFIED', 'EVIDENCE_SUFFICIENT'
        ))
        OR (decision = 'REJECTED' AND reason_code IN (
            'CONTENT_INCOMPLETE', 'PUBLIC_EVIDENCE_MISSING',
            'SCOPE_MISMATCH', 'OTHER'
        ))
        OR (decision = 'CLOSED' AND reason_code IN (
            'OUT_OF_SCOPE', 'DUPLICATE', 'NO_LONGER_RELEVANT',
            'AUTHOR_UNRESPONSIVE', 'OTHER'
        ))
    ),
    CONSTRAINT chk_maintenance_task_attempt_review CHECK (
        (decision IS NULL
            AND review_note IS NULL
            AND reviewed_by_uid IS NULL
            AND reviewed_at IS NULL)
        OR (decision IS NOT NULL
            AND reason_code IS NOT NULL
            AND CHAR_LENGTH(TRIM(review_note)) BETWEEN 1 AND 1000
            AND reviewed_by_uid > 0
            AND reviewed_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Immutable delivery and review rounds for maintenance tasks';

-- Preserve compatible timelines for pre-V38 tasks that already have a valid delivery snapshot.
INSERT INTO t_collab_content_maintenance_task_attempt (
    id, task_id, attempt_no, delivery_type, delivery_ref_id, delivery_post_id,
    note, submitted_by_uid, submitted_at, decision, reason_code,
    review_note, reviewed_by_uid, reviewed_at
)
SELECT
    task.id,
    task.id,
    1,
    task.delivery_type,
    task.delivery_ref_id,
    task.delivery_post_id,
    COALESCE(NULLIF(TRIM(task.delivery_note), ''), 'Legacy V38 compatibility backfill.'),
    COALESCE(NULLIF(task.assignee_uid, 0), task.created_by_uid),
    COALESCE(task.submitted_at, task.update_time, task.create_time),
    CASE task.task_status
        WHEN 'COMPLETED' THEN 'APPROVED'
        WHEN 'CLOSED' THEN 'CLOSED'
        ELSE NULL
    END,
    CASE task.task_status
        WHEN 'COMPLETED' THEN 'QUALITY_VERIFIED'
        WHEN 'CLOSED' THEN 'OTHER'
        ELSE NULL
    END,
    CASE
        WHEN task.task_status IN ('COMPLETED', 'CLOSED') THEN
            COALESCE(NULLIF(TRIM(task.review_note), ''), 'Legacy V38 compatibility backfill.')
        ELSE NULL
    END,
    CASE
        WHEN task.task_status IN ('COMPLETED', 'CLOSED') THEN
            COALESCE(
                NULLIF(task.reviewed_by_uid, 0),
                NULLIF(task.closed_by_uid, 0),
                NULLIF(task.assignee_uid, 0),
                task.created_by_uid
            )
        ELSE NULL
    END,
    CASE
        WHEN task.task_status IN ('COMPLETED', 'CLOSED') THEN
            COALESCE(task.reviewed_at, task.closed_at, task.update_time, task.create_time)
        ELSE NULL
    END
FROM t_collab_content_maintenance_task task
WHERE task.current_attempt_no = 0
  AND task.task_status IN ('SUBMITTED', 'COMPLETED', 'CLOSED')
  AND task.delivery_ref_id > 0
  AND (
      (task.delivery_type IN ('POST', 'QUESTION')
          AND task.delivery_post_id = task.delivery_ref_id)
      OR (task.delivery_type = 'SERIES' AND task.delivery_post_id IS NULL)
  )
  AND COALESCE(NULLIF(task.assignee_uid, 0), task.created_by_uid) > 0
  AND NOT EXISTS (
      SELECT 1
      FROM t_collab_content_maintenance_task_attempt attempt
      WHERE attempt.task_id = task.id
        AND attempt.attempt_no = 1
  );

UPDATE t_collab_content_maintenance_task task
JOIN t_collab_content_maintenance_task_attempt attempt
  ON attempt.task_id = task.id
 AND attempt.attempt_no = 1
SET task.current_attempt_no = 1,
    task.terminal_outcome_code = CASE
        WHEN task.task_status = 'COMPLETED' THEN 'VERIFIED_DELIVERY'
        ELSE NULL
    END,
    task.close_reason_code = CASE
        WHEN task.task_status = 'CLOSED' THEN 'OTHER'
        ELSE NULL
    END
WHERE task.current_attempt_no = 0;
