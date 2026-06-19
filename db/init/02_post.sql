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
    domain          TINYINT      GENERATED ALWAYS AS (CASE JSON_UNQUOTE(JSON_EXTRACT(ext_json, '$.domain')) WHEN '2' THEN 2 WHEN '3' THEN 3 WHEN '4' THEN 4 WHEN '5' THEN 5 ELSE 1 END) VIRTUAL,
    ext_json        JSON         NOT NULL,
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_company (company),
    KEY idx_position (position),
    KEY idx_company_result (company, interview_result),
    KEY idx_post_extension_domain_post (domain, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子扩展';

DROP TABLE IF EXISTS t_tag;
CREATE TABLE t_tag (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tag_name        VARCHAR(64)  NOT NULL,
    tag_type        TINYINT      NOT NULL COMMENT '1技术栈 2公司 3岗位 4自定义',
    use_count       BIGINT       NOT NULL DEFAULT 0,
    is_official     TINYINT      NOT NULL DEFAULT 0,
    tag_status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用/合并',
    recommended     TINYINT      NOT NULL DEFAULT 0 COMMENT '1推荐标签',
    synonyms        VARCHAR(512) NULL COMMENT '同义词，逗号分隔',
    merge_target_id BIGINT       NULL COMMENT '合并目标标签',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tag_name (tag_name, is_deleted),
    KEY idx_type_count (tag_type, use_count),
    KEY idx_tag_status_recommend (tag_status, recommended, use_count),
    KEY idx_tag_merge_target (merge_target_id)
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

DROP TABLE IF EXISTS t_community_topic;
CREATE TABLE t_community_topic (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区专题';

DROP TABLE IF EXISTS t_community_topic_tag;
CREATE TABLE t_community_topic_tag (
    id              BIGINT       NOT NULL PRIMARY KEY,
    topic_id        BIGINT       NOT NULL,
    tag_id          BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_topic_tag (topic_id, tag_id),
    KEY idx_topic_tag_topic (topic_id),
    KEY idx_topic_tag_tag (tag_id, topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区专题-标签关联';

DROP TABLE IF EXISTS t_community_topic_follow;
CREATE TABLE t_community_topic_follow (
    id              BIGINT       NOT NULL PRIMARY KEY,
    topic_id        BIGINT       NOT NULL,
    uid             BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_topic_follow_user (topic_id, uid),
    KEY idx_topic_follow_uid (uid, id),
    KEY idx_topic_follow_topic (topic_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='community topic follow';

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

DROP TABLE IF EXISTS t_post_draft;
CREATE TABLE t_post_draft (
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

DROP TABLE IF EXISTS t_post_version_history;
CREATE TABLE t_post_version_history (
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

DROP TABLE IF EXISTS t_search_index_retry_task;
CREATE TABLE t_search_index_retry_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    dedup_key       VARCHAR(128) NOT NULL,
    post_id         BIGINT       NOT NULL,
    operation       VARCHAR(16)  NOT NULL COMMENT 'INDEX or DELETE',
    task_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 pending 1 done 2 failed 3 running',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3)  NULL,
    lock_owner      VARCHAR(128) NULL,
    lock_until      DATETIME(3)  NULL,
    last_error      VARCHAR(500) NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_search_index_retry_dedup (dedup_key),
    KEY idx_search_index_retry_due (task_status, next_retry_time),
    KEY idx_search_index_retry_lock (lock_owner, lock_until),
    KEY idx_search_index_retry_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='search index retry tasks';
