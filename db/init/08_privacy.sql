-- 08_privacy.sql
-- User privacy settings.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_user_privacy_setting (
    user_id                  BIGINT      NOT NULL PRIMARY KEY,
    profile_visibility       VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    intent_visibility        VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    searchable               TINYINT     NOT NULL DEFAULT 1,
    interaction_notification TINYINT     NOT NULL DEFAULT 1,
    system_notification      TINYINT     NOT NULL DEFAULT 1,
    like_notification        TINYINT     NOT NULL DEFAULT 1,
    comment_notification     TINYINT     NOT NULL DEFAULT 1,
    follow_notification      TINYINT     NOT NULL DEFAULT 1,
    favorite_notification    TINYINT     NOT NULL DEFAULT 1,
    mention_notification     TINYINT     NOT NULL DEFAULT 1,
    accept_contact_request   TINYINT     NOT NULL DEFAULT 1,
    contact_request_policy   VARCHAR(16) NOT NULL DEFAULT 'following',
    contact_request_daily_limit INT      NULL,
    governance_reminder_notification TINYINT NOT NULL DEFAULT 1,
    governance_reminder_quiet_start_minute INT NULL,
    governance_reminder_quiet_end_minute INT NULL,
    governance_reminder_time_zone VARCHAR(64) NULL,
    create_time              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_searchable (searchable),
    KEY idx_profile_visibility (profile_visibility),
    KEY idx_intent_visibility (intent_visibility),
    KEY idx_contact_request_policy (contact_request_policy),
    CONSTRAINT chk_user_privacy_governance_reminder_quiet_window CHECK (
        governance_reminder_notification IN (0, 1)
        AND (
            (governance_reminder_quiet_start_minute IS NULL
                AND governance_reminder_quiet_end_minute IS NULL
                AND governance_reminder_time_zone IS NULL)
            OR (
                governance_reminder_quiet_start_minute BETWEEN 0 AND 1439
                AND governance_reminder_quiet_end_minute BETWEEN 0 AND 1439
                AND CHAR_LENGTH(TRIM(governance_reminder_time_zone)) BETWEEN 1 AND 64
            )
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User privacy settings';
