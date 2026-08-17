package com.offerlab.community.post.collaboration.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NeedDeliveryCandidateMapper {

    @Select("""
            SELECT id,
                   claimed_by_uid AS claimedByUid,
                   domain,
                   content_format AS contentFormat,
                   need_status AS status
            FROM t_collab_content_need
            WHERE id = #{needId}
              AND moderation_hidden = 0
            LIMIT 1
            """)
    NeedDeliveryCandidateRows.NeedContextRow selectNeed(
            @Param("needId") Long needId);

    @Select("""
            <script>
            SELECT id,
                   resolutionType,
                   title,
                   domain,
                   postType,
                   publicPath,
                   createTime,
                   updateTime
            FROM (
                SELECT p.id AS id,
                       CASE WHEN p.post_type IN (4, 13) THEN 'QUESTION' ELSE 'POST' END AS resolutionType,
                       p.title AS title,
                       e.domain AS domain,
                       p.post_type AS postType,
                       CONCAT('/post/', p.id) AS publicPath,
                       p.create_time AS createTime,
                       p.update_time AS updateTime
                  FROM t_post_main p
                  LEFT JOIN t_post_extension e ON e.post_id = p.id
                 WHERE p.author_id = #{uid}
                   AND p.is_deleted = 0
                   AND p.post_status = 1
                   AND p.visibility = 1
                   AND p.content_environment = 'COMMUNITY'
                   <if test="resolutionType != null and resolutionType != ''">
                     <choose>
                       <when test="resolutionType == 'QUESTION'">
                         AND p.post_type IN (4, 13)
                       </when>
                       <when test="resolutionType == 'POST'">
                         AND p.post_type NOT IN (4, 13)
                       </when>
                       <when test="resolutionType == 'SERIES'">
                         AND 1 = 0
                       </when>
                     </choose>
                   </if>
                   <if test="keyword != null and keyword != ''">
                     AND LOWER(p.title) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   </if>

                UNION ALL

                SELECT s.id AS id,
                       'SERIES' AS resolutionType,
                       s.title AS title,
                       s.domain AS domain,
                       CAST(NULL AS SIGNED) AS postType,
                       CONCAT('/collaboration/series/', s.id) AS publicPath,
                       s.create_time AS createTime,
                       s.update_time AS updateTime
                 FROM t_collab_series s
                 WHERE s.moderation_hidden = 0
                   AND s.series_status IN ('OPEN', 'CLOSED')
                   AND (
                       s.owner_uid = #{uid}
                       OR EXISTS (
                           SELECT 1
                             FROM t_collab_series_contribution c
                            WHERE c.series_id = s.id
                              AND c.contributor_uid = #{uid}
                       )
                   )
                   <if test="resolutionType != null and resolutionType != ''">
                     AND #{resolutionType} = 'SERIES'
                   </if>
                   <if test="keyword != null and keyword != ''">
                     AND LOWER(s.title) LIKE CONCAT('%', LOWER(#{keyword}), '%')
                   </if>
            ) candidates
            WHERE (#{cursorTime} IS NULL
                   OR updateTime &lt; #{cursorTime}
                   OR (updateTime = #{cursorTime} AND id &lt; #{cursorId}))
            ORDER BY updateTime DESC, id DESC, resolutionType ASC
            LIMIT #{limit}
            </script>
            """)
    List<NeedDeliveryCandidateRows.CandidateRow> listCandidates(
            @Param("uid") Long uid,
            @Param("resolutionType") String resolutionType,
            @Param("keyword") String keyword,
            @Param("cursorTime") java.time.LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);
}
