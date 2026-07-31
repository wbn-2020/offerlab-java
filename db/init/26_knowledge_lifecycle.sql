SET NAMES utf8mb4;

ALTER TABLE t_int_content_suggestion
    ADD COLUMN base_version INT NULL AFTER source_url,
    ADD COLUMN target_scope VARCHAR(16) NOT NULL DEFAULT 'OTHER' AFTER base_version,
    ADD COLUMN target_locator VARCHAR(255) NULL AFTER target_scope,
    ADD COLUMN expected_change VARCHAR(2000) NULL AFTER target_locator,
    ADD COLUMN resolution VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER decision,
    ADD COLUMN delivery_status VARCHAR(16) NOT NULL DEFAULT 'UNLINKED' AFTER resolution,
    ADD CONSTRAINT chk_int_content_suggestion_base_version
        CHECK (base_version IS NULL OR base_version >= 0),
    ADD CONSTRAINT chk_int_content_suggestion_target_scope
        CHECK (target_scope IN ('TITLE', 'CONTENT', 'SECTION', 'REFERENCE', 'FRESHNESS', 'OTHER')),
    ADD CONSTRAINT chk_int_content_suggestion_resolution
        CHECK (resolution IN ('PENDING', 'ACCEPTED', 'PARTIAL', 'REJECTED', 'PLANNED')),
    ADD CONSTRAINT chk_int_content_suggestion_delivery_status
        CHECK (delivery_status IN ('UNLINKED', 'LINKED'));

CREATE TABLE t_post_reference (
    id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    owner_uid BIGINT NOT NULL,
    reference_type VARCHAR(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    normalized_url VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_domain VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    note VARCHAR(1000) NULL,
    broken_reason VARCHAR(500) NULL,
    reference_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    revision INT NOT NULL DEFAULT 1,
    last_confirmed_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_reference_active_url (post_id, normalized_url, active_guard),
    KEY idx_post_reference_public (post_id, is_deleted, reference_status, sort_order, id),
    KEY idx_post_reference_owner (owner_uid, is_deleted, update_time, id),
    CONSTRAINT chk_post_reference_type
        CHECK (reference_type IN ('SOURCE', 'EXAMPLE', 'DATA', 'FOLLOW_UP')),
    CONSTRAINT chk_post_reference_status
        CHECK (reference_status IN ('ACTIVE', 'BROKEN')),
    CONSTRAINT chk_post_reference_sort
        CHECK (sort_order >= 0),
    CONSTRAINT chk_post_reference_revision
        CHECK (revision > 0),
    CONSTRAINT chk_post_reference_deleted
        CHECK (is_deleted IN (0, 1)),
    CONSTRAINT fk_post_reference_post
        FOREIGN KEY (post_id) REFERENCES t_post_main(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_reference_owner
        FOREIGN KEY (owner_uid) REFERENCES t_user_account(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_post_knowledge_relation (
    id BIGINT NOT NULL,
    source_post_id BIGINT NOT NULL,
    target_post_id BIGINT NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    reason_text VARCHAR(2000) NOT NULL,
    proposer_uid BIGINT NOT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    visibility_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    reviewer_uid BIGINT NULL,
    review_note VARCHAR(1000) NULL,
    reviewed_at DATETIME(3) NULL,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    effective_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 AND review_status IN ('PENDING', 'APPROVED') THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_knowledge_relation_effective
        (source_post_id, target_post_id, relation_type, effective_guard),
    KEY idx_post_knowledge_relation_source
        (source_post_id, review_status, visibility_status, is_deleted, create_time, id),
    KEY idx_post_knowledge_relation_target
        (target_post_id, review_status, visibility_status, is_deleted, create_time, id),
    KEY idx_post_knowledge_relation_review (review_status, risk_level, create_time, id),
    KEY idx_post_knowledge_relation_proposer (proposer_uid, review_status, update_time, id),
    CONSTRAINT chk_post_knowledge_relation_type
        CHECK (relation_type IN ('DUPLICATE_OF', 'SUPERSEDES', 'CONTINUES', 'SUPPLEMENTS', 'PREREQUISITE_OF', 'CONTRADICTS')),
    CONSTRAINT chk_post_knowledge_relation_review
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_post_knowledge_relation_visibility
        CHECK (visibility_status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT chk_post_knowledge_relation_risk
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_post_knowledge_relation_distinct
        CHECK (source_post_id <> target_post_id),
    CONSTRAINT chk_post_knowledge_relation_reviewer
        CHECK (reviewer_uid IS NULL OR reviewer_uid <> proposer_uid),
    CONSTRAINT fk_post_knowledge_relation_source
        FOREIGN KEY (source_post_id) REFERENCES t_post_main(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_knowledge_relation_target
        FOREIGN KEY (target_post_id) REFERENCES t_post_main(id) ON DELETE CASCADE,
    CONSTRAINT fk_post_knowledge_relation_proposer
        FOREIGN KEY (proposer_uid) REFERENCES t_user_account(id) ON DELETE RESTRICT,
    CONSTRAINT fk_post_knowledge_relation_reviewer
        FOREIGN KEY (reviewer_uid) REFERENCES t_user_account(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_int_post_outcome (
    id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    uid BIGINT NOT NULL,
    outcome_type VARCHAR(20) NOT NULL,
    context_note VARCHAR(1000) NULL,
    result_note VARCHAR(2000) NULL,
    visibility VARCHAR(24) NOT NULL DEFAULT 'PRIVATE',
    publication_status VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    consented_at DATETIME(3) NULL,
    reviewer_uid BIGINT NULL,
    review_note VARCHAR(1000) NULL,
    reviewed_at DATETIME(3) NULL,
    follow_up_at DATETIME(3) NULL,
    outcome_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    revision INT NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    effective_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 AND outcome_status = 'ACTIVE' THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_post_outcome_current (uid, post_id, effective_guard),
    KEY idx_int_post_outcome_public
        (post_id, outcome_status, publication_status, is_deleted, outcome_type),
    KEY idx_int_post_outcome_follow_up
        (uid, outcome_status, is_deleted, follow_up_at, id),
    CONSTRAINT chk_int_post_outcome_type
        CHECK (outcome_type IN ('TRIED', 'WORKED', 'PARTIAL', 'DID_NOT_WORK', 'NOT_APPLICABLE')),
    CONSTRAINT chk_int_post_outcome_visibility
        CHECK (visibility IN ('PRIVATE', 'PUBLIC_ANONYMOUS', 'PUBLIC_ATTRIBUTED')),
    CONSTRAINT chk_int_post_outcome_publication
        CHECK (publication_status IN ('PRIVATE', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT chk_int_post_outcome_status
        CHECK (outcome_status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT chk_int_post_outcome_revision
        CHECK (revision > 0),
    CONSTRAINT chk_int_post_outcome_deleted
        CHECK (is_deleted IN (0, 1)),
    CONSTRAINT fk_int_post_outcome_post
        FOREIGN KEY (post_id) REFERENCES t_post_main(id) ON DELETE CASCADE,
    CONSTRAINT fk_int_post_outcome_user
        FOREIGN KEY (uid) REFERENCES t_user_account(id) ON DELETE RESTRICT,
    CONSTRAINT fk_int_post_outcome_reviewer
        FOREIGN KEY (reviewer_uid) REFERENCES t_user_account(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
