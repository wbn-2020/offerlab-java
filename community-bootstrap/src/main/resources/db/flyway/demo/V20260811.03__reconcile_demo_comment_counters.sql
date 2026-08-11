-- Acceptance/demo data must not advertise comments that do not exist.
-- Recalculate only the known demo post range from real visible comment rows.
UPDATE t_post_counter pc
JOIN t_post p ON p.id = pc.post_id
SET pc.comment_count = (
        SELECT COUNT(*)
        FROM t_int_comment c
        WHERE c.post_id = pc.post_id
          AND c.comment_status = 1
          AND c.is_deleted = 0
    ),
    pc.update_time = CURRENT_TIMESTAMP(3)
WHERE p.id BETWEEN 991100000000000001 AND 991100000000000015
  AND p.is_deleted = 0;
