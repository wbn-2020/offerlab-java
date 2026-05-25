-- 09_moderation.sql
-- Minimal post report and moderation workflow.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_post_report (
    id             BIGINT        NOT NULL PRIMARY KEY,
    post_id        BIGINT        NOT NULL,
    reporter_uid   BIGINT        NOT NULL,
    reason         VARCHAR(64)   NOT NULL DEFAULT 'OTHER',
    detail         VARCHAR(1000) NULL,
    report_status  TINYINT       NOT NULL DEFAULT 0 COMMENT '0 pending, 1 approved, 2 rejected',
    reviewer_uid   BIGINT        NULL,
    review_note    VARCHAR(1000) NULL,
    review_time    DATETIME(3)   NULL,
    create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status_time (report_status, create_time),
    KEY idx_post_time (post_id, create_time),
    KEY idx_reporter_time (reporter_uid, create_time),
    KEY idx_post_reporter_status (post_id, reporter_uid, report_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post reports';

CREATE TABLE IF NOT EXISTS t_comment_report (
    id             BIGINT        NOT NULL PRIMARY KEY,
    comment_id     BIGINT        NOT NULL,
    post_id        BIGINT        NOT NULL,
    reporter_uid   BIGINT        NOT NULL,
    reason         VARCHAR(64)   NOT NULL DEFAULT 'OTHER',
    detail         VARCHAR(1000) NULL,
    report_status  TINYINT       NOT NULL DEFAULT 0 COMMENT '0 pending, 1 approved, 2 rejected',
    reviewer_uid   BIGINT        NULL,
    review_note    VARCHAR(1000) NULL,
    review_time    DATETIME(3)   NULL,
    create_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status_time (report_status, create_time),
    KEY idx_comment_time (comment_id, create_time),
    KEY idx_post_time (post_id, create_time),
    KEY idx_reporter_time (reporter_uid, create_time),
    KEY idx_comment_reporter_status (comment_id, reporter_uid, report_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Comment reports';
