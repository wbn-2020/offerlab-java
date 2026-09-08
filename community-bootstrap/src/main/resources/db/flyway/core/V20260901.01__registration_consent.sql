-- Registration consent audit fields.
-- Non-destructive: existing accounts remain unchanged and new registrations
-- must record the accepted policy versions.

ALTER TABLE t_user_account
    ADD COLUMN terms_accepted_at DATETIME(3) NULL COMMENT '服务条款与隐私政策确认时间' AFTER last_login_ip,
    ADD COLUMN terms_version VARCHAR(32) NULL COMMENT '服务条款版本' AFTER terms_accepted_at,
    ADD COLUMN privacy_version VARCHAR(32) NULL COMMENT '隐私政策版本' AFTER terms_version;
