-- 01_user.sql
-- 用户域：账号、资料、关注关系、用户级计数器
-- 编码：utf8mb4
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS offerlab DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE offerlab;

-- ----------------------------
-- 用户账号
-- ----------------------------
DROP TABLE IF EXISTS t_user_account;
CREATE TABLE t_user_account (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '用户ID（雪花）',
    email           VARCHAR(128) NOT NULL COMMENT '邮箱',
    password_hash   VARCHAR(128) NOT NULL COMMENT 'bcrypt hash',
    password_salt   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '盐（bcrypt 自带，可不用）',
    account_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2封禁 3未激活',
    last_login_time DATETIME(3)  NULL,
    last_login_ip   VARCHAR(64)  NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_email (email, is_deleted),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- ----------------------------
-- 用户资料 + 求职意向
-- ----------------------------
DROP TABLE IF EXISTS t_user_profile;
CREATE TABLE t_user_profile (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '同账号ID',
    nickname        VARCHAR(64)  NOT NULL,
    avatar_url      VARCHAR(512) NULL,
    bio             VARCHAR(255) NULL,
    intent_json     JSON         NULL COMMENT '求职意向',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    KEY idx_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料';

-- ----------------------------
-- 关注关系
-- ----------------------------
DROP TABLE IF EXISTS t_user_follow;
CREATE TABLE t_user_follow (
    id              BIGINT       NOT NULL PRIMARY KEY,
    from_uid        BIGINT       NOT NULL COMMENT '关注者',
    to_uid          BIGINT       NOT NULL COMMENT '被关注者',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '0关注中 1已取关',
    UNIQUE KEY uk_from_to (from_uid, to_uid),
    KEY idx_to_uid   (to_uid, create_time),
    KEY idx_from_uid (from_uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系';

-- ----------------------------
-- 用户级计数器
-- ----------------------------
DROP TABLE IF EXISTS t_user_counter;
CREATE TABLE t_user_counter (
    user_id         BIGINT       NOT NULL PRIMARY KEY,
    follower_count  BIGINT       NOT NULL DEFAULT 0,
    following_count BIGINT       NOT NULL DEFAULT 0,
    post_count      BIGINT       NOT NULL DEFAULT 0,
    like_received   BIGINT       NOT NULL DEFAULT 0,
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version         INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户级计数器';
