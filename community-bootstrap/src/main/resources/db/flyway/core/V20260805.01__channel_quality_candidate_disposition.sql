-- V37: persist current operational disposition for a channel-quality candidate revision.
CREATE TABLE IF NOT EXISTS t_collab_content_maintenance_candidate_disposition (
    id                  BIGINT        NOT NULL,
    domain              TINYINT       NOT NULL,
    source_type         VARCHAR(32)   NOT NULL,
    source_post_id      BIGINT        NOT NULL,
    source_ref_id       BIGINT        NOT NULL,
    disposition_state   VARCHAR(24)   NOT NULL,
    reason_code         VARCHAR(32)   NULL,
    snoozed_until       DATETIME(3)   NULL,
    updated_by_uid      BIGINT        NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_candidate_disposition_source
        (source_type, source_post_id, source_ref_id),
    KEY idx_candidate_disposition_source_state
        (source_type, source_post_id, source_ref_id, disposition_state),
    KEY idx_candidate_disposition_due
        (domain, disposition_state, snoozed_until, update_time),
    CONSTRAINT chk_candidate_disposition_domain CHECK (domain BETWEEN 1 AND 5),
    CONSTRAINT chk_candidate_disposition_ids CHECK (
        source_post_id > 0
        AND source_ref_id > 0
        AND updated_by_uid > 0
    ),
    CONSTRAINT chk_candidate_disposition_source_type CHECK (
        source_type = 'CHANNEL_HEALTH'
    ),
    CONSTRAINT chk_candidate_disposition_state CHECK (
        disposition_state IN ('DISMISSED', 'SNOOZED', 'CLEARED')
    ),
    CONSTRAINT chk_candidate_disposition_reason CHECK (
        reason_code IS NULL OR reason_code IN (
            'NOT_ACTIONABLE', 'OUT_OF_SCOPE', 'DUPLICATE', 'WAIT_FOR_AUTHOR'
        )
    ),
    CONSTRAINT chk_candidate_disposition_snooze CHECK (
        (disposition_state = 'SNOOZED' AND reason_code IS NOT NULL AND snoozed_until IS NOT NULL)
        OR (disposition_state = 'DISMISSED' AND reason_code IS NOT NULL AND snoozed_until IS NULL)
        OR (disposition_state = 'CLEARED' AND reason_code IS NULL AND snoozed_until IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='Current channel quality candidate disposition by public post revision';
