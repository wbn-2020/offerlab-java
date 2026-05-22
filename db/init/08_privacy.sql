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
    create_time              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_searchable (searchable),
    KEY idx_profile_visibility (profile_visibility),
    KEY idx_intent_visibility (intent_visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User privacy settings';
