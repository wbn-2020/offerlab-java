-- Add a dedicated optimistic-lock revision for operation-topic drafts and a
-- stable logical section key shared by all items in the same section.

ALTER TABLE t_operation_topic
    ADD COLUMN draft_revision INT NOT NULL DEFAULT 0 AFTER current_version,
    ADD CONSTRAINT chk_operation_topic_draft_revision
        CHECK (draft_revision >= 0);

ALTER TABLE t_operation_topic_section
    ADD COLUMN section_key VARCHAR(64) NULL AFTER section_title;

UPDATE t_operation_topic_section
SET section_key = CONCAT(
    'legacy-',
    LEFT(
        SHA2(CONCAT(topic_id, ':', COALESCE(section_title, '')), 256),
        57
    )
)
WHERE section_key IS NULL OR section_key = '';

-- migration-safety: allow ALTER_MODIFY reason=section_key is fully backfilled before enforcing the NOT NULL invariant
ALTER TABLE t_operation_topic_section
    MODIFY COLUMN section_key VARCHAR(64) NOT NULL,
    ADD CONSTRAINT chk_operation_topic_section_key
        CHECK (CHAR_LENGTH(section_key) BETWEEN 2 AND 64);
