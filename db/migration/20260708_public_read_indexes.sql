-- Add covering indexes for public/read-heavy paginated endpoints.
-- Safe to run repeatedly: each index is created only when missing.

DROP PROCEDURE IF EXISTS v20260708_add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE v20260708_add_index_if_missing(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
        LIMIT 1
    ) THEN
        SET @ddl = p_sql;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

CALL v20260708_add_index_if_missing('t_user_follow', 'idx_following_page',
    'ALTER TABLE t_user_follow ADD KEY idx_following_page (from_uid, is_deleted, id)');
CALL v20260708_add_index_if_missing('t_user_follow', 'idx_follower_page',
    'ALTER TABLE t_user_follow ADD KEY idx_follower_page (to_uid, is_deleted, id)');

CALL v20260708_add_index_if_missing('t_notif_message', 'idx_receiver_list',
    'ALTER TABLE t_notif_message ADD KEY idx_receiver_list (receiver_uid, is_deleted, notif_type, create_time, id)');
CALL v20260708_add_index_if_missing('t_notif_message', 'idx_receiver_unread_latest',
    'ALTER TABLE t_notif_message ADD KEY idx_receiver_unread_latest (receiver_uid, is_deleted, is_read, create_time, id)');

CALL v20260708_add_index_if_missing('t_int_contact_request', 'idx_contact_request_receiver_page',
    'ALTER TABLE t_int_contact_request ADD KEY idx_contact_request_receiver_page (receiver_uid, is_deleted, request_status, update_time, id)');
CALL v20260708_add_index_if_missing('t_int_contact_request', 'idx_contact_request_requester_page',
    'ALTER TABLE t_int_contact_request ADD KEY idx_contact_request_requester_page (requester_uid, is_deleted, request_status, update_time, id)');

CALL v20260708_add_index_if_missing('t_int_favorite', 'idx_favorite_user_page',
    'ALTER TABLE t_int_favorite ADD KEY idx_favorite_user_page (user_id, is_deleted, create_time, id)');
CALL v20260708_add_index_if_missing('t_int_favorite', 'idx_favorite_user_folder_page',
    'ALTER TABLE t_int_favorite ADD KEY idx_favorite_user_folder_page (user_id, folder_id, is_deleted, create_time, id)');

CALL v20260708_add_index_if_missing('t_int_like', 'idx_like_user_page',
    'ALTER TABLE t_int_like ADD KEY idx_like_user_page (user_id, target_type, is_deleted, create_time, id)');

CALL v20260708_add_index_if_missing('t_int_comment', 'idx_comment_quality_roots',
    'ALTER TABLE t_int_comment ADD KEY idx_comment_quality_roots (post_id, root_id, comment_status, is_deleted, create_time, id)');
CALL v20260708_add_index_if_missing('t_int_comment_quality_signal', 'idx_comment_quality_post_comment',
    'ALTER TABLE t_int_comment_quality_signal ADD KEY idx_comment_quality_post_comment (post_id, signal_type, signal_status, is_deleted, comment_id, root_id)');

CALL v20260708_add_index_if_missing('t_int_discussion_follow', 'idx_discussion_follow_notify_page',
    'ALTER TABLE t_int_discussion_follow ADD KEY idx_discussion_follow_notify_page (post_id, follow_status, is_deleted, id)');
CALL v20260708_add_index_if_missing('t_int_discussion_follow', 'idx_discussion_follow_uid_page',
    'ALTER TABLE t_int_discussion_follow ADD KEY idx_discussion_follow_uid_page (uid, follow_status, is_deleted, update_time, id)');

DROP PROCEDURE IF EXISTS v20260708_add_index_if_missing;
