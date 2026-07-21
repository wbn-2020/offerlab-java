package com.offerlab.community.notification.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.NotificationMessagePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMessageMapper extends BaseMapper<NotificationMessagePO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_notif_message'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 't_notif_message'
              AND column_name = 'dedup_key'
            """)
    int dedupKeyColumnExists();

    @Select("""
            SELECT id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                   content_json, is_read, create_time, is_deleted
            FROM t_notif_message
            WHERE receiver_uid = #{uid}
              AND is_read = 0
              AND is_deleted = 0
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    NotificationMessagePO selectLatestUnread(@Param("uid") Long uid);

    @Select("""
            SELECT notif_type AS notifType, COUNT(*) AS unreadCount
            FROM t_notif_message
            WHERE receiver_uid = #{uid}
              AND is_read = 0
              AND is_deleted = 0
            GROUP BY notif_type
            """)
    List<java.util.Map<String, Object>> countUnreadGroupedByType(@Param("uid") Long uid);

    @Select("""
            <script>
            SELECT id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                   content_json, is_read, create_time, is_deleted
            FROM t_notif_message
            <where>
              receiver_uid = #{uid}
              AND is_deleted = 0
              <if test="notifType != null">
                AND notif_type = #{notifType}
              </if>
              <if test="cursorTime != null">
                AND (
                  create_time &lt; #{cursorTime}
                  OR (#{cursorId} IS NOT NULL AND create_time = #{cursorTime} AND id &lt; #{cursorId})
                )
              </if>
            </where>
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<NotificationMessagePO> listByUser(@Param("uid") Long uid,
                                           @Param("notifType") Integer notifType,
                                           @Param("cursorTime") LocalDateTime cursorTime,
                                           @Param("cursorId") Long cursorId,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                   content_json, dedup_key, is_read, create_time, is_deleted
            FROM t_notif_message
            WHERE receiver_uid = #{uid}
              AND is_deleted = 0
              <if test="sourceType != null">
                <choose>
                  <when test="sourceType == 'TOPIC'">
                    AND (
                      JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.topicSlug'
                      )) IS NOT NULL
                      OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.topicId'
                      )) IS NOT NULL
                    )
                  </when>
                  <when test="sourceType == 'NEED'">
                    AND JSON_UNQUOTE(JSON_EXTRACT(
                      IF(JSON_VALID(content_json), content_json, '{}'), '$.needId'
                    )) IS NOT NULL
                  </when>
                  <when test="sourceType == 'SERIES'">
                    AND JSON_UNQUOTE(JSON_EXTRACT(
                      IF(JSON_VALID(content_json), content_json, '{}'), '$.seriesId'
                    )) IS NOT NULL
                  </when>
                  <when test="sourceType == 'COLLECTION'">
                    AND JSON_UNQUOTE(JSON_EXTRACT(
                      IF(JSON_VALID(content_json), content_json, '{}'), '$.collectionId'
                    )) IS NOT NULL
                  </when>
                  <when test="sourceType == 'POST'">
                    AND (
                      JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.postId'
                      )) IS NOT NULL
                      OR (target_type = 1 AND target_id IS NOT NULL)
                    )
                  </when>
                </choose>
              </if>
              <if test="sourceId != null">
                <choose>
                  <when test="sourceType == 'TOPIC'">
                    AND (
                      LOWER(JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.topicSlug'
                      ))) = LOWER(#{sourceId})
                      OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.topicId'
                      )) = #{sourceId}
                    )
                  </when>
                  <when test="sourceType == 'NEED'">
                    AND JSON_UNQUOTE(JSON_EXTRACT(
                      IF(JSON_VALID(content_json), content_json, '{}'), '$.needId'
                    )) = #{sourceId}
                  </when>
                  <when test="sourceType == 'SERIES'">
                    AND JSON_UNQUOTE(JSON_EXTRACT(
                      IF(JSON_VALID(content_json), content_json, '{}'), '$.seriesId'
                    )) = #{sourceId}
                  </when>
                  <when test="sourceType == 'COLLECTION'">
                    AND JSON_UNQUOTE(JSON_EXTRACT(
                      IF(JSON_VALID(content_json), content_json, '{}'), '$.collectionId'
                    )) = #{sourceId}
                  </when>
                  <when test="sourceType == 'POST'">
                    AND (
                      JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.postId'
                      )) = #{sourceId}
                      OR (target_type = 1 AND CAST(target_id AS CHAR) = #{sourceId})
                    )
                  </when>
                  <otherwise>
                    AND (
                      JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.postId'
                      )) = #{sourceId}
                      OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.topicId'
                      )) = #{sourceId}
                      OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.topicSlug'
                      ))) = LOWER(#{sourceId})
                      OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.needId'
                      )) = #{sourceId}
                      OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.seriesId'
                      )) = #{sourceId}
                      OR JSON_UNQUOTE(JSON_EXTRACT(
                        IF(JSON_VALID(content_json), content_json, '{}'), '$.collectionId'
                      )) = #{sourceId}
                      OR (target_type = 1 AND CAST(target_id AS CHAR) = #{sourceId})
                    )
                  </otherwise>
                </choose>
              </if>
              <if test="unreadOnly">
              AND is_read = 0
              </if>
              <if test="cursorTime != null">
              AND (
                    create_time &lt; #{cursorTime}
                    OR (
                        #{cursorId} IS NOT NULL
                        AND create_time = #{cursorTime}
                        AND id &lt; #{cursorId}
                    )
                  )
              </if>
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<NotificationMessagePO> listUpdateDigestCandidates(
            @Param("uid") Long uid,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    @Insert("""
            INSERT IGNORE INTO t_notif_message (
                id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                content_json, dedup_key, is_read, is_deleted
            ) VALUES (
                #{id}, #{receiverUid}, #{senderUid}, #{notifType}, #{targetType}, #{targetId},
                #{contentJson}, #{dedupKey}, #{isRead}, #{isDeleted}
            )
            """)
    int insertIgnore(NotificationMessagePO message);

    @Insert("""
            INSERT INTO t_notif_message (
                id, receiver_uid, sender_uid, notif_type, target_type, target_id,
                content_json, is_read, is_deleted
            ) VALUES (
                #{id}, #{receiverUid}, #{senderUid}, #{notifType}, #{targetType}, #{targetId},
                #{contentJson}, #{isRead}, #{isDeleted}
            )
            """)
    int insertLegacy(NotificationMessagePO message);

    @Update("""
            UPDATE t_notif_message
            SET is_read = 1
            WHERE receiver_uid = #{uid}
              AND is_deleted = 0
              AND is_read = 0
            ORDER BY create_time ASC, id ASC
            LIMIT #{limit}
            """)
    int markUnreadBatchAsRead(@Param("uid") Long uid, @Param("limit") int limit);
}
