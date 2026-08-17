-- AUTHORIZED CONTROLLED ENVIRONMENT ONLY.
-- Replace the placeholder INSERT with exact rows from the two-person-approved
-- CSV. This template hard-stops before writes when the approved snapshot is
-- incomplete or stale. Keep the final ROLLBACK for rehearsal. A separately
-- reviewed execution copy may replace only that final statement with COMMIT.

SET @governance_batch_id = '__REQUIRED_BATCH_ID__';
-- Set this to the exact number of rows in the reviewed CSV. NULL is intentional:
-- the template must not execute until a reviewer supplies the count.
SET @expected_approved_count = NULL;

CREATE TEMPORARY TABLE approved_post_environment (
    post_id BIGINT NOT NULL PRIMARY KEY,
    expected_old_environment VARCHAR(16) NOT NULL,
    approved_environment VARCHAR(16) NOT NULL,
    reviewer_1 VARCHAR(128) NOT NULL,
    reviewer_2 VARCHAR(128) NOT NULL,
    evidence_reference VARCHAR(512) NOT NULL,
    decision_reason VARCHAR(1000) NOT NULL,
    decision_status VARCHAR(32) NOT NULL,
    CHECK (approved_environment IN ('COMMUNITY', 'TEST', 'INTERNAL', 'UNCLASSIFIED')),
    CHECK (reviewer_1 <> reviewer_2)
);

-- INSERT INTO approved_post_environment (...) VALUES
-- (__EXACT_APPROVED_ROWS_ONLY__);

CREATE TEMPORARY TABLE content_environment_preflight_guard (
    assertion_name VARCHAR(128) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CHECK (passed = 1)
);

-- Each INSERT fails with a constraint error when its predicate is false. No
-- transaction or data write begins until all assertions pass.
INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'batch_id_and_expected_count',
       CASE
           WHEN @governance_batch_id IS NOT NULL
            AND TRIM(@governance_batch_id) <> ''
            AND @governance_batch_id <> '__REQUIRED_BATCH_ID__'
            AND @expected_approved_count IS NOT NULL
            AND @expected_approved_count > 0
           THEN 1 ELSE 0
       END;

INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'approved_backup_table_is_preprovisioned',
       CASE WHEN (
           SELECT COUNT(*)
           FROM information_schema.tables
           WHERE table_schema = DATABASE()
             AND table_name = 't_post_content_environment_backup'
       ) = 1 THEN 1 ELSE 0 END;

INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'exact_approved_count',
       CASE WHEN (SELECT COUNT(*) FROM approved_post_environment) = @expected_approved_count
            THEN 1 ELSE 0 END;

INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'approved_rows_are_reviewed_and_final',
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM approved_post_environment
           WHERE expected_old_environment NOT IN ('COMMUNITY', 'TEST', 'INTERNAL', 'UNCLASSIFIED')
              OR approved_environment NOT IN ('COMMUNITY', 'TEST', 'INTERNAL')
              OR TRIM(reviewer_1) = ''
              OR TRIM(reviewer_2) = ''
              OR reviewer_1 = reviewer_2
              OR TRIM(evidence_reference) = ''
              OR TRIM(decision_reason) = ''
              OR decision_status <> 'APPROVED'
       ) THEN 1 ELSE 0 END;

INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'no_prior_backup_for_batch',
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM t_post_content_environment_backup
           WHERE governance_batch_id = @governance_batch_id
       ) THEN 1 ELSE 0 END;

INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'approved_rows_match_current_database',
       CASE WHEN (
           SELECT COUNT(*)
           FROM t_post_main p
           JOIN approved_post_environment a
             ON a.post_id = p.id
            AND a.expected_old_environment = p.content_environment
       ) = @expected_approved_count THEN 1 ELSE 0 END;

-- This is a release-completion script, not a partial classifier. Every
-- historical UNCLASSIFIED row must appear in the approved exact-ID snapshot.
INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'no_unreviewed_unclassified_rows',
       CASE WHEN NOT EXISTS (
           SELECT 1
           FROM t_post_main p
           LEFT JOIN approved_post_environment a ON a.post_id = p.id
           WHERE p.content_environment = 'UNCLASSIFIED'
             AND a.post_id IS NULL
       ) THEN 1 ELSE 0 END;

SELECT approved_environment, COUNT(*) AS approved_count
FROM approved_post_environment
GROUP BY approved_environment
ORDER BY approved_environment;

SELECT a.post_id, a.expected_old_environment, p.content_environment AS actual_old_environment
FROM approved_post_environment a
LEFT JOIN t_post_main p ON p.id = a.post_id
WHERE p.id IS NULL
   OR p.content_environment <> a.expected_old_environment;

START TRANSACTION;

INSERT INTO t_post_content_environment_backup (
    governance_batch_id,
    post_id,
    old_content_environment,
    approved_content_environment
)
SELECT
    @governance_batch_id,
    p.id,
    p.content_environment,
    a.approved_environment
FROM t_post_main p
JOIN approved_post_environment a
  ON a.post_id = p.id
 AND a.expected_old_environment = p.content_environment;

SET @backup_inserted_count = ROW_COUNT();

UPDATE t_post_main p
JOIN approved_post_environment a
  ON a.post_id = p.id
 AND a.expected_old_environment = p.content_environment
SET p.content_environment = a.approved_environment;

SET @updated_count = ROW_COUNT();

SELECT
    @expected_approved_count AS approved_count,
    @backup_inserted_count AS backup_inserted_count,
    @updated_count AS updated_count,
    (SELECT COUNT(*)
       FROM t_post_content_environment_backup
      WHERE governance_batch_id = @governance_batch_id) AS backup_count,
    (SELECT COUNT(*)
      FROM t_post_main p
      JOIN approved_post_environment a ON a.post_id = p.id
      WHERE p.content_environment = a.approved_environment) AS verified_count;

-- A failed assertion after START TRANSACTION leaves this session in the
-- transaction. Issue ROLLBACK immediately; never repair counts with a COMMIT.
INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'backup_and_update_counts_match',
       CASE WHEN @backup_inserted_count = @expected_approved_count
              AND @updated_count = @expected_approved_count
              AND (
                  SELECT COUNT(*)
                  FROM t_post_content_environment_backup
                  WHERE governance_batch_id = @governance_batch_id
              ) = @expected_approved_count
              AND (
                  SELECT COUNT(*)
                  FROM t_post_main p
                  JOIN approved_post_environment a ON a.post_id = p.id
                  WHERE p.content_environment = a.approved_environment
              ) = @expected_approved_count
           THEN 1 ELSE 0 END;

INSERT INTO content_environment_preflight_guard (assertion_name, passed)
SELECT 'historical_unclassified_is_zero',
       CASE WHEN NOT EXISTS (
           SELECT 1 FROM t_post_main WHERE content_environment = 'UNCLASSIFIED'
       ) THEN 1 ELSE 0 END;

ROLLBACK;
