-- 05_analytics.sql
SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_ana_extracted_question;
CREATE TABLE t_ana_extracted_question (
    id              BIGINT       NOT NULL PRIMARY KEY,
    source_post_id  BIGINT       NOT NULL,
    question_text   VARCHAR(2000) NOT NULL,
    answer_summary  TEXT         NULL,
    exam_points     JSON         NULL,
    difficulty      TINYINT      NULL,
    company         VARCHAR(64)  NULL,
    position        VARCHAR(64)  NULL,
    similarity_hash CHAR(64)     NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_source_post (source_post_id),
    KEY idx_company_position (company, position),
    KEY idx_simhash (similarity_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 提取的面试题';

CREATE TABLE IF NOT EXISTS t_search_analytics_event (
    id           BIGINT       NOT NULL PRIMARY KEY,
    event_type   VARCHAR(32)  NOT NULL,
    uid          BIGINT       NULL,
    keyword      VARCHAR(100) NULL,
    company      VARCHAR(128) NULL,
    position     VARCHAR(128) NULL,
    post_type    INT          NULL,
    sort_type    VARCHAR(16)  NULL,
    result_count INT          NOT NULL DEFAULT 0,
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_event_time (event_type, create_time),
    KEY idx_keyword_time (keyword, create_time),
    KEY idx_company_time (company, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Search analytics events';

CREATE TABLE IF NOT EXISTS t_growth_event (
    id              BIGINT        NOT NULL PRIMARY KEY,
    event_type      VARCHAR(32)   NOT NULL,
    uid             BIGINT        NULL,
    domain          TINYINT       NULL,
    content_id      BIGINT        NULL,
    target_type     VARCHAR(32)   NULL,
    target_value    VARCHAR(128)  NULL,
    source_page     VARCHAR(128)  NULL,
    ext_json        JSON          NULL,
    create_time     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_growth_event_type_time (event_type, create_time),
    KEY idx_growth_event_domain_time (domain, create_time),
    KEY idx_growth_event_uid_time (uid, create_time),
    KEY idx_growth_event_content_time (content_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Community growth event log';

CREATE TABLE IF NOT EXISTS t_feed_recommend_support_stat (
    id BIGINT NOT NULL COMMENT 'snowflake id',
    viewer_uid BIGINT NULL COMMENT 'recommend feed viewer uid, null means anonymous',
    domain INT NULL COMMENT 'domain filter used by recommend feed; null means all domains',
    delivered_item_count INT NOT NULL DEFAULT 0 COMMENT 'count of items returned in this recommend feed response page, not viewport exposure',
    support_hit_item_count INT NOT NULL DEFAULT 0 COMMENT 'count of returned items in this recommend feed response page that matched the new creator support rule',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'record create time',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'record update time',
    PRIMARY KEY (id),
    KEY idx_feed_recommend_support_stat_create_time (create_time),
    KEY idx_feed_recommend_support_stat_domain_create_time (domain, create_time),
    KEY idx_feed_recommend_support_stat_viewer_create_time (viewer_uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Recommend feed new creator support delivery/hit stats per response page; counts are response items, not viewport exposure';
