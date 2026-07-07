-- 08_privacy.sql
-- User privacy settings.
SET NAMES utf8mb4;
USE offerlab;

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
    create_time              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_searchable (searchable),
    KEY idx_profile_visibility (profile_visibility),
    KEY idx_intent_visibility (intent_visibility),
    KEY idx_contact_request_policy (contact_request_policy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User privacy settings';
