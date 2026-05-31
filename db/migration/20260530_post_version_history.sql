SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_post_version_history (
    id                BIGINT       NOT NULL PRIMARY KEY,
    post_id           BIGINT       NOT NULL,
    author_id         BIGINT       NOT NULL,
    editor_uid        BIGINT       NOT NULL,
    base_version      INT          NOT NULL DEFAULT 0,
    post_type         TINYINT      NOT NULL,
    title             VARCHAR(255) NOT NULL,
    content           LONGTEXT     NOT NULL,
    cover_url         VARCHAR(512) NULL,
    visibility        TINYINT      NOT NULL DEFAULT 1,
    post_status       TINYINT      NOT NULL DEFAULT 1,
    ext_json          JSON         NULL,
    tag_snapshot_json JSON         NULL,
    change_summary    VARCHAR(255) NULL,
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_post_time (post_id, create_time),
    KEY idx_author_time (author_id, create_time),
    KEY idx_editor_time (editor_uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Post version history';
