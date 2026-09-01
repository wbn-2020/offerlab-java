-- OfferLab R9 acceptance dataset verification. Read only.
SET NAMES utf8mb4;
SET @batch_code = 'R9-20260817-ACCEPTANCE';

SELECT entity_type, COUNT(*) AS audited_rows
FROM t_acceptance_data_batch_item
WHERE batch_code = @batch_code
GROUP BY entity_type
ORDER BY entity_type;

SELECT
    COUNT(DISTINCT p.author_id) AS public_authors,
    COUNT(*) AS public_posts,
    COUNT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.domain'))) AS domains,
    COUNT(DISTINCT p.post_type) AS post_types,
    SUM(CHAR_LENGTH(p.content) >= 1200) AS long_posts,
    SUM(CHAR_LENGTH(p.content) <= 260) AS short_posts,
    SUM(CHAR_LENGTH(p.title) >= 180) AS near_title_limit_posts
FROM t_post_main p
JOIN t_post_extension e ON e.post_id = p.id
WHERE p.is_deleted = 0
  AND p.post_status = 1
  AND p.visibility = 1
  AND p.content_environment = 'COMMUNITY';

SELECT
    t.id,
    t.tag_name,
    t.use_count,
    COUNT(DISTINCT CASE
        WHEN p.is_deleted = 0
         AND p.post_status = 1
         AND p.visibility = 1
         AND p.content_environment = 'COMMUNITY'
        THEN p.id END) AS public_post_count
FROM t_tag t
LEFT JOIN t_post_tag_ref ptr ON ptr.tag_id = t.id
LEFT JOIN t_post_main p ON p.id = ptr.post_id
WHERE t.id IN (
    1001, 1004, 1005, 1006, 1007, 1010,
    991000000000000002, 991000000000000003, 991000000000000004,
    991000000000000005, 991000000000000006, 991000000000000007,
    991000000000000008, 991000000000000009, 991000000000000010
)
GROUP BY t.id, t.tag_name, t.use_count
ORDER BY public_post_count DESC, t.id;

SELECT t.id, t.slug, t.topic_name, COUNT(DISTINCT p.id) AS public_post_count
FROM t_community_topic t
JOIN t_community_topic_tag tt ON tt.topic_id = t.id
JOIN t_post_tag_ref ptr ON ptr.tag_id = tt.tag_id
JOIN t_post_main p ON p.id = ptr.post_id
WHERE t.is_deleted = 0
  AND t.topic_status = 1
  AND p.is_deleted = 0
  AND p.post_status = 1
  AND p.visibility = 1
  AND p.content_environment = 'COMMUNITY'
GROUP BY t.id, t.slug, t.topic_name
HAVING COUNT(DISTINCT p.id) >= 3
ORDER BY public_post_count DESC, t.id;

SELECT
    c.post_id,
    SUM(c.root_id = 0) AS root_comments,
    SUM(c.root_id <> 0) AS reply_comments,
    COUNT(*) AS total_comments,
    pc.comment_count AS counter_comment_count
FROM t_int_comment c
JOIN t_post_counter pc ON pc.post_id = c.post_id
WHERE c.is_deleted = 0
  AND c.comment_status = 1
  AND c.post_id BETWEEN 995100000000000001 AND 995100000000000023
GROUP BY c.post_id, pc.comment_count
ORDER BY c.post_id;

SELECT
    q.difficulty,
    COUNT(*) AS public_question_count,
    COUNT(DISTINCT q.source_post_id) AS source_post_count
FROM t_interview_question q
JOIN t_post_main p ON p.id = q.source_post_id
WHERE q.status = 1
  AND p.is_deleted = 0
  AND p.post_status = 1
  AND p.visibility = 1
  AND p.content_environment = 'COMMUNITY'
GROUP BY q.difficulty
ORDER BY q.difficulty;

SELECT
    id,
    slot_code,
    slot_status,
    current_version,
    JSON_LENGTH(published_snapshot_json, '$.items') AS published_items
FROM t_operation_slot
WHERE is_deleted = 0
  AND slot_code IN ('HOME_FEATURED', 'DISCOVERY_FEATURED_TOPICS')
ORDER BY slot_code;
