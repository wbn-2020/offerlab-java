-- 20260703_operation_curation.sql
-- Non-destructive migration for community operations curation workflow.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_operation_curation_item (
    id              BIGINT       NOT NULL PRIMARY KEY,
    source_type     VARCHAR(32)  NOT NULL COMMENT 'POST',
    source_id       BIGINT       NOT NULL,
    item_status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED',
    sort_order      INT          NOT NULL DEFAULT 100,
    note            VARCHAR(500) NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_curation_source (source_type, source_id, is_deleted),
    KEY idx_operation_curation_status_sort (item_status, sort_order, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations curation pool';

CREATE TABLE IF NOT EXISTS t_operation_slot (
    id              BIGINT       NOT NULL PRIMARY KEY,
    slot_code       VARCHAR(64)  NOT NULL,
    slot_name       VARCHAR(64)  NOT NULL,
    description     VARCHAR(500) NULL,
    slot_status     VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PREVIEW / PUBLISHED / OFFLINE; ARCHIVED is topic-only',
    sort_order      INT          NOT NULL DEFAULT 100,
    default_limit   TINYINT      NOT NULL DEFAULT 3,
    starts_at       DATETIME(3)  NULL,
    ends_at         DATETIME(3)  NULL,
    preview_token   VARCHAR(64)  NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_slot_code (slot_code, is_deleted),
    KEY idx_operation_slot_status_sort (slot_status, sort_order, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations slot';

CREATE TABLE IF NOT EXISTS t_operation_slot_item (
    id              BIGINT       NOT NULL PRIMARY KEY,
    slot_id         BIGINT       NOT NULL,
    source_type     VARCHAR(32)  NOT NULL COMMENT 'POST / OPERATION_TOPIC',
    source_id       BIGINT       NOT NULL,
    item_status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED',
    sort_order      INT          NOT NULL DEFAULT 100,
    note            VARCHAR(500) NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_slot_source (slot_id, source_type, source_id, is_deleted),
    KEY idx_operation_slot_item_slot_sort (slot_id, item_status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations slot item';

CREATE TABLE IF NOT EXISTS t_operation_topic (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    slug                    VARCHAR(64)  NOT NULL,
    topic_name              VARCHAR(64)  NOT NULL,
    description             VARCHAR(500) NULL,
    operation_type          VARCHAR(16)  NOT NULL DEFAULT 'TOPIC' COMMENT 'TOPIC / EVENT',
    cover_url               VARCHAR(512) NULL,
    domain                  TINYINT      NULL,
    topic_status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PREVIEW / PUBLISHED / OFFLINE / ARCHIVED',
    sort_order              INT          NOT NULL DEFAULT 100,
    starts_at               DATETIME(3)  NULL,
    ends_at                 DATETIME(3)  NULL,
    preview_token           VARCHAR(64)  NULL,
    current_version         INT          NOT NULL DEFAULT 0,
    published_snapshot_json JSON          NULL,
    rollback_snapshot_json  JSON          NULL,
    note                    VARCHAR(500) NULL,
    created_by              BIGINT       NULL,
    updated_by              BIGINT       NULL,
    create_time             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted              TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_operation_topic_slug (slug, is_deleted),
    KEY idx_operation_topic_status_sort (topic_status, sort_order, update_time),
    KEY idx_operation_topic_type_status (operation_type, topic_status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations topic and event';

CREATE TABLE IF NOT EXISTS t_operation_topic_section (
    id              BIGINT       NOT NULL PRIMARY KEY,
    topic_id        BIGINT       NOT NULL,
    section_title   VARCHAR(64)  NULL,
    source_type     VARCHAR(32)  NOT NULL COMMENT 'POST',
    source_id       BIGINT       NOT NULL,
    section_status  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED',
    sort_order      INT          NOT NULL DEFAULT 100,
    note            VARCHAR(500) NULL,
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_operation_topic_section_topic (topic_id, section_status, sort_order),
    KEY idx_operation_topic_section_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community operations topic section';
