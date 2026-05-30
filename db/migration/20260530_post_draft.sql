SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_post_draft (
    id              BIGINT       NOT NULL PRIMARY KEY,
    uid             BIGINT       NOT NULL,
    source_post_id  BIGINT       NULL COMMENT '编辑已有帖子时关联的帖子 ID',
    post_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '1面经 2技术博客 3题解 4求职问答',
    title           VARCHAR(255) NULL,
    content         LONGTEXT     NULL,
    cover_url       VARCHAR(512) NULL,
    visibility      TINYINT      NOT NULL DEFAULT 1,
    ext_json        JSON         NULL,
    tag_ids_json    JSON         NULL,
    tag_names_json  JSON         NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_uid_update_time (uid, update_time),
    KEY idx_uid_source_post (uid, source_post_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子服务端草稿';
