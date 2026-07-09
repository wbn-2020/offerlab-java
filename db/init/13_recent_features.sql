SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_domain_moderator (
    id BIGINT NOT NULL PRIMARY KEY,
    uid BIGINT NOT NULL,
    domain TINYINT NOT NULL COMMENT '1 tech, 2 career, 3 reading, 4 lifestyle, 5 investment',
    enabled TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_domain_moderator_uid_domain (uid, domain),
    KEY idx_domain_moderator_domain_enabled (domain, enabled),
    KEY idx_domain_moderator_uid_enabled (uid, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Domain-scoped community moderators';

CREATE TABLE IF NOT EXISTS t_content_series (
    id BIGINT NOT NULL PRIMARY KEY,
    creator_uid BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    domain TINYINT NOT NULL,
    cover_url VARCHAR(512) NULL,
    visibility TINYINT NOT NULL DEFAULT 2,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_content_series_creator_update (creator_uid, update_time, id),
    KEY idx_content_series_domain_update (domain, update_time, id),
    KEY idx_content_series_public_creator (visibility, creator_uid, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-owned content series';

CREATE TABLE IF NOT EXISTS t_content_series_post (
    id BIGINT NOT NULL PRIMARY KEY,
    series_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_content_series_post (series_id, post_id),
    KEY idx_content_series_post_series_sort (series_id, sort_order, id),
    KEY idx_content_series_post_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post membership in a content series';

CREATE TABLE IF NOT EXISTS t_creator_representative_post (
    id BIGINT NOT NULL PRIMARY KEY,
    creator_uid BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_creator_representative_post (creator_uid, post_id),
    KEY idx_creator_representative_active (creator_uid, is_deleted, sort_order, id),
    KEY idx_creator_representative_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Creator profile representative post links';

CREATE TABLE IF NOT EXISTS t_expert_cert_application (
    id BIGINT NOT NULL PRIMARY KEY,
    applicant_uid BIGINT NOT NULL,
    domain TINYINT NOT NULL,
    status INT NOT NULL DEFAULT 10,
    evidence_summary VARCHAR(500) NOT NULL,
    evidence_links_json TEXT NULL,
    eligibility_passed TINYINT NOT NULL DEFAULT 0,
    eligibility_summary VARCHAR(500) NULL,
    eligibility_snapshot_json TEXT NULL,
    risk_acknowledged TINYINT NOT NULL DEFAULT 0,
    risk_warning VARCHAR(500) NULL,
    reviewer_uid BIGINT NULL,
    review_note VARCHAR(500) NULL,
    review_time DATETIME(3) NULL,
    revoked_by BIGINT NULL,
    revoke_note VARCHAR(500) NULL,
    revoked_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 AND status IN (10, 20) THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_expert_cert_active_guard (applicant_uid, domain, active_guard),
    KEY idx_expert_cert_applicant_domain (applicant_uid, domain, status, update_time),
    KEY idx_expert_cert_review_queue (domain, status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Expert certification pilot applications';

CREATE TABLE IF NOT EXISTS t_operation_curation_item (
    id BIGINT NOT NULL PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL COMMENT 'POST',
    source_id BIGINT NOT NULL,
    item_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED',
    sort_order INT NOT NULL DEFAULT 100,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_operation_curation_source (source_type, source_id, active_guard),
    KEY idx_operation_curation_status_sort (item_status, sort_order, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations curation pool';

CREATE TABLE IF NOT EXISTS t_operation_slot (
    id BIGINT NOT NULL PRIMARY KEY,
    slot_code VARCHAR(64) NOT NULL,
    slot_name VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    slot_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 100,
    default_limit TINYINT NOT NULL DEFAULT 3,
    starts_at DATETIME(3) NULL,
    ends_at DATETIME(3) NULL,
    preview_token VARCHAR(64) NULL,
    current_version INT NOT NULL DEFAULT 0,
    published_snapshot_json JSON NULL,
    rollback_snapshot_json JSON NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_slot_code (slot_code, is_deleted),
    KEY idx_operation_slot_status_sort (slot_status, sort_order, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations slot';

CREATE TABLE IF NOT EXISTS t_operation_slot_item (
    id BIGINT NOT NULL PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'POST / OPERATION_TOPIC',
    source_id BIGINT NOT NULL,
    item_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 100,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_operation_slot_source (slot_id, source_type, source_id, active_guard),
    KEY idx_operation_slot_item_slot_sort (slot_id, item_status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations slot item';

CREATE TABLE IF NOT EXISTS t_operation_topic (
    id BIGINT NOT NULL PRIMARY KEY,
    slug VARCHAR(64) NOT NULL,
    topic_name VARCHAR(64) NOT NULL,
    description VARCHAR(500) NULL,
    operation_type VARCHAR(16) NOT NULL DEFAULT 'TOPIC',
    cover_url VARCHAR(512) NULL,
    domain TINYINT NULL,
    topic_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 100,
    starts_at DATETIME(3) NULL,
    ends_at DATETIME(3) NULL,
    preview_token VARCHAR(64) NULL,
    current_version INT NOT NULL DEFAULT 0,
    published_snapshot_json JSON NULL,
    rollback_snapshot_json JSON NULL,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_topic_slug (slug, is_deleted),
    KEY idx_operation_topic_status_sort (topic_status, sort_order, update_time),
    KEY idx_operation_topic_type_status (operation_type, topic_status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations topic and event';

CREATE TABLE IF NOT EXISTS t_operation_topic_section (
    id BIGINT NOT NULL PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    section_title VARCHAR(64) NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'POST',
    source_id BIGINT NOT NULL,
    section_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 100,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_operation_topic_section_topic (topic_id, section_status, sort_order),
    KEY idx_operation_topic_section_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations topic section';

CREATE TABLE IF NOT EXISTS t_content_assist_record (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'snowflake id',
    uid BIGINT NOT NULL COMMENT 'caller user id',
    scene VARCHAR(32) NOT NULL COMMENT 'WRITING / QUALITY_SCORE / TAG_TOPIC_SUGGESTIONS',
    provider VARCHAR(32) NOT NULL COMMENT 'deepseek / rules',
    assist_status VARCHAR(32) NOT NULL COMMENT 'RULE_ONLY / AI_SUCCESS / AI_FALLBACK',
    domain INT NULL COMMENT 'post domain snapshot',
    content_length INT NOT NULL DEFAULT 0 COMMENT 'draft body length only',
    content_hash CHAR(64) NOT NULL COMMENT 'sha256 hash of draft body',
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    estimated_cost_micros BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_content_assist_scene_time (scene, create_time),
    KEY idx_content_assist_status_time (assist_status, create_time),
    KEY idx_content_assist_provider_time (provider, create_time),
    KEY idx_content_assist_uid_time (uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='content assist metadata only, no raw draft body stored';
