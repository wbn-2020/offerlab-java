-- 01_user.sql
-- 鐢ㄦ埛鍩燂細璐﹀彿銆佽祫鏂欍€佸叧娉ㄥ叧绯汇€佺敤鎴风骇璁℃暟鍣?
-- 缂栫爜锛歶tf8mb4
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS offerlab DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ----------------------------
-- 鐢ㄦ埛璐﹀彿
-- ----------------------------
DROP TABLE IF EXISTS t_user_account;
CREATE TABLE t_user_account (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '鐢ㄦ埛ID锛堥洩鑺憋級',
    email           VARCHAR(128) NOT NULL COMMENT '閭',
    password_hash   VARCHAR(128) NOT NULL COMMENT 'bcrypt hash',
    password_salt   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '鐩愶紙bcrypt 鑷甫锛屽彲涓嶇敤锛?,
    account_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '1姝ｅ父 2灏佺 3鏈縺娲?,
    last_login_time DATETIME(3)  NULL,
    last_login_ip   VARCHAR(64)  NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uk_email (email, is_deleted),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛璐﹀彿';

-- ----------------------------
-- 鐢ㄦ埛璧勬枡 + 姹傝亴鎰忓悜
-- ----------------------------
DROP TABLE IF EXISTS t_user_profile;
CREATE TABLE t_user_profile (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '鍚岃处鍙稩D',
    nickname        VARCHAR(64)  NOT NULL,
    avatar_url      VARCHAR(512) NULL,
    bio             VARCHAR(255) NULL,
    intent_json     JSON         NULL COMMENT '姹傝亴鎰忓悜',
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    KEY idx_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛璧勬枡';

-- ----------------------------
-- 鍏虫敞鍏崇郴
-- ----------------------------
DROP TABLE IF EXISTS t_user_follow;
CREATE TABLE t_user_follow (
    id              BIGINT       NOT NULL PRIMARY KEY,
    from_uid        BIGINT       NOT NULL COMMENT '鍏虫敞鑰?,
    to_uid          BIGINT       NOT NULL COMMENT '琚叧娉ㄨ€?,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '0鍏虫敞涓?1宸插彇鍏?,
    UNIQUE KEY uk_from_to (from_uid, to_uid),
    KEY idx_to_uid   (to_uid, create_time),
    KEY idx_from_uid (from_uid, create_time),
    KEY idx_following_page (from_uid, is_deleted, id),
    KEY idx_follower_page  (to_uid, is_deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鍏虫敞鍏崇郴';

-- ----------------------------
-- 鐢ㄦ埛绾ц鏁板櫒
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛绾ц鏁板櫒';

-- ----------------------------
-- 鐢ㄦ埛浠诲姟鐘舵€?
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_user_task_state (
    id                   BIGINT       NOT NULL PRIMARY KEY COMMENT '浠诲姟鐘舵€両D锛堥洩鑺憋級',
    uid                  BIGINT       NOT NULL COMMENT '鐢ㄦ埛ID',
    task_type            VARCHAR(16)  NOT NULL COMMENT '浠诲姟绫诲瀷锛歄NBOARDING / DAILY',
    task_code            VARCHAR(64)  NOT NULL COMMENT '鍥哄畾浠诲姟缂栫爜',
    task_date            DATE         NOT NULL COMMENT '浠诲姟鏃ユ湡妗讹紝onboarding 鐢ㄩ敋鐐规棩鏈?,
    completed            TINYINT      NOT NULL DEFAULT 0 COMMENT '0鏈畬鎴?1宸插畬鎴?,
    complete_source      VARCHAR(64)  NULL COMMENT '瀹屾垚鏉ユ簮',
    complete_ref_id      BIGINT       NULL COMMENT '瀹屾垚鍏宠仈瀵硅薄ID',
    first_completed_time DATETIME(3)  NULL COMMENT '棣栨瀹屾垚鏃堕棿',
    create_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_task_scope_code_day (uid, task_type, task_code, task_date),
    KEY idx_user_task_scope_date (uid, task_type, task_date, completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鐢ㄦ埛浠诲姟鐘舵€?;
