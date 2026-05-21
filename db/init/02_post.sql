-- 02_post.sql
-- 内容域：帖子、扩展信息、标签
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_post_main;
CREATE TABLE t_post_main (
    id              BIGINT       NOT NULL PRIMARY KEY,
    author_id       BIGINT       NOT NULL,
    post_type       TINYINT      NOT NULL COMMENT '1面经 2技术博客 3题解 4求职问答',
    title           VARCHAR(255) NOT NULL,
    content         LONGTEXT     NOT NULL,
    cover_url       VARCHAR(512) NULL,
    visibility      TINYINT      NOT NULL DEFAULT 1 COMMENT '1公开 2仅自己 3粉丝可见',
    post_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1已发布 2草稿 3审核中 4已下架',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    KEY idx_author_time (author_id, create_time),
    KEY idx_type_time   (post_type, create_time),
    KEY idx_status_time (post_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子主表';

DROP TABLE IF EXISTS t_post_extension;
CREATE TABLE t_post_extension (
    post_id         BIGINT       NOT NULL PRIMARY KEY,
    post_type       TINYINT      NOT NULL,
    company         VARCHAR(64)  GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.company'))) VIRTUAL,
    position        VARCHAR(64)  GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.position'))) VIRTUAL,
    years_of_exp    INT          GENERATED ALWAYS AS (JSON_EXTRACT(ext_json, '$.yearsOfExp')) VIRTUAL,
    interview_result TINYINT     GENERATED ALWAYS AS (JSON_EXTRACT(ext_json, '$.interviewResult')) VIRTUAL,
    ext_json        JSON         NOT NULL,
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_company (company),
    KEY idx_position (position),
    KEY idx_company_result (company, interview_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子扩展';

DROP TABLE IF EXISTS t_tag;
CREATE TABLE t_tag (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tag_name        VARCHAR(64)  NOT NULL,
    tag_type        TINYINT      NOT NULL COMMENT '1技术栈 2公司 3岗位 4自定义',
    use_count       BIGINT       NOT NULL DEFAULT 0,
    is_official     TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tag_name (tag_name, is_deleted),
    KEY idx_type_count (tag_type, use_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签库';

DROP TABLE IF EXISTS t_post_tag_ref;
CREATE TABLE t_post_tag_ref (
    id              BIGINT       NOT NULL PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    tag_id          BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_post_tag (post_id, tag_id),
    KEY idx_tag_post (tag_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子-标签关联';

DROP TABLE IF EXISTS t_post_counter;
CREATE TABLE t_post_counter (
    post_id         BIGINT       NOT NULL PRIMARY KEY,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    like_count      BIGINT       NOT NULL DEFAULT 0,
    comment_count   BIGINT       NOT NULL DEFAULT 0,
    favorite_count  BIGINT       NOT NULL DEFAULT 0,
    share_count     BIGINT       NOT NULL DEFAULT 0,
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version         INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子计数器';
