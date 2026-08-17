-- AUTHORIZED CONTROLLED ENVIRONMENT ONLY.
-- Confirm backup count and batch identity before replacing the final ROLLBACK
-- with COMMIT in a separately reviewed execution copy.

SET @governance_batch_id = '__REQUIRED_BATCH_ID__';

SELECT
    governance_batch_id,
    approved_content_environment,
    COUNT(*) AS backup_count
FROM t_post_content_environment_backup
WHERE governance_batch_id = @governance_batch_id
GROUP BY governance_batch_id, approved_content_environment;

START TRANSACTION;

UPDATE t_post_main p
JOIN t_post_content_environment_backup b
  ON b.post_id = p.id
 AND b.governance_batch_id = @governance_batch_id
SET p.content_environment = CASE
        WHEN b.approved_content_environment IN ('TEST', 'INTERNAL')
            THEN b.approved_content_environment
        ELSE b.old_content_environment
    END
WHERE p.content_environment = b.approved_content_environment;

SELECT ROW_COUNT() AS rollback_updated_count;

SELECT p.id, p.content_environment, b.old_content_environment,
       b.approved_content_environment
FROM t_post_main p
JOIN t_post_content_environment_backup b
  ON b.post_id = p.id
 AND b.governance_batch_id = @governance_batch_id
WHERE p.content_environment <> CASE
        WHEN b.approved_content_environment IN ('TEST', 'INTERNAL')
            THEN b.approved_content_environment
        ELSE b.old_content_environment
    END;

ROLLBACK;
