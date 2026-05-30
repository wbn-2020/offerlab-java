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
