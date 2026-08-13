-- V29 keeps author controls in the existing feed-control fact table. MySQL
-- has no partial unique index, so AUTHOR controls use the established
-- (uid, post_id) uniqueness slot with a deterministic negative post_id.
-- migration-safety: allow ALTER_MODIFY reason=AUTHOR controls persist until explicit removal, so expires_at must allow NULL.
ALTER TABLE t_feed_feedback_preference
    DROP CHECK chk_feed_feedback_action,
    DROP CHECK chk_feed_feedback_target_type,
    MODIFY COLUMN expires_at DATETIME(3) NULL,
    ADD CONSTRAINT chk_feed_feedback_action
        CHECK (action IN ('HIDE', 'LESS_LIKE_THIS', 'BLOCK_AUTHOR')),
    ADD CONSTRAINT chk_feed_feedback_target_type
        CHECK (target_type IN ('POST', 'DOMAIN', 'AUTHOR')),
    ADD CONSTRAINT chk_feed_feedback_action_target_expiry
        CHECK (
            (action IN ('HIDE', 'LESS_LIKE_THIS')
                AND target_type = 'POST'
                AND post_id > 0
                AND target_id = post_id
                AND expires_at IS NOT NULL)
            OR (action = 'LESS_LIKE_THIS'
                AND target_type = 'DOMAIN'
                AND post_id > 0
                AND target_id > 0
                AND expires_at IS NOT NULL)
            OR (action = 'BLOCK_AUTHOR'
                AND target_type = 'AUTHOR'
                AND target_id > 0
                AND post_id = -target_id
                AND expires_at IS NULL)
        );
