-- Durable consumer inbox for transactional event idempotency.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_event_consumer_inbox (
    id                BIGINT       NOT NULL,
    consumer_name     VARCHAR(64)  NOT NULL,
    idempotency_key   VARCHAR(160) NOT NULL,
    event_type        VARCHAR(64)  NULL,
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer_name, idempotency_key),
    KEY idx_event_consumer_inbox_created (create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional inbox for idempotent event consumers';
