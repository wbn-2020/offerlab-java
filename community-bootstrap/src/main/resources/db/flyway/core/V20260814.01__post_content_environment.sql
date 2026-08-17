-- Explicit content provenance for public distribution.
-- Existing rows remain fail-closed until an independently reviewed governance
-- run classifies them. Do not backfill COMMUNITY in this schema migration.
ALTER TABLE t_post_main
    ADD COLUMN content_environment VARCHAR(16) NOT NULL DEFAULT 'UNCLASSIFIED'
        COMMENT 'COMMUNITY/TEST/INTERNAL/UNCLASSIFIED'
        AFTER post_status,
    ADD KEY idx_post_environment_public
        (content_environment, is_deleted, post_status, visibility, create_time, id),
    ADD CONSTRAINT chk_post_content_environment CHECK (
        content_environment IN ('COMMUNITY', 'TEST', 'INTERNAL', 'UNCLASSIFIED')
    );
