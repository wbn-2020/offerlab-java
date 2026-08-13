-- V32: persist the latest publicly effective text revision used as a quality-signal boundary.
ALTER TABLE t_post_main
    ADD COLUMN latest_effective_content_revision_at DATETIME(3) NULL,
    ADD COLUMN latest_effective_content_revision_token VARCHAR(64) NULL;

ALTER TABLE t_post_version_history
    ADD COLUMN quality_signal_revision TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN quality_signal_revision_state VARCHAR(32) NULL,
    ADD COLUMN quality_signal_effective_at DATETIME(3) NULL,
    ADD COLUMN quality_signal_revision_token VARCHAR(64) NULL,
    ADD KEY idx_post_quality_signal_revision
        (post_id, quality_signal_revision, quality_signal_revision_state,
         quality_signal_effective_at, result_version, id);

-- Keep V31's index while introducing the access path used by per-post V32 windows.
ALTER TABLE t_feed_feedback_preference
    ADD KEY idx_feed_feedback_quality_signal_v32
        (reason, post_id, action, update_time, expires_at, uid);
