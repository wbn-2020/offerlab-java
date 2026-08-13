-- V31 reads only active V30 quality-expectation controls as an opaque aggregate.
ALTER TABLE t_feed_feedback_preference
    ADD KEY idx_feed_feedback_quality_signal
        (reason, action, post_id, expires_at, update_time, uid);
