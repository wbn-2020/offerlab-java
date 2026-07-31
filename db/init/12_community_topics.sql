SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_community_topic (
    id              BIGINT       NOT NULL PRIMARY KEY,
    slug            VARCHAR(64)  NOT NULL,
    topic_name      VARCHAR(64)  NOT NULL,
    description     VARCHAR(500) NULL,
    topic_type      VARCHAR(32)  NOT NULL DEFAULT 'custom' COMMENT 'tech_stack/scenario/resource/project/custom',
    cover_url       VARCHAR(512) NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    featured        TINYINT      NOT NULL DEFAULT 0,
    topic_status    TINYINT      NOT NULL DEFAULT 1 COMMENT '1 online 0 offline',
    created_by      BIGINT       NULL,
    updated_by      BIGINT       NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_topic_slug (slug, is_deleted),
    KEY idx_topic_status_sort (topic_status, sort_order, update_time),
    KEY idx_topic_featured_sort (featured, topic_status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community topic';

CREATE TABLE IF NOT EXISTS t_community_topic_tag (
    id              BIGINT       NOT NULL PRIMARY KEY,
    topic_id        BIGINT       NOT NULL,
    tag_id          BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_topic_tag (topic_id, tag_id),
    KEY idx_topic_tag_topic (topic_id),
    KEY idx_topic_tag_tag (tag_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community topic tag relation';

CREATE TABLE IF NOT EXISTS t_community_topic_follow (
    id              BIGINT       NOT NULL PRIMARY KEY,
    topic_id        BIGINT       NOT NULL,
    uid             BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_topic_follow_user (topic_id, uid),
    KEY idx_topic_follow_uid (uid, id),
    KEY idx_topic_follow_topic (topic_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community topic follow';
