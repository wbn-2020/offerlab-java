SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_subscription_update_digest (
    id              BIGINT       NOT NULL,
    receiver_uid    BIGINT       NOT NULL,
    source_type     VARCHAR(16)  NOT NULL,
    source_id       BIGINT       NOT NULL,
    resource_type   VARCHAR(16)  NOT NULL,
    resource_id     BIGINT       NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    event_key       VARCHAR(160) NOT NULL,
    actor_uid       BIGINT       NULL,
    payload_json    TEXT         NOT NULL,
    occurred_at     DATETIME(3)  NOT NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_digest_event
        (receiver_uid, source_type, source_id, event_key),
    KEY idx_subscription_digest_receiver_list
        (receiver_uid, is_deleted, occurred_at, id),
    KEY idx_subscription_digest_receiver_source
        (receiver_uid, is_deleted, source_type, source_id, occurred_at, id),
    KEY idx_subscription_digest_resource
        (resource_type, resource_id, is_deleted, occurred_at, id),
    CONSTRAINT chk_subscription_digest_source_type
        CHECK (source_type IN ('TOPIC', 'DISCUSSION', 'NEED')),
    CONSTRAINT chk_subscription_digest_resource_type
        CHECK (resource_type IN ('POST', 'TOPIC', 'NEED', 'COLLECTION', 'SERIES')),
    CONSTRAINT chk_subscription_digest_deleted
        CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Digest-only subscription delivery facts';
