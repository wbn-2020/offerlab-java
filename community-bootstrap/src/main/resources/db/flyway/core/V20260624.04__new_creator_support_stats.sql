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
