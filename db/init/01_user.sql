-- 01_user.sql
-- 用户域：账号、资料、关注关系、用户级计数器
-- 编码：utf8mb4
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS offerlab DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ----------------------------
-- 用户账号
-- ----------------------------
DROP TABLE IF EXISTS t_user_account;
CREATE TABLE t_user_account (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '用户ID（雪花）',
    email           VARCHAR(128) NOT NULL COMMENT '閭',
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
    intent_json     JSON         NULL COMMENT '姹傝亴鎰忓悜',
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
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '0关注中 1已取消',
    UNIQUE KEY uk_from_to (from_uid, to_uid),
    KEY idx_to_uid   (to_uid, create_time),
    KEY idx_from_uid (from_uid, create_time),
    KEY idx_following_page (from_uid, is_deleted, id),
    KEY idx_follower_page  (to_uid, is_deleted, id)
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

-- ----------------------------
-- 用户任务状态
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_user_task_state (
    id                   BIGINT       NOT NULL PRIMARY KEY COMMENT '任务状态ID（雪花）',
    uid                  BIGINT       NOT NULL COMMENT '用户ID',
    task_type            VARCHAR(16)  NOT NULL COMMENT '任务类型：ONBOARDING / DAILY',
    task_code            VARCHAR(64)  NOT NULL COMMENT '固定任务编码',
    task_date            DATE         NOT NULL COMMENT '任务日期桶，onboarding 使用锚点日期',
    completed            TINYINT      NOT NULL DEFAULT 0 COMMENT '0未完成 1已完成',
    complete_source      VARCHAR(64)  NULL COMMENT '完成来源',
    complete_ref_id      BIGINT       NULL COMMENT '完成关联对象ID',
    first_completed_time DATETIME(3)  NULL COMMENT '首次完成时间',
    create_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_task_scope_code_day (uid, task_type, task_code, task_date),
    KEY idx_user_task_scope_date (uid, task_type, task_date, completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户任务状态';
