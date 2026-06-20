-- 20260615_interview_material_pack.sql
-- Non-destructive migration for personal interview material packs.
-- Review and run manually on an existing database.
SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_interview_material_pack (
    id                       BIGINT       NOT NULL PRIMARY KEY,
    uid                      BIGINT       NOT NULL,
    post_id                  BIGINT       NOT NULL,
    source_post_version      INT          NOT NULL DEFAULT 0,
    generation_status        VARCHAR(16)  NOT NULL DEFAULT 'SUCCEEDED',
    star_situation           TEXT         NULL,
    star_task                TEXT         NULL,
    star_action              TEXT         NULL,
    star_result              TEXT         NULL,
    resume_bullet_json       JSON         NULL,
    follow_up_question_json  JSON         NULL,
    technical_highlight_json JSON         NULL,
    missing_hint_json        JSON         NULL,
    user_note                VARCHAR(1000) NULL,
    saved_to_prep            TINYINT      NOT NULL DEFAULT 0,
    provider                 VARCHAR(32)  NULL,
    fallback_used            TINYINT      NOT NULL DEFAULT 1,
    create_time              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_uid_post (uid, post_id),
    KEY idx_uid_saved_time (uid, saved_to_prep, update_time),
    KEY idx_post_time (post_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Personal interview material pack';
