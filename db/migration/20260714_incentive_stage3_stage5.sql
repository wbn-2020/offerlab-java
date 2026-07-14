-- Stage 3-5: shadow reputation/points, limited virtual benefits and governed community roles.
-- MySQL is authoritative. No table in this migration represents real money or transferable currency.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_incentive_account (
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    account_type      VARCHAR(16)  NOT NULL COMMENT 'REPUTATION / POINT',
    domain_code       VARCHAR(32)  NOT NULL DEFAULT 'GLOBAL',
    total_balance     BIGINT       NOT NULL DEFAULT 0,
    available_balance BIGINT       NOT NULL DEFAULT 0,
    frozen_balance    BIGINT       NOT NULL DEFAULT 0,
    recovery_debt     BIGINT       NOT NULL DEFAULT 0,
    account_status    VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / FROZEN / RECOVERY_DUE',
    version           BIGINT       NOT NULL DEFAULT 0,
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_account_scope (user_id, account_type, domain_code),
    KEY idx_incentive_account_type_domain (account_type, domain_code, user_id),
    CONSTRAINT chk_incentive_account_type CHECK (account_type IN ('REPUTATION', 'POINT')),
    CONSTRAINT chk_incentive_account_balances CHECK (
        total_balance >= 0 AND available_balance >= 0 AND frozen_balance >= 0
        AND recovery_debt >= 0
        AND total_balance = available_balance + frozen_balance
    ),
    CONSTRAINT chk_incentive_account_status CHECK (
        account_status IN ('ACTIVE', 'FROZEN', 'RECOVERY_DUE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Non-cash incentive account projection';

CREATE TABLE IF NOT EXISTS t_incentive_ledger (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    account_id          BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    account_type        VARCHAR(16)   NOT NULL,
    domain_code         VARCHAR(32)   NOT NULL DEFAULT 'GLOBAL',
    entry_type          VARCHAR(24)   NOT NULL,
    delta_total         BIGINT        NOT NULL,
    delta_available     BIGINT        NOT NULL,
    delta_frozen        BIGINT        NOT NULL,
    total_after         BIGINT        NOT NULL,
    available_after     BIGINT        NOT NULL,
    frozen_after        BIGINT        NOT NULL,
    idempotency_key     VARCHAR(96)   NOT NULL,
    request_fingerprint CHAR(64)      NOT NULL,
    reference_type      VARCHAR(32)   NULL,
    reference_id        VARCHAR(64)   NULL,
    rule_code           VARCHAR(64)   NULL,
    rule_version        INT           NULL,
    batch_id            BIGINT        NULL,
    reversed_entry_id   BIGINT        NULL,
    reason              VARCHAR(500)  NOT NULL,
    operator_uid        BIGINT        NULL,
    audit_json          JSON          NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_ledger_idempotency (idempotency_key),
    UNIQUE KEY uk_incentive_ledger_reversal (reversed_entry_id),
    KEY idx_incentive_ledger_user_time (user_id, create_time, id),
    KEY idx_incentive_ledger_account_time (account_id, create_time, id),
    KEY idx_incentive_ledger_reference (reference_type, reference_id),
    CONSTRAINT chk_incentive_ledger_projection CHECK (
        total_after >= 0 AND available_after >= 0 AND frozen_after >= 0
        AND total_after = available_after + frozen_after
        AND delta_total = delta_available + delta_frozen
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only immutable incentive ledger';

CREATE TABLE IF NOT EXISTS t_incentive_recovery_debt (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    account_id          BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    original_entry_id   BIGINT        NOT NULL,
    reversal_entry_id   BIGINT        NOT NULL,
    original_amount     BIGINT        NOT NULL,
    recovered_amount    BIGINT        NOT NULL DEFAULT 0,
    outstanding_amount  BIGINT        NOT NULL,
    debt_status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    action_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    settled_time        DATETIME(3)   NULL,
    UNIQUE KEY uk_incentive_recovery_original (original_entry_id),
    KEY idx_incentive_recovery_account (account_id, debt_status, id),
    CONSTRAINT chk_incentive_recovery_amount CHECK (
        original_amount > 0 AND recovered_amount >= 0 AND outstanding_amount >= 0
        AND recovered_amount + outstanding_amount <= original_amount
    ),
    CONSTRAINT chk_incentive_recovery_status CHECK (
        debt_status IN ('PENDING', 'SETTLED', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Explicit pending recovery debt offset by future rewards';

DROP TRIGGER IF EXISTS trg_incentive_ledger_block_update;
CREATE TRIGGER trg_incentive_ledger_block_update
BEFORE UPDATE ON t_incentive_ledger
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_incentive_ledger is append-only';

DROP TRIGGER IF EXISTS trg_incentive_ledger_block_delete;
CREATE TRIGGER trg_incentive_ledger_block_delete
BEFORE DELETE ON t_incentive_ledger
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_incentive_ledger is append-only';

CREATE TABLE IF NOT EXISTS t_incentive_reward_rule (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    rule_code           VARCHAR(64)   NOT NULL,
    rule_version        INT           NOT NULL,
    account_type        VARCHAR(16)   NOT NULL,
    domain_code         VARCHAR(32)   NOT NULL DEFAULT 'GLOBAL',
    reward_amount       BIGINT        NOT NULL,
    daily_user_cap      BIGINT        NOT NULL DEFAULT 0,
    lifetime_user_cap   BIGINT        NOT NULL DEFAULT 0,
    enabled             TINYINT       NOT NULL DEFAULT 1,
    valid_from          DATETIME(3)   NULL,
    valid_until         DATETIME(3)   NULL,
    created_by          BIGINT        NOT NULL,
    change_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_rule_version (rule_code, rule_version),
    KEY idx_incentive_rule_enabled (enabled, valid_from, valid_until),
    CONSTRAINT chk_incentive_rule_amount CHECK (reward_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned immutable reward rules';

INSERT IGNORE INTO t_incentive_reward_rule(
    id, rule_code, rule_version, account_type, domain_code, reward_amount,
    daily_user_cap, lifetime_user_cap, enabled, valid_from, valid_until, created_by, change_reason
) VALUES
    (202607140101, 'ACCEPTED_ANSWER_POINT_V1', 1, 'POINT', 'GLOBAL', 12, 60, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140102, 'ACCEPTED_ANSWER_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 6, 30, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140103, 'SUGGESTION_ACCEPTED_POINT_V1', 1, 'POINT', 'GLOBAL', 10, 50, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140104, 'SUGGESTION_ACCEPTED_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 4, 20, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140105, 'FRESHNESS_UPDATED_POINT_V1', 1, 'POINT', 'GLOBAL', 8, 40, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140106, 'FRESHNESS_UPDATED_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 2, 10, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140107, 'OPERATION_SELECTED_POINT_V1', 1, 'POINT', 'GLOBAL', 30, 60, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140108, 'OPERATION_SELECTED_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 20, 40, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140109, 'REPORT_ACTION_TAKEN_POINT_V1', 1, 'POINT', 'GLOBAL', 3, 15, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140110, 'REPORT_ACTION_TAKEN_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 1, 5, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140111, 'COLLAB_ACCEPTED_POINT_V1', 1, 'POINT', 'GLOBAL', 10, 50, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140112, 'COLLAB_ACCEPTED_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 4, 20, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140113, 'FIRST_QUALIFIED_POST_POINT_V1', 1, 'POINT', 'GLOBAL', 20, 20, 20, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140114, 'FIRST_QUALIFIED_POST_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 5, 5, 5, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140115, 'QUALIFIED_POST_POINT_V1', 1, 'POINT', 'GLOBAL', 10, 30, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140116, 'HELPFUL_COMMENT_POINT_V1', 1, 'POINT', 'GLOBAL', 5, 15, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed'),
    (202607140117, 'HELPFUL_COMMENT_REPUTATION_V1', 1, 'REPUTATION', 'EVENT_DOMAIN', 3, 9, 0, 1, NULL, NULL, 0, 'Stage 3 shadow reward seed');

CREATE TABLE IF NOT EXISTS t_incentive_reward_batch (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    batch_key           VARCHAR(96)   NOT NULL,
    rule_code           VARCHAR(64)   NOT NULL,
    rule_version        INT           NOT NULL,
    batch_status        VARCHAR(16)   NOT NULL DEFAULT 'CREATED',
    requested_count     INT           NOT NULL DEFAULT 0,
    applied_count       INT           NOT NULL DEFAULT 0,
    rejected_count      INT           NOT NULL DEFAULT 0,
    failed_count        INT           NOT NULL DEFAULT 0,
    created_by          BIGINT        NOT NULL,
    action_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finish_time         DATETIME(3)   NULL,
    UNIQUE KEY uk_incentive_batch_key (batch_key),
    KEY idx_incentive_batch_status_time (batch_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bounded reward processing batch';

CREATE TABLE IF NOT EXISTS t_incentive_reward_inbox (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    stable_key          VARCHAR(96)   NOT NULL,
    event_type          VARCHAR(64)   NOT NULL,
    request_fingerprint CHAR(64)      NOT NULL,
    recipient_uid       BIGINT        NOT NULL,
    domain_code         VARCHAR(32)   NOT NULL DEFAULT 'GLOBAL',
    event_domain_code   VARCHAR(32)   NULL COMMENT 'Original content domain, including for global POINT rewards',
    source_reference_type VARCHAR(48) NULL,
    source_reference_id VARCHAR(128)  NULL,
    parent_reference_type VARCHAR(48) NULL,
    parent_reference_id  VARCHAR(128) NULL,
    rule_code           VARCHAR(64)   NOT NULL,
    rule_version        INT           NOT NULL,
    payload_json        JSON          NULL,
    received_by         BIGINT        NOT NULL,
    receive_reason      VARCHAR(500)  NOT NULL,
    inbox_status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    batch_id            BIGINT        NULL,
    attempt_count       INT           NOT NULL DEFAULT 0,
    processed_entry_id  BIGINT        NULL,
    error_message       VARCHAR(500)  NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    processed_time      DATETIME(3)   NULL,
    UNIQUE KEY uk_incentive_inbox_stable_key (stable_key),
    KEY idx_incentive_inbox_status_rule (inbox_status, rule_code, rule_version, id),
    KEY idx_incentive_inbox_recipient_time (recipient_uid, create_time),
    KEY idx_incentive_inbox_source (source_reference_type, source_reference_id, inbox_status),
    KEY idx_incentive_inbox_parent (parent_reference_type, parent_reference_id, inbox_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent reward event inbox';

CREATE TABLE IF NOT EXISTS t_incentive_invalidation_job (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    job_key             CHAR(64)      NOT NULL,
    reference_type      VARCHAR(48)   NOT NULL,
    reference_id        VARCHAR(128)  NOT NULL,
    invalidation_reason VARCHAR(500)  NOT NULL,
    job_status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    cursor_value        BIGINT        NOT NULL DEFAULT 0,
    processed_count     INT           NOT NULL DEFAULT 0,
    attempt_count       INT           NOT NULL DEFAULT 0,
    lease_owner         VARCHAR(64)   NULL,
    lease_until         DATETIME(3)   NULL,
    last_error          VARCHAR(500)  NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    completed_time      DATETIME(3)   NULL,
    UNIQUE KEY uk_incentive_invalidation_job_key (job_key),
    KEY idx_incentive_invalidation_job_queue (job_status, lease_until, update_time, id),
    CONSTRAINT chk_incentive_invalidation_job_status CHECK (
        job_status IN ('PENDING', 'RUNNING', 'COMPLETED')
    ),
    CONSTRAINT chk_incentive_invalidation_job_counters CHECK (
        cursor_value >= 0 AND processed_count >= 0 AND attempt_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Leased persistent cursor for trusted reward invalidation';

CREATE TABLE IF NOT EXISTS t_incentive_reward_guard (
    recipient_uid       BIGINT        NOT NULL,
    rule_code           VARCHAR(64)   NOT NULL,
    rule_version        INT           NOT NULL,
    counter_date        DATE          NOT NULL,
    daily_awarded       BIGINT        NOT NULL DEFAULT 0,
    lifetime_awarded    BIGINT        NOT NULL DEFAULT 0,
    version             BIGINT        NOT NULL DEFAULT 0,
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (recipient_uid, rule_code, rule_version),
    CONSTRAINT chk_incentive_reward_guard CHECK (daily_awarded >= 0 AND lifetime_awarded >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recipient-rule serial guard for reward caps';

CREATE TABLE IF NOT EXISTS t_incentive_freeze_record (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    account_id          BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    freeze_amount       BIGINT        NOT NULL,
    freeze_status       VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / RELEASED / CONSUMED',
    blocks_spending     TINYINT       NOT NULL DEFAULT 1,
    freeze_entry_id     BIGINT        NOT NULL,
    release_entry_id    BIGINT        NULL,
    operator_uid        BIGINT        NOT NULL,
    action_reason       VARCHAR(500)  NOT NULL,
    release_reason      VARCHAR(500)  NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_freeze_entry (freeze_entry_id),
    KEY idx_incentive_freeze_user_status (user_id, freeze_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audited incentive balance freeze';

CREATE TABLE IF NOT EXISTS t_incentive_reconciliation_run (
    id                        BIGINT        NOT NULL PRIMARY KEY,
    run_status                VARCHAR(16)   NOT NULL DEFAULT 'RUNNING',
    scanned_count             INT           NOT NULL DEFAULT 0,
    mismatch_count            INT           NOT NULL DEFAULT 0,
    total_absolute_difference BIGINT        NOT NULL DEFAULT 0,
    cycle_no                   BIGINT        NOT NULL DEFAULT 1,
    cursor_start_account_id    BIGINT        NOT NULL DEFAULT 0,
    cursor_end_account_id      BIGINT        NOT NULL DEFAULT 0,
    next_cursor_account_id     BIGINT        NOT NULL DEFAULT 0,
    coverage_complete          TINYINT       NOT NULL DEFAULT 0,
    operator_uid              BIGINT        NOT NULL,
    action_reason             VARCHAR(500)  NOT NULL,
    create_time               DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finish_time               DATETIME(3)   NULL,
    KEY idx_incentive_recon_time (create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bounded reconciliation summary';

CREATE TABLE IF NOT EXISTS t_incentive_reconciliation_cursor (
    id                  TINYINT       NOT NULL PRIMARY KEY,
    last_account_id     BIGINT        NOT NULL DEFAULT 0,
    cycle_no            BIGINT        NOT NULL DEFAULT 1,
    version             BIGINT        NOT NULL DEFAULT 0,
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT chk_incentive_reconciliation_cursor_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Persistent keyset cursor for full incentive account reconciliation';

INSERT IGNORE INTO t_incentive_reconciliation_cursor(id, last_account_id, cycle_no, version)
VALUES (1, 0, 1, 0);

CREATE TABLE IF NOT EXISTS t_incentive_reconciliation_item (
    id                        BIGINT        NOT NULL PRIMARY KEY,
    run_id                    BIGINT        NOT NULL,
    account_id                BIGINT        NOT NULL,
    projected_total           BIGINT        NOT NULL,
    ledger_total              BIGINT        NOT NULL,
    projected_available       BIGINT        NOT NULL,
    ledger_available          BIGINT        NOT NULL,
    projected_frozen          BIGINT        NOT NULL,
    ledger_frozen             BIGINT        NOT NULL,
    absolute_difference       BIGINT        NOT NULL,
    create_time               DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_recon_account (run_id, account_id),
    KEY idx_incentive_recon_item_difference (run_id, absolute_difference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reconciliation mismatch detail';

CREATE TABLE IF NOT EXISTS t_incentive_appeal (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    appellant_uid         BIGINT        NOT NULL,
    target_type           VARCHAR(16)   NOT NULL,
    target_id             BIGINT        NOT NULL,
    related_ledger_id     BIGINT        NULL,
    related_recovery_debt_id BIGINT     NULL,
    original_operator_uid BIGINT        NULL,
    appeal_reason         VARCHAR(1000) NOT NULL,
    appeal_status         VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED',
    reviewer_uid          BIGINT        NULL,
    review_reason         VARCHAR(500)  NULL,
    restore_entry_id      BIGINT        NULL,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_appeal_target (appellant_uid, target_type, target_id),
    KEY idx_incentive_appeal_queue (appeal_status, update_time, id),
    CONSTRAINT chk_incentive_appeal_target CHECK (target_type IN ('LEDGER', 'REVERSAL', 'FREEZE')),
    CONSTRAINT chk_incentive_appeal_status CHECK (appeal_status IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-owned incentive action appeal and compensation lifecycle';

CREATE TABLE IF NOT EXISTS t_incentive_risk_scan_cursor (
    scan_type            VARCHAR(48)   NOT NULL PRIMARY KEY,
    cursor_value         BIGINT        NOT NULL DEFAULT 0,
    cycle_no             BIGINT        NOT NULL DEFAULT 1,
    version              BIGINT        NOT NULL DEFAULT 0,
    update_time          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Persistent cursor for bounded incentive risk scans';

CREATE TABLE IF NOT EXISTS t_incentive_risk_scan_run (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    scan_type             VARCHAR(48)   NOT NULL,
    cycle_no              BIGINT        NOT NULL,
    cursor_start          BIGINT        NOT NULL,
    cursor_end            BIGINT        NOT NULL,
    next_cursor           BIGINT        NOT NULL,
    coverage_complete     TINYINT       NOT NULL DEFAULT 0,
    scanned_count         INT           NOT NULL DEFAULT 0,
    finding_count         INT           NOT NULL DEFAULT 0,
    run_status            VARCHAR(16)   NOT NULL DEFAULT 'RUNNING',
    operator_uid          BIGINT        NOT NULL,
    action_reason         VARCHAR(500)  NOT NULL,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finish_time           DATETIME(3)   NULL,
    KEY idx_incentive_risk_run (scan_type, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audited bounded incentive risk scan execution';

CREATE TABLE IF NOT EXISTS t_incentive_risk_finding (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    stable_key            VARCHAR(160)  NOT NULL,
    finding_type          VARCHAR(48)   NOT NULL,
    subject_type          VARCHAR(32)   NOT NULL,
    subject_id            VARCHAR(160)  NOT NULL,
    domain_code           VARCHAR(32)   NULL,
    severity              VARCHAR(16)   NOT NULL,
    evaluation_status     VARCHAR(24)   NOT NULL,
    finding_status        VARCHAR(24)   NOT NULL,
    metric_value          BIGINT        NOT NULL DEFAULT 0,
    threshold_value       BIGINT        NOT NULL DEFAULT 0,
    evidence_json         JSON          NOT NULL,
    scan_run_id           BIGINT        NOT NULL,
    resolved_by           BIGINT        NULL,
    resolution_reason     VARCHAR(500)  NULL,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_incentive_risk_finding_stable (stable_key),
    KEY idx_incentive_risk_finding_queue (finding_status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Explainable incentive risk signal; never an automatic punishment';

CREATE TABLE IF NOT EXISTS t_virtual_benefit_catalog (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    benefit_code        VARCHAR(64)   NOT NULL,
    benefit_name        VARCHAR(128)  NOT NULL,
    description         VARCHAR(1000) NULL,
    category            VARCHAR(32)   NOT NULL,
    delivery_type       VARCHAR(32)   NOT NULL,
    point_cost          BIGINT        NOT NULL,
    total_stock         INT           NULL,
    available_stock     INT           NULL,
    enabled             TINYINT       NOT NULL DEFAULT 0,
    created_by          BIGINT        NOT NULL,
    updated_by          BIGINT        NOT NULL,
    action_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_virtual_benefit_code (benefit_code),
    KEY idx_virtual_benefit_enabled (enabled, update_time),
    CONSTRAINT chk_virtual_benefit_cost CHECK (point_cost > 0),
    CONSTRAINT chk_virtual_benefit_stock CHECK (
        total_stock IS NULL OR (total_stock >= 0 AND available_stock >= 0 AND available_stock <= total_stock)
    ),
    CONSTRAINT chk_virtual_benefit_category CHECK (
        category IN ('PROFILE_STYLE', 'CONTENT_TOOL', 'COMMEMORATION', 'COMMUNITY_TOOL')
    ),
    CONSTRAINT chk_virtual_benefit_delivery CHECK (
        delivery_type IN ('ACCOUNT_ENTITLEMENT', 'MANUAL', 'REVERSIBLE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Low-risk non-cash virtual benefit catalog';

INSERT IGNORE INTO t_virtual_benefit_catalog(
    id, benefit_code, benefit_name, description, category, delivery_type, point_cost,
    total_stock, available_stock, enabled, created_by, updated_by, action_reason
) VALUES
    (202607144001, 'PROFILE_THEME_UNLOCK', 'Profile theme', 'Future profile theme authorization model.', 'PROFILE_STYLE', 'ACCOUNT_ENTITLEMENT', 30, NULL, NULL, 0, 0, 0, 'Disabled until a real profile consumer exists'),
    (202607144002, 'IDENTITY_CARD_STYLE', 'Identity card style', 'Future non-authoritative identity card authorization model.', 'PROFILE_STYLE', 'ACCOUNT_ENTITLEMENT', 40, NULL, NULL, 0, 0, 0, 'Disabled until a real identity consumer exists'),
    (202607144003, 'SERIES_LAYOUT_TEMPLATE', 'Series layout template', 'Future collaborative series layout authorization model.', 'CONTENT_TOOL', 'ACCOUNT_ENTITLEMENT', 50, NULL, NULL, 0, 0, 0, 'Disabled until a real layout consumer exists'),
    (202607144004, 'ACTIVITY_BADGE', 'Activity commemorative badge', 'Future commemorative badge authorization model.', 'COMMEMORATION', 'MANUAL', 25, 1000, 1000, 0, 0, 0, 'Disabled until a real badge consumer exists'),
    (202607144005, 'AI_ASSIST_QUOTA', 'AI assist quota', 'Add a bounded content-assist quota.', 'CONTENT_TOOL', 'ACCOUNT_ENTITLEMENT', 80, NULL, NULL, 1, 0, 0, 'Stage 4 low-risk catalog seed'),
    (202607144006, 'CAMPAIGN_DEPOSIT', 'Campaign anti-spam deposit', 'Future low-risk campaign authorization model.', 'COMMUNITY_TOOL', 'REVERSIBLE', 100, NULL, NULL, 0, 0, 0, 'Disabled until a real campaign consumer exists'),
    (202607144007, 'COLLECTION_ORGANIZATION_QUOTA', 'Collection organization quota', 'Add a bounded collection organization quota.', 'CONTENT_TOOL', 'ACCOUNT_ENTITLEMENT', 60, NULL, NULL, 1, 0, 0, 'Stage 4 low-risk catalog seed');

CREATE TABLE IF NOT EXISTS t_virtual_benefit_order (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    order_no            VARCHAR(64)   NOT NULL,
    idempotency_key     VARCHAR(96)   NOT NULL,
    request_fingerprint CHAR(64)      NOT NULL,
    user_id             BIGINT        NOT NULL,
    benefit_id          BIGINT        NOT NULL,
    benefit_code        VARCHAR(64)   NOT NULL,
    benefit_name        VARCHAR(128)  NOT NULL,
    quantity            INT           NOT NULL,
    unit_point_cost     BIGINT        NOT NULL,
    total_point_cost    BIGINT        NOT NULL,
    order_status        VARCHAR(16)   NOT NULL DEFAULT 'CREATED',
    reserve_entry_id    BIGINT        NULL,
    refund_entry_id     BIGINT        NULL,
    delivery_reference  VARCHAR(256)  NULL,
    operator_uid        BIGINT        NULL,
    action_reason       VARCHAR(500)  NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    delivered_time      DATETIME(3)   NULL,
    cancelled_time      DATETIME(3)   NULL,
    refunded_time       DATETIME(3)   NULL,
    UNIQUE KEY uk_virtual_benefit_order_no (order_no),
    UNIQUE KEY uk_virtual_benefit_order_idempotency (user_id, idempotency_key),
    KEY idx_virtual_benefit_order_user (user_id, create_time, id),
    KEY idx_virtual_benefit_order_status (order_status, update_time, id),
    CONSTRAINT chk_virtual_benefit_order_status CHECK (
        order_status IN ('CREATED', 'RESERVED', 'DELIVERED', 'CANCELLED', 'REFUNDED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Virtual benefit order state projection';

CREATE TABLE IF NOT EXISTS t_virtual_benefit_order_history (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    order_id            BIGINT        NOT NULL,
    from_status         VARCHAR(16)   NULL,
    to_status           VARCHAR(16)   NOT NULL,
    operator_uid        BIGINT        NOT NULL,
    action_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_virtual_benefit_history_order (order_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only virtual benefit order history';

CREATE TABLE IF NOT EXISTS t_virtual_benefit_entitlement (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    order_id              BIGINT        NOT NULL,
    user_id               BIGINT        NOT NULL,
    benefit_code          VARCHAR(64)   NOT NULL,
    entitlement_type      VARCHAR(32)   NOT NULL,
    entitlement_key       VARCHAR(96)   NOT NULL,
    quantity_total        BIGINT        NOT NULL,
    quantity_remaining    BIGINT        NOT NULL,
    entitlement_status    VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    reversible            TINYINT       NOT NULL DEFAULT 0,
    payload_json          JSON          NOT NULL,
    granted_by            BIGINT        NOT NULL,
    grant_reason          VARCHAR(500)  NOT NULL,
    revoked_by            BIGINT        NULL,
    revoke_reason         VARCHAR(500)  NULL,
    granted_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revoked_at            DATETIME(3)   NULL,
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_virtual_entitlement_order (order_id),
    KEY idx_virtual_entitlement_user (user_id, entitlement_status, update_time, id),
    CONSTRAINT chk_virtual_entitlement_quantity CHECK (
        quantity_total > 0 AND quantity_remaining >= 0 AND quantity_remaining <= quantity_total
    ),
    CONSTRAINT chk_virtual_entitlement_status CHECK (
        entitlement_status IN ('ACTIVE', 'CONSUMED', 'REVOKED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Structured, queryable delivery fact for every delivered virtual benefit';

CREATE TABLE IF NOT EXISTS t_virtual_benefit_entitlement_usage (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    entitlement_id        BIGINT        NOT NULL,
    user_id               BIGINT        NOT NULL,
    amount                BIGINT        NOT NULL,
    idempotency_key       VARCHAR(96)   NOT NULL,
    request_fingerprint   CHAR(64)      NOT NULL,
    action_reason         VARCHAR(500)  NOT NULL,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_virtual_entitlement_usage (entitlement_id, idempotency_key),
    KEY idx_virtual_entitlement_usage_user (user_id, create_time, id),
    CONSTRAINT chk_virtual_entitlement_usage_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent consumption history for quota entitlements';

CREATE TABLE IF NOT EXISTS t_thank_ticket_daily (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    ticket_date         DATE          NOT NULL,
    granted_count       INT           NOT NULL,
    used_count          INT           NOT NULL DEFAULT 0,
    version             BIGINT        NOT NULL DEFAULT 0,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_thank_ticket_user_day (user_id, ticket_date),
    CONSTRAINT chk_thank_ticket_count CHECK (granted_count >= 0 AND used_count >= 0 AND used_count <= granted_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Daily free non-purchasable thank-you ticket projection';

CREATE TABLE IF NOT EXISTS t_thank_action (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    sender_uid          BIGINT        NOT NULL,
    receiver_uid        BIGINT        NOT NULL,
    target_type         VARCHAR(32)   NOT NULL,
    target_id           VARCHAR(64)   NOT NULL,
    thank_date          DATE          NOT NULL,
    note                VARCHAR(200)  NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_thank_sender_target (sender_uid, target_type, target_id),
    KEY idx_thank_receiver_time (receiver_uid, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Thank signal; never credits recipient balance';

CREATE TABLE IF NOT EXISTS t_incentive_domain_policy (
    domain_code         VARCHAR(32)   NOT NULL PRIMARY KEY,
    incentive_enabled   TINYINT       NOT NULL DEFAULT 1,
    bounty_enabled      TINYINT       NOT NULL DEFAULT 1,
    disabled_reason     VARCHAR(500)  NULL,
    updated_by          BIGINT        NOT NULL DEFAULT 0,
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Domain safety policy for incentives and platform bounties';

INSERT INTO t_incentive_domain_policy(domain_code, incentive_enabled, bounty_enabled, disabled_reason, updated_by)
VALUES ('TECH', 1, 1, NULL, 0),
       ('CAREER', 1, 1, NULL, 0),
       ('READING', 1, 1, NULL, 0),
       ('LIFESTYLE', 1, 1, NULL, 0),
       ('INVESTMENT', 0, 0, 'High-risk domain is disabled for points and bounties by default.', 0)
ON DUPLICATE KEY UPDATE domain_code = VALUES(domain_code);

CREATE TABLE IF NOT EXISTS t_bounty_platform_budget_guard (
    period_key          CHAR(7)       NOT NULL PRIMARY KEY COMMENT 'YYYY-MM',
    budget_limit        BIGINT        NOT NULL,
    reserved_amount     BIGINT        NOT NULL DEFAULT 0,
    awarded_amount      BIGINT        NOT NULL DEFAULT 0,
    version             BIGINT        NOT NULL DEFAULT 0,
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT chk_bounty_platform_budget CHECK (
        budget_limit > 0 AND reserved_amount >= 0 AND awarded_amount >= 0
        AND reserved_amount + awarded_amount <= budget_limit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Auditable hard cap for platform-funded bounty points by period';

CREATE TABLE IF NOT EXISTS t_bounty_user_budget_guard (
    period_key          CHAR(7)       NOT NULL,
    user_id             BIGINT        NOT NULL,
    awarded_amount      BIGINT        NOT NULL DEFAULT 0,
    budget_limit        BIGINT        NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (period_key, user_id),
    CONSTRAINT chk_bounty_user_budget CHECK (
        budget_limit > 0 AND awarded_amount >= 0 AND awarded_amount <= budget_limit
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-user hard cap for platform bounty awards by period';

CREATE TABLE IF NOT EXISTS t_quota_bounty (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    title               VARCHAR(160)  NOT NULL,
    description         VARCHAR(2000) NOT NULL,
    domain_code         VARCHAR(32)   NOT NULL,
    request_type        VARCHAR(32)   NOT NULL,
    risk_category       VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    quota               INT           NOT NULL,
    awarded_count       INT           NOT NULL DEFAULT 0,
    point_reward        BIGINT        NOT NULL,
    total_budget        BIGINT        NOT NULL,
    budget_period       CHAR(7)       NULL,
    reserved_budget     BIGINT        NOT NULL DEFAULT 0,
    consumed_budget     BIGINT        NOT NULL DEFAULT 0,
    bounty_status       VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / OPEN / CLOSED / CANCELLED',
    created_by          BIGINT        NOT NULL,
    updated_by          BIGINT        NOT NULL,
    action_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_quota_bounty_status_domain (bounty_status, domain_code, update_time, id),
    CONSTRAINT chk_quota_bounty_values CHECK (
        quota > 0 AND quota <= 500
        AND awarded_count >= 0 AND awarded_count <= quota
        AND point_reward > 0 AND point_reward <= 5000
        AND total_budget = quota * point_reward AND total_budget <= 100000
        AND reserved_budget >= 0 AND consumed_budget >= 0
        AND consumed_budget <= reserved_budget AND reserved_budget <= total_budget
    ),
    CONSTRAINT chk_quota_bounty_risk CHECK (risk_category = 'LOW'),
    CONSTRAINT chk_quota_bounty_domain CHECK (
        domain_code NOT IN ('INVESTMENT', 'MEDICAL', 'LEGAL', 'PRIVACY', 'DOXXING', 'GHOSTWRITING')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Platform-funded quota bounty only';

CREATE TABLE IF NOT EXISTS t_quota_bounty_submission (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    bounty_id           BIGINT        NOT NULL,
    applicant_uid       BIGINT        NOT NULL,
    public_post_id      BIGINT        NOT NULL,
    request_type        VARCHAR(32)   NOT NULL,
    risk_category       VARCHAR(16)   NOT NULL DEFAULT 'LOW',
    evidence            VARCHAR(2000) NOT NULL,
    submission_status   VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED',
    reviewer_uid        BIGINT        NULL,
    review_reason       VARCHAR(500)  NULL,
    reward_entry_id     BIGINT        NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_quota_bounty_applicant (bounty_id, applicant_uid),
    KEY idx_quota_submission_review (submission_status, update_time, id),
    KEY idx_quota_submission_post (public_post_id, submission_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Submission to a platform-created quota bounty';

CREATE TABLE IF NOT EXISTS t_quota_bounty_appeal (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    submission_id         BIGINT        NOT NULL,
    applicant_uid         BIGINT        NOT NULL,
    original_status       VARCHAR(16)   NOT NULL,
    original_reviewer_uid BIGINT        NULL,
    appeal_reason         VARCHAR(1000) NOT NULL,
    appeal_status         VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED',
    reviewer_uid          BIGINT        NULL,
    review_reason         VARCHAR(500)  NULL,
    compensation_entry_id BIGINT        NULL,
    create_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_quota_bounty_appeal_submission (submission_id),
    KEY idx_quota_bounty_appeal_queue (appeal_status, update_time, id),
    CONSTRAINT chk_quota_bounty_appeal_original CHECK (original_status IN ('APPROVED', 'REJECTED')),
    CONSTRAINT chk_quota_bounty_appeal_status CHECK (appeal_status IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Independent bounty decision appeal and idempotent compensation lifecycle';

CREATE TABLE IF NOT EXISTS t_community_role_definition (
    id                          BIGINT        NOT NULL PRIMARY KEY,
    role_code                   VARCHAR(64)   NOT NULL,
    role_name                   VARCHAR(128)  NOT NULL,
    description                 VARCHAR(1000) NULL,
    domain_code                 VARCHAR(32)   NOT NULL,
    min_account_age_days        INT           NOT NULL DEFAULT 0,
    min_domain_reputation       BIGINT        NOT NULL DEFAULT 0,
    min_activity_count          INT           NOT NULL DEFAULT 0,
    max_violation_count         INT           NOT NULL DEFAULT 0,
    min_curation_accuracy_bps   INT           NOT NULL DEFAULT 0,
    requires_no_risk_freeze     TINYINT       NOT NULL DEFAULT 1,
    enabled                     TINYINT       NOT NULL DEFAULT 0,
    created_by                  BIGINT        NOT NULL,
    updated_by                  BIGINT        NOT NULL,
    action_reason               VARCHAR(500)  NOT NULL,
    create_time                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_community_role_scope (role_code, domain_code),
    KEY idx_community_role_enabled (enabled, domain_code, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Eligibility policy; never grants authority automatically';

INSERT IGNORE INTO t_community_role_definition(
    id, role_code, role_name, description, domain_code,
    min_account_age_days, min_domain_reputation, min_activity_count,
    max_violation_count, min_curation_accuracy_bps, requires_no_risk_freeze,
    enabled, created_by, updated_by, action_reason
)
SELECT 202607145000 + r.role_no * 10 + d.domain_no,
       r.role_code, r.role_name, r.description, d.domain_code,
       r.min_age, r.min_reputation, r.min_activity,
       0, r.min_accuracy, 1,
       1, 0, 0, 'Stage 5 governed role seed'
FROM (
    SELECT 1 AS role_no, 'COLLABORATION_INITIATOR' AS role_code,
           'Collaboration initiator' AS role_name,
           'May propose and organize bounded public collaboration.' AS description,
           7 AS min_age, 20 AS min_reputation, 2 AS min_activity, 0 AS min_accuracy
    UNION ALL SELECT 2, 'TOPIC_CANDIDATE_RECOMMENDER', 'Topic candidate recommender',
           'May recommend public topic candidates for governed review.', 14, 30, 3, 0
    UNION ALL SELECT 3, 'CHANNEL_RESOURCE_MAINTAINER', 'Channel resource maintainer',
           'May maintain low-risk public channel resources.', 30, 80, 8, 0
    UNION ALL SELECT 4, 'LOW_RISK_CANDIDATE_SCOUT', 'Low-risk candidate scout',
           'May review low-risk candidate material without moderation authority.', 30, 100, 10, 7000
    UNION ALL SELECT 5, 'TOPIC_STEWARD', 'Topic steward',
           'May coordinate a public topic under manual governance.', 60, 150, 15, 7500
    UNION ALL SELECT 6, 'CHANNEL_CURATOR', 'Channel curator',
           'May curate public channel resources under manual governance.', 60, 200, 20, 8000
    UNION ALL SELECT 7, 'CAMPAIGN_CONTRIBUTOR', 'Expanded campaign contributor',
           'May join expanded low-risk public campaigns.', 14, 40, 5, 0
    UNION ALL SELECT 8, 'COMMUNITY_RULE_PROPOSER', 'Community rule proposer',
           'May submit community rule proposals for manual review.', 90, 120, 12, 0
) r
CROSS JOIN (
    SELECT 1 AS domain_no, 'TECH' AS domain_code
    UNION ALL SELECT 2, 'CAREER'
    UNION ALL SELECT 3, 'READING'
    UNION ALL SELECT 4, 'LIFESTYLE'
) d;

CREATE TABLE IF NOT EXISTS t_community_role_metric (
    user_id                     BIGINT        NOT NULL,
    domain_code                 VARCHAR(32)   NOT NULL,
    curation_correct_count      INT           NOT NULL DEFAULT 0,
    curation_reviewed_count     INT           NOT NULL DEFAULT 0,
    updated_by                  BIGINT        NOT NULL DEFAULT 0,
    update_reason               VARCHAR(500)  NOT NULL DEFAULT '',
    update_time                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, domain_code),
    CONSTRAINT chk_community_role_metric CHECK (
        curation_correct_count >= 0 AND curation_reviewed_count >= 0
        AND curation_correct_count <= curation_reviewed_count
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audited curation accuracy aggregate';

CREATE TABLE IF NOT EXISTS t_community_role_application (
    id                          BIGINT        NOT NULL PRIMARY KEY,
    applicant_uid               BIGINT        NOT NULL,
    role_code                   VARCHAR(64)   NOT NULL,
    domain_code                 VARCHAR(32)   NOT NULL,
    statement                   VARCHAR(1000) NOT NULL,
    eligibility_snapshot_json   JSON          NOT NULL,
    application_status          VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED',
    reviewer_uid                BIGINT        NULL,
    review_reason               VARCHAR(500)  NULL,
    create_time                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time                 DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_guard                TINYINT GENERATED ALWAYS AS (
        CASE WHEN application_status = 'SUBMITTED' THEN 1 ELSE NULL END
    ) STORED,
    UNIQUE KEY uk_community_role_active_application (applicant_uid, role_code, domain_code, active_guard),
    KEY idx_community_role_application_review (application_status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Manual community role application';

CREATE TABLE IF NOT EXISTS t_community_role_grant (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    role_code           VARCHAR(64)   NOT NULL,
    domain_code         VARCHAR(32)   NOT NULL,
    grant_status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    application_id      BIGINT        NULL,
    granted_by          BIGINT        NOT NULL,
    grant_reason        VARCHAR(500)  NOT NULL,
    action_by           BIGINT        NULL,
    action_reason       VARCHAR(500)  NULL,
    granted_at          DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at          DATETIME(3)   NULL,
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_guard        TINYINT GENERATED ALWAYS AS (
        CASE WHEN grant_status IN ('ACTIVE', 'SUSPENDED') THEN 1 ELSE NULL END
    ) STORED,
    UNIQUE KEY uk_community_role_active_grant (user_id, role_code, domain_code, active_guard),
    KEY idx_community_role_grant_user (user_id, grant_status, update_time, id),
    KEY idx_community_role_grant_expiry (grant_status, expires_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Manually approved community role lifecycle';

CREATE TABLE IF NOT EXISTS t_community_role_grant_history (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    grant_id            BIGINT        NOT NULL,
    from_status         VARCHAR(16)   NULL,
    to_status           VARCHAR(16)   NOT NULL,
    operator_uid        BIGINT        NOT NULL,
    action_reason       VARCHAR(500)  NOT NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_community_role_history_grant (grant_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Append-only community role lifecycle history';
