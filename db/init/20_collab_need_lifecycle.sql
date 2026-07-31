-- Content need lifecycle timestamps, immutable collaboration timeline, and relation-id pagination indexes.
SET NAMES utf8mb4;

ALTER TABLE t_collab_content_need
    ADD COLUMN claimed_at       DATETIME(3) NULL COMMENT 'When the current claim started' AFTER claimed_by_uid,
    ADD COLUMN last_progress_at DATETIME(3) NULL COMMENT 'Most recent real collaboration progress time' AFTER claimed_at;

UPDATE t_collab_content_need
SET claimed_at = COALESCE(claimed_at, update_time, create_time),
    last_progress_at = COALESCE(last_progress_at, update_time, create_time)
WHERE need_status IN ('CLAIMED', 'SUBMITTED')
  AND (claimed_at IS NULL OR last_progress_at IS NULL);

CREATE TABLE IF NOT EXISTS t_collab_content_need_event (
    id               BIGINT        NOT NULL,
    need_id          BIGINT        NOT NULL,
    event_type       VARCHAR(24)   NOT NULL,
    actor_uid        BIGINT        NULL,
    claimant_uid     BIGINT        NULL COMMENT 'Claimant associated with this event at write time',
    from_status      VARCHAR(24)   NULL,
    to_status        VARCHAR(24)   NULL,
    target_type      VARCHAR(32)   NULL,
    target_id        BIGINT        NULL,
    note             VARCHAR(1000) NULL,
    visibility_scope VARCHAR(24)   NOT NULL DEFAULT 'PUBLIC',
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collab_need_event_need (need_id, id),
    KEY idx_collab_need_event_visibility (need_id, visibility_scope, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable content need collaboration timeline';

ALTER TABLE t_collab_content_need_follow
    ADD KEY idx_collab_need_follow_uid_active_id (uid, active, id),
    ADD KEY idx_collab_need_follow_need_active_id (need_id, active, id);
