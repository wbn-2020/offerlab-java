-- Roll back only the registration-consent fields introduced on 2026-09-01.
-- Run only after reverting the application version that writes these fields.

ALTER TABLE t_user_account
    DROP COLUMN privacy_version,
    DROP COLUMN terms_version,
    DROP COLUMN terms_accepted_at;
