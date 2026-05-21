-- 03_interaction.sql
-- 互动域：评论、点赞、收藏
SET NAMES utf8mb4;
USE offerlab;

DROP TABLE IF EXISTS t_int_comment;
CREATE TABLE t_int_comment (
    id              BIGINT       NOT NULL PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    post_author_id  BIGINT       NOT NULL COMMENT '冗余：帖子作者',
    author_id       BIGINT       NOT NULL,
    root_id         BIGINT       NOT NULL DEFAULT 0,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    reply_to_uid    BIGINT       NULL,
    content         VARCHAR(2000) NOT NULL,
    like_count      INT          NOT NULL DEFAULT 0,
    comment_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2审核 3已删',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    KEY idx_post_root (post_id, root_id, create_time),
    KEY idx_author_time (author_id, create_time),
    KEY idx_post_author_time (post_author_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

DROP TABLE IF EXISTS t_int_like;
CREATE TABLE t_int_like (
    id               BIGINT       NOT NULL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    target_type      TINYINT      NOT NULL COMMENT '1帖子 2评论',
    target_id        BIGINT       NOT NULL,
    target_author_id BIGINT       NOT NULL,
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id, is_deleted),
    KEY idx_target (target_type, target_id, create_time),
    KEY idx_target_author (target_author_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞';

DROP TABLE IF EXISTS t_int_favorite;
CREATE TABLE t_int_favorite (
    id              BIGINT       NOT NULL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    post_id         BIGINT       NOT NULL,
    folder_id       BIGINT       NOT NULL DEFAULT 0,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_post (user_id, post_id, is_deleted),
    KEY idx_user_folder (user_id, folder_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';
