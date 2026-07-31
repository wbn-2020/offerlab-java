CREATE TABLE IF NOT EXISTS t_feed_feedback_preference (
    id          BIGINT      NOT NULL,
    uid         BIGINT      NOT NULL,
    post_id     BIGINT      NOT NULL,
    action      VARCHAR(24) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id   BIGINT      NOT NULL,
    reason      VARCHAR(200) NULL,
    expires_at  DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_feed_feedback_uid_post (uid, post_id),
    KEY idx_feed_feedback_uid_action_active (uid, action, expires_at, post_id),
    KEY idx_feed_feedback_uid_target_active (uid, action, target_type, expires_at, target_id),
    KEY idx_feed_feedback_uid_cursor (uid, update_time, id),
    CONSTRAINT chk_feed_feedback_action
        CHECK (action IN ('HIDE', 'LESS_LIKE_THIS')),
    CONSTRAINT chk_feed_feedback_target_type
        CHECK (target_type IN ('POST', 'DOMAIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Per-user reversible feed controls; MySQL is the source of truth and Redis is a cache';
