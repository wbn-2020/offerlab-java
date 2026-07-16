package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.UserRevisitItemPO;
import com.offerlab.community.interaction.infrastructure.persistence.projection.RevisitSourceRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserRevisitItemMapper extends BaseMapper<UserRevisitItemPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_int_user_revisit_item'
            """)
    int tableExists();

    @Select("""
            SELECT 'FAVORITE' AS sourceType,
                   CAST(p.id AS CHAR) AS sourceId,
                   'SAVED_CONTENT' AS reasonType,
                   CAST(GREATEST(UNIX_TIMESTAMP(f.update_time), UNIX_TIMESTAMP(p.update_time)) * 1000 AS UNSIGNED)
                       AS activityCursor,
                   p.title AS title,
                   '来自你保存的公开内容' AS description,
                   CONCAT('/post/', p.id) AS targetPath
            FROM t_int_favorite f
            JOIN t_post_main p ON p.id = f.post_id
            WHERE f.user_id = #{uid}
              AND f.is_deleted = 0
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
            ORDER BY f.update_time DESC, f.id DESC
            LIMIT #{limit}
            """)
    List<RevisitSourceRow> selectFavoriteCandidates(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT 'FOLLOWING_AUTHOR' AS sourceType,
                   CAST(p.author_id AS CHAR) AS sourceId,
                   'AUTHOR_PUBLIC_UPDATE' AS reasonType,
                   CAST(UNIX_TIMESTAMP(p.update_time) * 1000 AS UNSIGNED) AS activityCursor,
                   p.title AS title,
                   '来自你关注的作者的公开更新' AS description,
                   CONCAT('/post/', p.id) AS targetPath
            FROM t_user_follow f
            JOIN t_post_main p ON p.author_id = f.to_uid
            WHERE f.from_uid = #{uid}
              AND f.is_deleted = 0
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.create_time >= f.create_time
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_post_main newer
                  WHERE newer.author_id = p.author_id
                    AND newer.is_deleted = 0
                    AND newer.post_status = 1
                    AND newer.visibility = 1
                    AND newer.create_time >= f.create_time
                    AND (
                        newer.create_time > p.create_time
                        OR (newer.create_time = p.create_time AND newer.id > p.id)
                    )
              )
            ORDER BY p.update_time DESC, p.id DESC
            LIMIT #{limit}
            """)
    List<RevisitSourceRow> selectFollowingAuthorCandidates(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT 'DISCUSSION_FOLLOW' AS sourceType,
                   CAST(f.post_id AS CHAR) AS sourceId,
                   'NEW_DISCUSSION_REPLY' AS reasonType,
                   MAX(c.id) AS activityCursor,
                   p.title AS title,
                   '你关注的讨论有新的公开回复' AS description,
                   CONCAT('/post/', p.id) AS targetPath
            FROM t_int_discussion_follow f
            JOIN t_post_main p ON p.id = f.post_id
            JOIN t_int_comment c ON c.post_id = f.post_id
            WHERE f.uid = #{uid}
              AND f.follow_status = 1
              AND f.is_deleted = 0
              AND p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND c.is_deleted = 0
              AND c.comment_status = 1
              AND c.author_id <> f.uid
              AND c.create_time >= f.create_time
              AND c.id > COALESCE(f.last_read_comment_id, 0)
            GROUP BY f.post_id, p.title
            ORDER BY activityCursor DESC
            LIMIT #{limit}
            """)
    List<RevisitSourceRow> selectDiscussionCandidates(@Param("uid") Long uid, @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_int_user_revisit_item (
                id, uid, source_type, source_id, reason_type, activity_cursor,
                title, description, target_path, due_at, revisit_status, dedup_key,
                create_time, update_time
            ) VALUES (
                #{item.id}, #{item.uid}, #{item.sourceType}, #{item.sourceId}, #{item.reasonType},
                #{item.activityCursor}, #{item.title}, #{item.description}, #{item.targetPath},
                CURRENT_TIMESTAMP(3), 'OPEN', #{item.dedupKey},
                CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            ON DUPLICATE KEY UPDATE
                reason_type = VALUES(reason_type),
                title = VALUES(title),
                description = VALUES(description),
                target_path = VALUES(target_path),
                due_at = CASE
                    WHEN VALUES(activity_cursor) > COALESCE(activity_cursor, 0)
                    THEN CURRENT_TIMESTAMP(3) ELSE due_at END,
                revisit_status = CASE
                    WHEN VALUES(activity_cursor) > COALESCE(activity_cursor, 0)
                    THEN 'OPEN' ELSE revisit_status END,
                completed_at = CASE
                    WHEN VALUES(activity_cursor) > COALESCE(activity_cursor, 0)
                    THEN NULL ELSE completed_at END,
                snoozed_until = CASE
                    WHEN VALUES(activity_cursor) > COALESCE(activity_cursor, 0)
                    THEN NULL ELSE snoozed_until END,
                activity_cursor = GREATEST(COALESCE(activity_cursor, 0), VALUES(activity_cursor)),
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsertCandidate(@Param("item") UserRevisitItemPO item);

    @Update("""
            UPDATE t_int_user_revisit_item
            SET revisit_status = 'OPEN',
                snoozed_until = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE uid = #{uid}
              AND revisit_status = 'SNOOZED'
              AND snoozed_until <= CURRENT_TIMESTAMP(3)
            """)
    int reopenDueSnoozes(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT id, uid, source_type, source_id, reason_type, activity_cursor,
                   title, description, target_path, due_at, revisit_status, dedup_key,
                   completed_at, snoozed_until, last_notified_at, create_time, update_time
            FROM t_int_user_revisit_item
            WHERE uid = #{uid}
              <if test="status != null and status != ''">
              AND revisit_status = #{status}
              </if>
              <if test="cursor != null and cursor &gt; 0">
              AND id &lt; #{cursor}
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<UserRevisitItemPO> listByUser(@Param("uid") Long uid,
                                       @Param("status") String status,
                                       @Param("cursor") Long cursor,
                                       @Param("limit") int limit);

    @Select("""
            SELECT id, uid, source_type, source_id, reason_type, activity_cursor,
                   title, description, target_path, due_at, revisit_status, dedup_key,
                   completed_at, snoozed_until, last_notified_at, create_time, update_time
            FROM t_int_user_revisit_item
            WHERE id = #{id}
              AND uid = #{uid}
            LIMIT 1
            """)
    UserRevisitItemPO selectOwned(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_int_user_revisit_item
            SET revisit_status = 'COMPLETED',
                completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP(3)),
                snoozed_until = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND uid = #{uid}
              AND revisit_status IN ('OPEN', 'SNOOZED', 'COMPLETED')
            """)
    int complete(@Param("id") Long id, @Param("uid") Long uid);

    @Update("""
            UPDATE t_int_user_revisit_item
            SET revisit_status = 'SNOOZED',
                snoozed_until = #{until},
                completed_at = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND uid = #{uid}
              AND revisit_status IN ('OPEN', 'SNOOZED')
            """)
    int snooze(@Param("id") Long id, @Param("uid") Long uid, @Param("until") LocalDateTime until);

    @Update("""
            UPDATE t_int_user_revisit_item
            SET revisit_status = 'IGNORED',
                snoozed_until = NULL,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND uid = #{uid}
              AND revisit_status IN ('OPEN', 'SNOOZED', 'IGNORED')
            """)
    int ignore(@Param("id") Long id, @Param("uid") Long uid);
}
