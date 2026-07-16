-- Stage 6: trusted experience profiles, server-side revisits, and persisted search gaps.
-- These tables only model public-community trust, return visits, and governed demand.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_int_content_trust_profile (
    post_id                BIGINT        NOT NULL,
    author_uid             BIGINT        NOT NULL,
    author_role            VARCHAR(32)   NOT NULL,
    experience_start_at    DATETIME(3)   NULL,
    experience_end_at      DATETIME(3)   NULL,
    applicable_audience    VARCHAR(500)  NULL,
    applicable_context     VARCHAR(1000) NULL,
    process_summary        VARCHAR(2000) NULL,
    outcome_summary        VARCHAR(2000) NULL,
    known_limitations      VARCHAR(2000) NULL,
    source_summary         VARCHAR(1000) NULL,
    interest_disclosure    VARCHAR(1000) NULL,
    completeness_score     TINYINT       NOT NULL DEFAULT 0,
    last_confirmed_at      DATETIME(3)   NULL,
    profile_version        BIGINT        NOT NULL DEFAULT 1,
    create_time            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (post_id),
    KEY idx_trust_profile_author (author_uid, update_time, post_id),
    KEY idx_trust_profile_confirmation (last_confirmed_at, post_id),
    CONSTRAINT chk_trust_profile_score CHECK (completeness_score >= 0 AND completeness_score <= 100),
    CONSTRAINT chk_trust_profile_time_range CHECK (
        experience_start_at IS NULL
        OR experience_end_at IS NULL
        OR experience_end_at >= experience_start_at
    ),
    CONSTRAINT chk_trust_profile_version CHECK (profile_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Author-maintained public experience trust profile';

CREATE TABLE IF NOT EXISTS t_int_user_revisit_item (
    id                  BIGINT        NOT NULL,
    uid                 BIGINT        NOT NULL,
    source_type         VARCHAR(32)   NOT NULL,
    source_id           VARCHAR(64)   NOT NULL,
    reason_type         VARCHAR(32)   NOT NULL,
    activity_cursor     BIGINT        NULL,
    title               VARCHAR(160)  NOT NULL,
    description         VARCHAR(500)  NULL,
    target_path         VARCHAR(255)  NOT NULL,
    due_at              DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    revisit_status      VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    dedup_key           CHAR(64)      NOT NULL,
    completed_at        DATETIME(3)   NULL,
    snoozed_until       DATETIME(3)   NULL,
    last_notified_at    DATETIME(3)   NULL,
    create_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_revisit_item_dedup (uid, dedup_key),
    KEY idx_revisit_user_status_due (uid, revisit_status, due_at, id),
    KEY idx_revisit_source_cursor (source_type, source_id, activity_cursor),
    CONSTRAINT chk_revisit_status CHECK (
        revisit_status IN ('OPEN', 'SNOOZED', 'COMPLETED', 'IGNORED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Server-side, on-site revisit work items';

CREATE TABLE IF NOT EXISTS t_search_content_gap (
    id                  BIGINT         NOT NULL,
    gap_key             VARCHAR(80)    NOT NULL,
    keyword             VARCHAR(80)    NOT NULL,
    cluster_id          VARCHAR(80)    NOT NULL,
    reason_text         VARCHAR(500)   NOT NULL,
    window_days         INT            NOT NULL,
    search_count        BIGINT         NOT NULL DEFAULT 0,
    no_result_count     BIGINT         NOT NULL DEFAULT 0,
    weak_result_count   BIGINT         NOT NULL DEFAULT 0,
    min_sample_met      TINYINT        NOT NULL DEFAULT 0,
    risk_level          VARCHAR(16)    NOT NULL,
    target_stage        VARCHAR(24)    NOT NULL,
    gap_status          VARCHAR(24)    NOT NULL,
    source              VARCHAR(32)    NOT NULL,
    source_refs_json    TEXT           NULL,
    created_from        VARCHAR(32)    NOT NULL,
    last_seen_at        DATETIME(3)    NULL,
    domain              TINYINT        NULL,
    reviewed_by         BIGINT         NULL,
    review_note         VARCHAR(1000)  NULL,
    reviewed_at         DATETIME(3)    NULL,
    converted_need_id   BIGINT         NULL,
    resolution_type     VARCHAR(32)    NULL,
    resolution_id       BIGINT         NULL,
    resolution_post_id  BIGINT         NULL,
    fulfilled_at        DATETIME(3)    NULL,
    create_time         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_search_content_gap_key (gap_key),
    KEY idx_search_content_gap_queue (gap_status, risk_level, last_seen_at, id),
    KEY idx_search_content_gap_domain_queue (domain, gap_status, last_seen_at, id),
    KEY idx_search_content_gap_need (converted_need_id, gap_status, id),
    KEY idx_search_content_gap_resolution (resolution_type, resolution_id),
    CONSTRAINT chk_search_content_gap_window CHECK (window_days >= 1 AND window_days <= 90),
    CONSTRAINT chk_search_content_gap_counts CHECK (
        search_count >= 0
        AND no_result_count >= 0
        AND weak_result_count >= 0
    ),
    CONSTRAINT chk_search_content_gap_min_sample CHECK (min_sample_met IN (0, 1)),
    CONSTRAINT chk_search_content_gap_domain CHECK (domain IS NULL OR domain BETWEEN 1 AND 5),
    CONSTRAINT chk_search_content_gap_risk CHECK (
        risk_level IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT chk_search_content_gap_status CHECK (
        gap_status IN ('CANDIDATE', 'REVIEW_REQUIRED', 'APPROVED', 'IGNORED', 'CONVERTED', 'FULFILLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Governed aggregate search-content gaps';
