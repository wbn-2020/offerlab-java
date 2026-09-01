-- OfferLab R9 acceptance dataset dry run. Read only.
SET NAMES utf8mb4;
SET @batch_code = 'R9-20260817-ACCEPTANCE';

SELECT
    @batch_code AS batch_code,
    DATABASE() AS database_name,
    NOW(3) AS inspected_at;

SELECT
    (SELECT COUNT(*) FROM t_user_account WHERE is_deleted = 0) AS current_users,
    (SELECT COUNT(*) FROM t_post_main
      WHERE is_deleted = 0 AND post_status = 1 AND visibility = 1
        AND content_environment = 'COMMUNITY') AS current_public_posts,
    (SELECT COUNT(*) FROM t_int_comment
      WHERE is_deleted = 0 AND comment_status = 1) AS current_comments,
    (SELECT COUNT(*) FROM t_interview_question q
      JOIN t_post_main p ON p.id = q.source_post_id
      WHERE q.status = 1
        AND p.is_deleted = 0 AND p.post_status = 1 AND p.visibility = 1
        AND p.content_environment = 'COMMUNITY') AS current_public_questions,
    (SELECT COUNT(*) FROM t_operation_slot WHERE is_deleted = 0) AS current_operation_slots;

SELECT
    5 AS planned_authors,
    23 AS planned_public_posts,
    29 AS planned_comments,
    8 AS planned_public_questions,
    2 AS planned_operation_slots,
    4 AS planned_operation_slot_items;

SELECT COUNT(*) AS fixed_id_rows_before_apply
FROM (
    SELECT id FROM t_user_account WHERE id BETWEEN 995000000000000001 AND 995000000000000005
    UNION ALL
    SELECT id FROM t_post_main WHERE id BETWEEN 995100000000000001 AND 995100000000000023
    UNION ALL
    SELECT id FROM t_int_comment WHERE id BETWEEN 995120000000000001 AND 995120000000000029
    UNION ALL
    SELECT id FROM t_interview_question WHERE id BETWEEN 995200000000000001 AND 995200000000000008
    UNION ALL
    SELECT id FROM t_operation_slot WHERE id BETWEEN 995300000000000001 AND 995300000000000002
) conflicts;

SELECT COUNT(*) AS email_conflicts
FROM t_user_account
WHERE is_deleted = 0
  AND email IN (
      'lin.cheng@community.invalid',
      'zhou.yuzhou@community.invalid',
      'su.yao@community.invalid',
      'chen.he@community.invalid',
      'gu.yuanchuan@community.invalid'
  )
  AND id NOT BETWEEN 995000000000000001 AND 995000000000000005;

SELECT COUNT(*) AS slot_code_conflicts
FROM t_operation_slot
WHERE is_deleted = 0
  AND slot_code IN ('HOME_FEATURED', 'DISCOVERY_FEATURED_TOPICS')
  AND id NOT BETWEEN 995300000000000001 AND 995300000000000002;

SELECT id, tag_name, use_count, recommended, tag_status
FROM t_tag
WHERE is_deleted = 0
  AND id IN (
      1001, 1004, 1005, 1006, 1007, 1010,
      991000000000000002, 991000000000000003, 991000000000000004,
      991000000000000005, 991000000000000006, 991000000000000007,
      991000000000000008, 991000000000000009, 991000000000000010
  )
ORDER BY id;

SELECT t.id, t.slug, t.topic_name, COUNT(DISTINCT p.id) AS current_public_post_count
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
ORDER BY t.id;
