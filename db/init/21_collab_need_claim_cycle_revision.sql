-- Claim cycles and structured submission revisions for content needs.
-- This migration creates one explicit bridge cycle for claims that are still
-- current at migration time. It never reconstructs ended cycles or old revisions.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_collab_content_need_claim_cycle (
    id                BIGINT        NOT NULL,
    need_id           BIGINT        NOT NULL,
    cycle_no          INT           NOT NULL,
    claimant_uid      BIGINT        NOT NULL,
    cycle_status      VARCHAR(24)   NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/COMPLETED/RELEASED/CLOSED',
    cycle_origin      VARCHAR(32)   NOT NULL DEFAULT 'CLAIM'
        COMMENT 'CLAIM/LEGACY_CURRENT',
    claimed_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_progress_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at          DATETIME(3)   NULL,
    end_reason        VARCHAR(500)  NULL,
    create_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    active_need_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN cycle_status = 'ACTIVE' THEN need_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_need_claim_cycle_no (need_id, cycle_no),
    UNIQUE KEY uk_collab_need_claim_cycle_active (active_need_guard),
    KEY idx_collab_need_claim_cycle_need (need_id, cycle_no, id),
    KEY idx_collab_need_claim_cycle_status (need_id, cycle_status, id),
    KEY idx_collab_need_claim_cycle_claimant (claimant_uid, cycle_status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Historical content need claim cycles';

CREATE TABLE IF NOT EXISTS t_collab_content_need_revision (
    id                 BIGINT        NOT NULL,
    need_id            BIGINT        NOT NULL,
    cycle_id           BIGINT        NOT NULL,
    cycle_no           INT           NOT NULL,
    revision_no        INT           NOT NULL,
    submitter_uid      BIGINT        NOT NULL,
    resolution_type    VARCHAR(24)   NOT NULL,
    resolution_id      BIGINT        NOT NULL,
    resolution_post_id BIGINT        NULL,
    revision_note      VARCHAR(1000) NULL,
    revision_status    VARCHAR(24)   NOT NULL DEFAULT 'SUBMITTED'
        COMMENT 'SUBMITTED/REJECTED/WITHDRAWN/ACCEPTED/CLOSED',
    revision_origin    VARCHAR(32)   NOT NULL DEFAULT 'SUBMISSION'
        COMMENT 'SUBMISSION/LEGACY_CURRENT_SUBMISSION',
    submitted_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    decided_by         BIGINT        NULL,
    decided_at         DATETIME(3)   NULL,
    decision_note      VARCHAR(1000) NULL,
    visibility_scope   VARCHAR(24)   NOT NULL DEFAULT 'PARTICIPANTS'
        COMMENT 'PARTICIPANTS by default; PUBLIC is an explicit future opt-in',
    create_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_need_revision_no (cycle_id, revision_no),
    KEY idx_collab_need_revision_need (need_id, cycle_no, revision_no, id),
    KEY idx_collab_need_revision_cycle_status (cycle_id, revision_status, id),
    KEY idx_collab_need_revision_submitter (submitter_uid, create_time, id),
    KEY idx_collab_need_revision_visibility (need_id, visibility_scope, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Structured content need submission revisions';

-- Use the negative need id as a deterministic migration-only id namespace.
-- Only the currently active projection is handed off; no ended history is inferred.
INSERT INTO t_collab_content_need_claim_cycle(
    id, need_id, cycle_no, claimant_uid, cycle_status, cycle_origin,
    claimed_at, last_progress_at, create_time, update_time
)
SELECT -n.id,
       n.id,
       COALESCE((
           SELECT MAX(existing.cycle_no) + 1
           FROM t_collab_content_need_claim_cycle existing
           WHERE existing.need_id = n.id
       ), 1),
       n.claimed_by_uid,
       'ACTIVE',
       'LEGACY_CURRENT',
       COALESCE(n.claimed_at, n.update_time, n.create_time),
       COALESCE(n.last_progress_at, n.submitted_at, n.claimed_at, n.update_time, n.create_time),
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3)
FROM t_collab_content_need n
WHERE n.claimed_by_uid IS NOT NULL
  AND n.need_status IN ('CLAIMED', 'SUBMITTED')
  AND NOT EXISTS (
      SELECT 1
      FROM t_collab_content_need_claim_cycle current_cycle
      WHERE current_cycle.need_id = n.id
        AND current_cycle.cycle_status = 'ACTIVE'
  );
