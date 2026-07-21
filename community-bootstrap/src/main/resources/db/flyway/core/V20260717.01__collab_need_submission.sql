-- Claimant delivery loop: add submission staging columns to content needs.
-- Introduces the SUBMITTED state (stored as need_status string, no DDL enum change)
-- plus columns to stage the claimant's pending deliverable and the reject reason.
SET NAMES utf8mb4;

ALTER TABLE t_collab_content_need
    ADD COLUMN submitted_by_uid            BIGINT        NULL COMMENT 'Claimant uid who submitted the pending deliverable',
    ADD COLUMN submitted_at                DATETIME(3)   NULL COMMENT 'When the pending deliverable was submitted',
    ADD COLUMN submission_resolution_type  VARCHAR(24)   NULL COMMENT 'Submitted deliverable type POST/QUESTION/SERIES',
    ADD COLUMN submission_resolution_id    BIGINT        NULL COMMENT 'Submitted deliverable target id',
    ADD COLUMN submission_note             VARCHAR(1000) NULL COMMENT 'Claimant submission note',
    ADD COLUMN reject_reason               VARCHAR(500)  NULL COMMENT 'Creator/moderator reason when a submission is returned';
