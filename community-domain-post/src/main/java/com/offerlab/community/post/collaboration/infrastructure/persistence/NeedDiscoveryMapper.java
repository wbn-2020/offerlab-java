package com.offerlab.community.post.collaboration.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NeedDiscoveryMapper {

    @Select("""
            <script>
            SELECT n.id,
                   n.creator_uid AS creatorUid,
                   n.domain,
                   n.source_type AS sourceType,
                   n.source_ref_id AS sourceRefId,
                   n.content_format AS contentFormat,
                   n.title,
                   n.description,
                   n.acceptance_criteria AS acceptanceCriteria,
                   n.need_status AS status,
                   n.claimed_by_uid AS claimedByUid,
                   n.claimed_at AS claimedAt,
                   n.last_progress_at AS lastProgressAt,
                   n.merged_into_need_id AS mergedIntoNeedId,
                   n.resolution_type AS resolutionType,
                   n.resolution_id AS resolutionId,
                   n.resolution_post_id AS resolutionPostId,
                   n.follower_count AS followerCount,
                   CASE WHEN #{uid} IS NULL THEN 0 ELSE EXISTS(
                       SELECT 1
                         FROM t_collab_content_need_follow f
                        WHERE f.need_id = n.id
                          AND f.uid = #{uid}
                          AND f.active = 1
                   ) END AS followed,
                   CASE WHEN n.need_status = 'CLAIMED'
                              AND COALESCE(n.last_progress_at, n.claimed_at, n.update_time, n.create_time)
                                  &lt; DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY)
                        THEN 1 ELSE 0 END AS stalled,
                   <choose>
                     <when test="uid == null">
                       0 AS viewerDomainMatch,
                       0 AS viewerFormatMatch,
                     </when>
                     <otherwise>
                       CASE WHEN EXISTS (
                           SELECT 1
                             FROM t_post_main vp
                             LEFT JOIN t_post_extension ve ON ve.post_id = vp.id
                            WHERE vp.author_id = #{uid}
                              AND vp.is_deleted = 0
                              AND vp.post_status = 1
                              AND vp.visibility = 1
                              AND vp.content_environment = 'COMMUNITY'
                              AND ve.domain = n.domain
                       ) THEN 1 ELSE 0 END AS viewerDomainMatch,
                       CASE WHEN EXISTS (
                           SELECT 1
                             FROM t_post_main vp
                             LEFT JOIN t_post_extension ve ON ve.post_id = vp.id
                            WHERE vp.author_id = #{uid}
                              AND vp.is_deleted = 0
                              AND vp.post_status = 1
                              AND vp.visibility = 1
                              AND vp.content_environment = 'COMMUNITY'
                              AND (
                                  (n.content_format = 'QUESTION' AND vp.post_type IN (4, 13))
                                  OR (n.content_format = 'RESOURCE' AND vp.post_type = 14)
                                  OR (n.content_format IN ('ARTICLE', 'GUIDE', 'CHECKLIST')
                                      AND vp.post_type IN (1, 2, 3, 10, 11, 12, 15, 16, 17))
                              )
                       ) THEN 1 ELSE 0 END AS viewerFormatMatch,
                     </otherwise>
                   </choose>
                   n.create_time AS createTime,
                   n.update_time AS updateTime
            FROM t_collab_content_need n
            WHERE n.moderation_hidden = 0
              <if test="domain != null">
                AND n.domain = #{domain}
              </if>
              <if test="status != null and status != ''">
                AND n.need_status = #{status}
              </if>
              <if test="contentFormat != null and contentFormat != ''">
                AND n.content_format = #{contentFormat}
              </if>
              <if test="sourceType != null and sourceType != ''">
                AND n.source_type = #{sourceType}
              </if>
              <if test="keyword != null and keyword != ''">
                AND LOWER(CONCAT_WS(' ', n.title, n.description))
                    LIKE CONCAT('%', LOWER(#{keyword}), '%')
              </if>
              <if test="cursorTime != null">
                <choose>
                  <when test="sort == 'STALLED_FIRST'">
                    AND (
                        (CASE WHEN n.need_status = 'CLAIMED'
                                   AND COALESCE(n.last_progress_at, n.claimed_at, n.update_time, n.create_time)
                                       &lt; DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY)
                              THEN 1 ELSE 0 END) &lt; #{cursorStalled}
                        OR (
                            (CASE WHEN n.need_status = 'CLAIMED'
                                       AND COALESCE(n.last_progress_at, n.claimed_at, n.update_time, n.create_time)
                                           &lt; DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 14 DAY)
                                  THEN 1 ELSE 0 END) = #{cursorStalled}
                            AND (
                                n.update_time &lt; #{cursorTime}
                                OR (n.update_time = #{cursorTime} AND n.id &lt; #{cursorId})
                            )
                        )
                    )
                  </when>
                  <when test="sort == 'UPDATED'">
                    AND (
                        n.update_time &lt; #{cursorTime}
                        OR (n.update_time = #{cursorTime} AND n.id &lt; #{cursorId})
                    )
                  </when>
                  <otherwise>
                    AND (
                        n.create_time &lt; #{cursorTime}
                        OR (n.create_time = #{cursorTime} AND n.id &lt; #{cursorId})
                    )
                  </otherwise>
                </choose>
              </if>
            <choose>
              <when test="sort == 'STALLED_FIRST'">
                ORDER BY stalled DESC, n.update_time DESC, n.id DESC
              </when>
              <when test="sort == 'UPDATED'">
                ORDER BY n.update_time DESC, n.id DESC
              </when>
              <otherwise>
                ORDER BY n.create_time DESC, n.id DESC
              </otherwise>
            </choose>
            LIMIT #{limit}
            </script>
            """)
    List<NeedDiscoveryRows.NeedRow> listNeeds(
            @Param("uid") Long uid,
            @Param("domain") Integer domain,
            @Param("status") String status,
            @Param("contentFormat") String contentFormat,
            @Param("sourceType") String sourceType,
            @Param("keyword") String keyword,
            @Param("sort") String sort,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("cursorStalled") Integer cursorStalled,
            @Param("limit") int limit);
}
