-- 10_question.sql
-- V2 question knowledge base.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_ai_extract_task (
    id              BIGINT       NOT NULL PRIMARY KEY,
    post_id         BIGINT       NOT NULL,
    task_type       VARCHAR(32)  NOT NULL COMMENT 'question_extract / summary',
    task_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 pending, 1 running, 2 succeeded, 3 failed',
    retry_count     INT          NOT NULL DEFAULT 0,
    question_count  INT          NOT NULL DEFAULT 0,
    error_message   VARCHAR(1000) NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_post_type_time (post_id, task_type, create_time),
    KEY idx_status_time (task_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI extract task';

CREATE TABLE IF NOT EXISTS t_interview_question (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    canonical_id       BIGINT       NULL,
    question_text      TEXT         NOT NULL,
    normalized_hash    VARCHAR(64)  NOT NULL,
    answer_hint        TEXT         NULL,
    company            VARCHAR(128) NULL,
    position           VARCHAR(128) NULL,
    interview_round    VARCHAR(64)  NULL,
    difficulty         VARCHAR(16)  NULL,
    confidence         DECIMAL(5,4) NOT NULL DEFAULT 0.5000,
    source_post_id     BIGINT       NOT NULL,
    source_author_uid  BIGINT       NOT NULL,
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '0 pending, 1 approved, 2 hidden',
    appear_count       INT          NOT NULL DEFAULT 1,
    quality_score      INT          NOT NULL DEFAULT 0,
    create_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_post_question_hash (source_post_id, normalized_hash),
    KEY idx_status_time (status, create_time),
    KEY idx_company_time (company, create_time),
    KEY idx_position_time (position, create_time),
    KEY idx_canonical (canonical_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Interview question';

CREATE TABLE IF NOT EXISTS t_interview_question_tag (
    id              BIGINT       NOT NULL PRIMARY KEY,
    question_id     BIGINT       NOT NULL,
    tag_id          BIGINT       NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_question_tag (question_id, tag_id),
    KEY idx_tag_question (tag_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Interview question tag';

CREATE TABLE IF NOT EXISTS t_user_question_progress (
    id              BIGINT       NOT NULL PRIMARY KEY,
    uid             BIGINT       NOT NULL,
    question_id     BIGINT       NOT NULL,
    progress_status VARCHAR(16)  NULL COMMENT 'todo / learning / mastered / review',
    favorite        TINYINT      NOT NULL DEFAULT 0,
    note            TEXT         NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_question (uid, question_id),
    KEY idx_uid_favorite_time (uid, favorite, update_time),
    KEY idx_uid_status_time (uid, progress_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User question progress';

CREATE TABLE IF NOT EXISTS t_company_alias (
    id                BIGINT       NOT NULL PRIMARY KEY,
    canonical_company VARCHAR(128) NOT NULL,
    alias             VARCHAR(128) NOT NULL,
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
    create_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_alias (alias),
    KEY idx_canonical (canonical_company, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Company alias';

CREATE TABLE IF NOT EXISTS t_user_prep_target (
    id              BIGINT       NOT NULL PRIMARY KEY,
    uid             BIGINT       NOT NULL,
    target_type     VARCHAR(16)  NOT NULL COMMENT 'company / position / tag',
    target_value    VARCHAR(128) NOT NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_target (uid, target_type, target_value),
    KEY idx_uid_time (uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User prep target';
