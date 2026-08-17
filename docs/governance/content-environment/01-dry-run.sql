-- READ ONLY. Run after V20260814.01 and export all result sets.

SELECT content_environment, COUNT(*) AS post_count
FROM t_post_main
GROUP BY content_environment
ORDER BY content_environment;

SELECT
    p.id,
    p.author_id,
    p.post_type,
    p.title,
    p.content_environment,
    p.post_status,
    p.visibility,
    p.create_time,
    p.update_time,
    JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.source')) AS declared_source,
    JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.batchId')) AS declared_batch_id,
    JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.environment')) AS declared_environment,
    JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.createdBy')) AS declared_creator,
    CASE WHEN p.id = 741177956554252288 THEN 1 ELSE 0 END AS known_acceptance_candidate,
    CASE WHEN UPPER(CONCAT_WS(' ', p.title, p.content, e.ext_json))
                   REGEXP 'E2E|SMOKE|CODEX|TESTDATA|FIXTURE|DEMO|DEPLOY|ACCEPTANCE'
         THEN 1 ELSE 0 END AS text_review_signal,
    (SELECT COUNT(*) FROM t_post_tag_ref ptr WHERE ptr.post_id = p.id) AS tag_ref_count,
    (SELECT COUNT(*) FROM t_int_comment c WHERE c.post_id = p.id AND c.is_deleted = 0) AS comment_count
FROM t_post_main p
LEFT JOIN t_post_extension e ON e.post_id = p.id
WHERE p.is_deleted = 0
  AND p.content_environment = 'UNCLASSIFIED'
ORDER BY p.create_time ASC, p.id ASC;

SELECT
    p.author_id,
    MIN(p.create_time) AS first_created_at,
    MAX(p.create_time) AS last_created_at,
    COUNT(*) AS candidate_count,
    SUM(CASE WHEN UPPER(CONCAT_WS(' ', p.title, p.content, e.ext_json))
                       REGEXP 'E2E|SMOKE|CODEX|TESTDATA|FIXTURE|DEMO|DEPLOY|ACCEPTANCE'
             THEN 1 ELSE 0 END) AS text_signal_count
FROM t_post_main p
LEFT JOIN t_post_extension e ON e.post_id = p.id
WHERE p.is_deleted = 0
  AND p.content_environment = 'UNCLASSIFIED'
GROUP BY p.author_id
ORDER BY first_created_at ASC, p.author_id ASC;

-- This row remains pending until human evidence review. No classification is
-- implied by its presence in this result.
SELECT *
FROM t_post_main
WHERE id = 741177956554252288;
