SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_user_subscription_preference (
    id            BIGINT       NOT NULL,
    uid           BIGINT       NOT NULL,
    source_type   VARCHAR(24)  NOT NULL,
    source_id     BIGINT       NOT NULL,
    delivery_mode VARCHAR(16)  NOT NULL DEFAULT 'IMMEDIATE',
    expires_at    DATETIME(3)  NULL,
    create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_subscription_preference_source (uid, source_type, source_id),
    KEY idx_user_subscription_preference_mode
        (uid, is_deleted, delivery_mode, expires_at, source_type, source_id),
    KEY idx_user_subscription_preference_expiry (is_deleted, expires_at, id),
    CONSTRAINT chk_user_subscription_preference_source_type
        CHECK (source_type IN ('USER', 'TOPIC', 'DISCUSSION', 'NEED', 'SERIES', 'ACTIVITY')),
    CONSTRAINT chk_user_subscription_preference_delivery_mode
        CHECK (delivery_mode IN ('IMMEDIATE', 'DIGEST', 'MUTED')),
    CONSTRAINT chk_user_subscription_preference_deleted
        CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Per-user delivery preferences for active community relationships';
