SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_projection_reconcile_request (
    id                  BIGINT       NOT NULL,
    resource_id         VARCHAR(64)  NOT NULL,
    operator_uid        BIGINT       NOT NULL,
    projection_type     VARCHAR(64)  NOT NULL,
    idempotency_key     VARCHAR(96)  NOT NULL,
    request_fingerprint CHAR(64)     NOT NULL,
    request_status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    result_json         JSON         NULL,
    create_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_projection_reconcile_resource (resource_id),
    KEY idx_projection_reconcile_operator_time (operator_uid, create_time, id),
    KEY idx_projection_reconcile_type_time (projection_type, create_time, id),
    CONSTRAINT chk_projection_reconcile_status
        CHECK (request_status IN ('PENDING', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Transactional idempotency reservation and result for projection reconciliation';
