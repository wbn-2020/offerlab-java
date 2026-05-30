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
    exam_point         VARCHAR(255) NULL,
    reference_answer   TEXT         NULL,
    source_snippet     TEXT         NULL,
    quality_reason     VARCHAR(500) NULL,
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
    KEY idx_normalized_status (normalized_hash, status, source_post_id),
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
    mistake_reason  VARCHAR(32)  NULL COMMENT 'concept / project / memory / expression / careless / other',
    answer_draft    TEXT         NULL,
    star_story      TEXT         NULL,
    next_review_at  DATETIME(3)  NULL,
    last_reviewed_at DATETIME(3) NULL,
    review_count    INT          NOT NULL DEFAULT 0,
    review_interval_days INT     NOT NULL DEFAULT 1,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_question (uid, question_id),
    KEY idx_uid_update_time (uid, update_time),
    KEY idx_uid_favorite_time (uid, favorite, update_time),
    KEY idx_uid_status_time (uid, progress_status, update_time),
    KEY idx_uid_next_review (uid, next_review_at),
    KEY idx_uid_status_next_review (uid, progress_status, next_review_at),
    KEY idx_uid_reason_time (uid, mistake_reason, update_time),
    KEY idx_uid_status_question_time (uid, progress_status, question_id, update_time),
    KEY idx_uid_reason_question_time (uid, mistake_reason, question_id, update_time)
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
    interview_date  DATE         NULL,
    priority        VARCHAR(16)  NOT NULL DEFAULT 'medium' COMMENT 'low / medium / high / urgent',
    note            VARCHAR(300) NULL,
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_target (uid, target_type, target_value),
    KEY idx_uid_time (uid, create_time),
    KEY idx_uid_interview_priority (uid, interview_date, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User prep target';

CREATE TABLE IF NOT EXISTS t_mock_interview_session (
    id               BIGINT       NOT NULL PRIMARY KEY,
    uid              BIGINT       NOT NULL,
    company          VARCHAR(128) NULL,
    position         VARCHAR(128) NULL,
    difficulty       VARCHAR(16)  NULL,
    focus_tag        VARCHAR(64)  NULL,
    question_count   INT          NOT NULL DEFAULT 0,
    answered_count   INT          NOT NULL DEFAULT 0,
    total_score      INT          NOT NULL DEFAULT 0,
    duration_seconds INT          NOT NULL DEFAULT 0,
    status           VARCHAR(16)  NOT NULL DEFAULT 'started' COMMENT 'started / completed',
    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_uid_update_time (uid, update_time),
    KEY idx_uid_status_time (uid, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mock interview session';

CREATE TABLE IF NOT EXISTS t_mock_interview_answer (
    id           BIGINT       NOT NULL PRIMARY KEY,
    session_id   BIGINT       NOT NULL,
    uid          BIGINT       NOT NULL,
    question_id  BIGINT       NOT NULL,
    sequence_no  INT          NOT NULL,
    question_text_snapshot TEXT NULL,
    answer_hint_snapshot TEXT NULL,
    company_snapshot VARCHAR(128) NULL,
    position_snapshot VARCHAR(128) NULL,
    round_snapshot VARCHAR(64) NULL,
    difficulty_snapshot VARCHAR(16) NULL,
    answer_text  TEXT         NULL,
    self_review  VARCHAR(1000) NULL,
    score        INT          NOT NULL DEFAULT 0,
    ai_reviewed  TINYINT      NOT NULL DEFAULT 0,
    ai_score     INT          NULL,
    ai_completeness VARCHAR(300) NULL,
    ai_project_expression VARCHAR(300) NULL,
    ai_follow_up_suggestion VARCHAR(300) NULL,
    ai_review_provider VARCHAR(32) NULL,
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_session_question (session_id, question_id),
    KEY idx_session_sequence (session_id, sequence_no),
    KEY idx_uid_time (uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mock interview answer';
