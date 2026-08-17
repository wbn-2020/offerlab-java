package com.offerlab.community.feed.infrastructure.persistence.mapper;

import com.offerlab.community.feed.infrastructure.persistence.po.FeedFeedbackPreferencePO;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedQualitySignalAggregateRow;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedRevisionAwareQualitySignalAggregateRow;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedRevisionAwareQualitySignalWindowQuery;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface FeedFeedbackPreferenceMapper {

    @Insert("""
            INSERT INTO t_feed_feedback_preference
                (id, uid, post_id, action, target_type, target_id, reason,
                 expires_at, create_time, update_time)
            VALUES
                (#{id}, #{uid}, #{postId}, #{action}, #{targetType}, #{targetId}, #{reason},
                 #{expiresAt}, #{createTime}, #{updateTime})
            ON DUPLICATE KEY UPDATE
                action = #{action},
                target_type = #{targetType},
                target_id = #{targetId},
                reason = #{reason},
                expires_at = #{expiresAt},
                update_time = #{updateTime}
            """)
    int upsert(FeedFeedbackPreferencePO preference);

    @Select("""
            <script>
            SELECT post_id AS postId,
                   COUNT(DISTINCT uid) AS distinctReaderCount,
                   MAX(update_time) AS latestUpdatedAt
              FROM t_feed_feedback_preference
             WHERE reason = 'v30:quality_not_expected'
               AND action IN ('HIDE', 'LESS_LIKE_THIS')
               AND post_id &gt; 0
               AND expires_at &gt; #{now}
               AND update_time &gt;= #{since}
               AND post_id IN
               <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                   #{postId}
               </foreach>
             GROUP BY post_id
            </script>
            """)
    List<FeedQualitySignalAggregateRow> aggregateActiveQualitySignals(
            @Param("postIds") List<Long> postIds,
            @Param("since") LocalDateTime since,
            @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT post_id AS postId,
                   COUNT(DISTINCT CASE
                   <foreach collection="windows" item="window" separator=" ">
                       WHEN post_id = #{window.postId}
                        AND uid &lt;&gt; #{window.authorId}
                        <choose>
                            <when test="window.hasEffectiveRevision and window.windowStart != null">
                             AND update_time &lt;= #{window.windowStart}
                            </when>
                            <otherwise>
                             AND 1 = 0
                            </otherwise>
                        </choose>
                       THEN uid
                   </foreach>
                   END) AS priorRevisionDistinctReaderCount,
                   COUNT(DISTINCT CASE
                   <foreach collection="windows" item="window" separator=" ">
                       WHEN post_id = #{window.postId}
                        AND uid &lt;&gt; #{window.authorId}
                        <choose>
                            <when test="window.hasEffectiveRevision and window.windowStart != null">
                             AND update_time &gt; #{window.windowStart}
                            </when>
                            <otherwise>
                             AND 1 = 1
                            </otherwise>
                        </choose>
                       THEN uid
                   </foreach>
                   END) AS currentRevisionDistinctReaderCount
              FROM t_feed_feedback_preference
             WHERE reason = 'v30:quality_not_expected'
               AND action IN ('HIDE', 'LESS_LIKE_THIS')
               AND post_id &gt; 0
               AND expires_at &gt; #{now}
               AND update_time &gt;= #{baseWindowStart}
               AND (
                   <foreach collection="windows" item="window" separator=" OR ">
                       (post_id = #{window.postId} AND uid &lt;&gt; #{window.authorId})
                   </foreach>
               )
             GROUP BY post_id
            </script>
            """)
    List<FeedRevisionAwareQualitySignalAggregateRow> aggregateRevisionAwareActiveQualitySignals(
            @Param("windows") List<FeedRevisionAwareQualitySignalWindowQuery> windows,
            @Param("baseWindowStart") LocalDateTime baseWindowStart,
            @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT extension.domain AS domain,
                   COUNT(DISTINCT signal.post_id) AS qualifiedPostCount
              FROM (
                    SELECT post_id
                      FROM t_feed_feedback_preference
                     WHERE reason = 'v30:quality_not_expected'
                       AND action IN ('HIDE', 'LESS_LIKE_THIS')
                       AND post_id &gt; 0
                       AND expires_at &gt; #{now}
                       AND update_time &gt;= #{since}
                     GROUP BY post_id
                    HAVING COUNT(DISTINCT uid) &gt;= #{minimumDistinctReaders}
                   ) signal
              JOIN t_post_main post ON post.id = signal.post_id
             JOIN t_post_extension extension ON extension.post_id = post.id
             WHERE post.is_deleted = 0
               AND post.post_status = 1
               AND post.visibility = 1
               AND post.content_environment = 'COMMUNITY'
               AND (extension.domain &lt;&gt; 2
                    OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(extension.ext_json, '$.anonymous')), 'false')
                        NOT IN ('true', '1'))
               AND extension.domain IN
               <foreach collection="domainCodes" item="domainCode" open="(" separator="," close=")">
                   #{domainCode}
               </foreach>
             GROUP BY extension.domain
            </script>
            """)
    List<FeedQualitySignalAggregateRow> countQualifiedQualitySignalPostsByDomain(
            @Param("domainCodes") List<Integer> domainCodes,
            @Param("minimumDistinctReaders") int minimumDistinctReaders,
            @Param("since") LocalDateTime since,
            @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND post_id = #{postId}
            """)
    int deleteByPost(@Param("uid") Long uid, @Param("postId") Long postId);

    @Select("""
            <script>
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
             FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND target_type &lt;&gt; 'AUTHOR'
               AND (expires_at IS NULL OR expires_at &gt; #{now})
             <if test="cursorTime != null">
               AND (update_time &lt; #{cursorTime}
                    OR (update_time = #{cursorTime} AND id &lt; #{cursorId}))
             </if>
             ORDER BY update_time DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    List<FeedFeedbackPreferencePO> listActive(@Param("uid") Long uid,
                                              @Param("now") LocalDateTime now,
                                              @Param("cursorTime") LocalDateTime cursorTime,
                                              @Param("cursorId") Long cursorId,
                                              @Param("limit") int limit);

    @Select("""
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
             FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND post_id = #{postId}
               AND target_type &lt;&gt; 'AUTHOR'
               AND (expires_at IS NULL OR expires_at > #{now})
             LIMIT 1
            """)
    FeedFeedbackPreferencePO findActive(@Param("uid") Long uid,
                                        @Param("postId") Long postId,
                                        @Param("now") LocalDateTime now);

    @Select("""
            SELECT post_id
             FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'HIDE'
               AND target_type = 'POST'
               AND (expires_at IS NULL OR expires_at > #{now})
            """)
    Set<Long> listActiveHiddenPostIds(@Param("uid") Long uid,
                                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT DISTINCT target_id
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'LESS_LIKE_THIS'
               AND target_type = 'DOMAIN'
               AND (expires_at IS NULL OR expires_at > #{now})
            """)
    Set<Long> listActiveReducedDomainIds(@Param("uid") Long uid,
                                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND post_id <> #{postId}
               AND action = 'LESS_LIKE_THIS'
               AND target_type = 'DOMAIN'
               AND target_id = #{domain}
               AND (expires_at IS NULL OR expires_at > #{now})
            """)
    int countOtherActiveDomainControls(@Param("uid") Long uid,
                                       @Param("postId") Long postId,
                                       @Param("domain") Integer domain,
                                       @Param("now") LocalDateTime now);

    @Select("""
            SELECT target_id
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'BLOCK_AUTHOR'
               AND target_type = 'AUTHOR'
               AND target_id > 0
               AND (expires_at IS NULL OR expires_at > #{now})
            """)
    Set<Long> listActiveBlockedAuthorIds(@Param("uid") Long uid,
                                         @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND (expires_at IS NULL OR expires_at &gt; #{now})
             <if test="cursorTime != null">
               AND (update_time &lt; #{cursorTime}
                    OR (update_time = #{cursorTime} AND id &lt; #{cursorId}))
             </if>
             ORDER BY update_time DESC, id DESC
             LIMIT #{limit}
            </script>
            """)
    List<FeedFeedbackPreferencePO> listActiveControls(@Param("uid") Long uid,
                                                       @Param("now") LocalDateTime now,
                                                       @Param("cursorTime") LocalDateTime cursorTime,
                                                       @Param("cursorId") Long cursorId,
                                                       @Param("limit") int limit);

    @Select("""
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'BLOCK_AUTHOR'
               AND target_type = 'AUTHOR'
               AND target_id = #{authorUid}
               AND (expires_at IS NULL OR expires_at > #{now})
             LIMIT 1
            """)
    FeedFeedbackPreferencePO findActiveAuthorControl(@Param("uid") Long uid,
                                                      @Param("authorUid") Long authorUid,
                                                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT id,
                   uid,
                   post_id AS postId,
                   action,
                   target_type AS targetType,
                   target_id AS targetId,
                   reason,
                   expires_at AS expiresAt,
                   create_time AS createTime,
                   update_time AS updateTime
              FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND id = #{id}
             LIMIT 1
            """)
    FeedFeedbackPreferencePO findOwnedControlById(@Param("uid") Long uid,
                                                   @Param("id") Long id);

    @Delete("""
            DELETE FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND id = #{control.id}
               AND post_id = #{control.postId}
               AND action = #{control.action}
               AND target_type = #{control.targetType}
               AND target_id = #{control.targetId}
               AND reason <=> #{control.reason}
               AND expires_at <=> #{control.expiresAt}
               AND update_time = #{control.updateTime}
            """)
    int deleteOwnedControlIfUnchanged(@Param("uid") Long uid,
                                      @Param("control") FeedFeedbackPreferencePO control);

    @Delete("""
            DELETE FROM t_feed_feedback_preference
             WHERE uid = #{uid}
               AND action = 'BLOCK_AUTHOR'
               AND target_type = 'AUTHOR'
               AND target_id = #{authorUid}
            """)
    int deleteAuthorControl(@Param("uid") Long uid, @Param("authorUid") Long authorUid);

    @Select("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE()
               AND table_name = 't_feed_feedback_preference'
            """)
    int tableExists();
}
