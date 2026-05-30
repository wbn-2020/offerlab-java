-- 20260528_mock_interview.sql
-- Non-destructive migration for lightweight mock interview practice sessions.
-- Review before running on an existing database.
SET NAMES utf8mb4;
USE offerlab;

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
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_session_question (session_id, question_id),
    KEY idx_session_sequence (session_id, sequence_no),
    KEY idx_uid_time (uid, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Mock interview answer';
