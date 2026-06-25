SET NAMES utf8mb4;
USE offerlab;

CREATE TABLE IF NOT EXISTS t_user_task_state (
    id                   BIGINT       NOT NULL PRIMARY KEY,
    uid                  BIGINT       NOT NULL,
    task_type            VARCHAR(16)  NOT NULL,
    task_code            VARCHAR(64)  NOT NULL,
    task_date            DATE         NOT NULL,
    completed            TINYINT      NOT NULL DEFAULT 0,
    complete_source      VARCHAR(64)  NULL,
    complete_ref_id      BIGINT       NULL,
    first_completed_time DATETIME(3)  NULL,
    create_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_task_scope_code_day (uid, task_type, task_code, task_date),
    KEY idx_user_task_scope_date (uid, task_type, task_date, completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User onboarding and daily task state';
