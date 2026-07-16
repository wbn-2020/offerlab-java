-- Stage 8: governed content-maintenance coordination.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_collab_content_maintenance_task (
    id                  BIGINT        NOT NULL,
    domain              TINYINT       NOT NULL,
    source_type         VARCHAR(32)   NOT NULL,
    source_ref_id       BIGINT        NULL,
    source_post_id      BIGINT        NULL,
    created_by_uid      BIGINT        NOT NULL,
    assignee_uid        BIGINT        NULL,
    title               VARCHAR(160)  NOT NULL,
    detail              VARCHAR(2000) NOT NULL,
    task_status         VARCHAR(24)   NOT NULL DEFAULT 'OPEN',
    delivery_type       VARCHAR(24)   NULL,
    delivery_ref_id     BIGINT        NULL,
    delivery_post_id    BIGINT        NULL,
    delivery_note       VARCHAR(1000) NULL,
    review_note         VARCHAR(1000) NULL,
    claimed_at          DATETIME(3)   NULL,
    submitted_at        DATETIME(3)   NULL,
    reviewed_by_uid     BIGINT        NULL,
    reviewed_at         DATETIME(3)   NULL,
    closed_by_uid       BIGINT        NULL,
    closed_at           DATETIME(3)   NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_source (source_type, source_ref_id, source_post_id),
    KEY idx_maintenance_queue (domain, task_status, update_time, id),
    KEY idx_maintenance_assignee (assignee_uid, task_status, update_time, id),
    KEY idx_maintenance_creator (created_by_uid, task_status, update_time, id),
    KEY idx_maintenance_delivery (delivery_type, delivery_ref_id),
    CONSTRAINT chk_maintenance_domain CHECK (domain BETWEEN 1 AND 5),
    CONSTRAINT chk_maintenance_source CHECK (
        source_type IN (
            'CHANNEL_HEALTH', 'SEARCH_GAP', 'SUGGESTION',
            'FRESHNESS', 'PROFILE_CONFIRMATION', 'QUESTION', 'MANUAL'
        )
    ),
    CONSTRAINT chk_maintenance_status CHECK (
        task_status IN ('OPEN', 'CLAIMED', 'SUBMITTED', 'COMPLETED', 'CLOSED')
    ),
    CONSTRAINT chk_maintenance_delivery_type CHECK (
        delivery_type IS NULL OR delivery_type IN ('POST', 'QUESTION', 'SERIES')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Governed public-content maintenance coordination tasks';
