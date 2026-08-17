package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.api.dto.PublicPostUpdateDTO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostContentRevisionCandidateRow;
import com.offerlab.community.post.infrastructure.persistence.projection.PostContentRevisionQueryRow;
import com.offerlab.community.post.infrastructure.persistence.po.PostVersionHistoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PostVersionHistoryMapper extends BaseMapper<PostVersionHistoryPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_post_version_history'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND (
                    (table_name = 't_post_version_history'
                     AND column_name IN (
                         'quality_signal_revision',
                         'quality_signal_revision_state',
                         'quality_signal_effective_at',
                         'quality_signal_revision_token'
                     ))
                    OR
                    (table_name = 't_post_main'
                     AND column_name IN (
                         'latest_effective_content_revision_at',
                         'latest_effective_content_revision_token'
                     ))
                  )
            """)
    int qualitySignalSchemaColumnCount();

    @Select("""
            SELECT *
            FROM t_post_version_history
            WHERE post_id = #{postId}
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostVersionHistoryPO> selectRecentByPost(@Param("postId") Long postId, @Param("limit") int limit);

    @Select("""
            SELECT result_version AS resultVersion,
                   public_update_summary AS publicUpdateSummary,
                   impact_scope AS impactScope,
                   create_time AS createTime
            FROM t_post_version_history
            WHERE post_id = #{postId}
              AND result_version IS NOT NULL
              AND result_version > 0
              AND public_update_summary IS NOT NULL
              AND LENGTH(TRIM(public_update_summary)) > 0
              AND EXISTS (
                    SELECT 1
                    FROM t_post_main p
                    WHERE p.id = t_post_version_history.post_id
                      AND p.is_deleted = 0
                      AND p.post_status = 1
                      AND (p.visibility = 1 OR p.visibility IS NULL)
                      AND p.content_environment = 'COMMUNITY'
              )
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PublicPostUpdateDTO> listPublicUpdates(@Param("postId") Long postId, @Param("limit") int limit);

    @Update("""
            UPDATE t_post_version_history
            SET quality_signal_revision_state = 'SUPERSEDED'
            WHERE post_id = #{postId}
              AND result_version <> #{resultVersion}
              AND quality_signal_revision_state = 'CANDIDATE'
            """)
    int supersedeOtherPendingQualityRevisions(@Param("postId") Long postId,
                                              @Param("resultVersion") Integer resultVersion);

    @Select("""
            SELECT post_id AS postId,
                   result_version AS resultVersion,
                   quality_signal_revision_token AS revisionToken
            FROM t_post_version_history
            WHERE post_id = #{postId}
              AND result_version = #{resultVersion}
              AND quality_signal_revision_state = 'CANDIDATE'
            LIMIT 1
            """)
    PostContentRevisionCandidateRow selectPendingQualityRevision(@Param("postId") Long postId,
                                                                  @Param("resultVersion") Integer resultVersion);

    @Update("""
            UPDATE t_post_version_history
            SET quality_signal_revision_state = 'EFFECTIVE',
                quality_signal_effective_at = #{effectiveAt}
            WHERE post_id = #{postId}
              AND result_version = #{resultVersion}
              AND quality_signal_revision_token = #{revisionToken}
              AND quality_signal_revision_state = 'CANDIDATE'
            """)
    int activateQualityRevision(@Param("postId") Long postId,
                                @Param("resultVersion") Integer resultVersion,
                                @Param("revisionToken") String revisionToken,
                                @Param("effectiveAt") LocalDateTime effectiveAt);

    @Update("""
            UPDATE t_post_version_history
            SET quality_signal_revision_state = 'REJECTED'
            WHERE post_id = #{postId}
              AND result_version = #{resultVersion}
              AND quality_signal_revision_token = #{revisionToken}
              AND quality_signal_revision_state = 'CANDIDATE'
            """)
    int rejectQualityRevision(@Param("postId") Long postId,
                              @Param("resultVersion") Integer resultVersion,
                              @Param("revisionToken") String revisionToken);

    @Update("""
            UPDATE t_post_main
            SET latest_effective_content_revision_at = #{effectiveAt},
                latest_effective_content_revision_token = #{revisionToken}
            WHERE id = #{postId}
              AND is_deleted = 0
              AND post_status = 1
              AND (visibility = 1 OR visibility IS NULL)
              AND content_environment = 'COMMUNITY'
            """)
    int updateLatestEffectiveContentRevision(@Param("postId") Long postId,
                                             @Param("revisionToken") String revisionToken,
                                             @Param("effectiveAt") LocalDateTime effectiveAt);

    @Select("""
            <script>
            SELECT p.id AS postId,
                   p.author_id AS authorId,
                   p.visibility AS visibility,
                   p.post_status AS postStatus,
                   p.is_deleted AS isDeleted,
                   p.content_environment AS contentEnvironment,
                   CAST(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.domain')), '') AS UNSIGNED) AS domain,
                   CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false')
                             IN ('true', '1') THEN 1 ELSE 0 END AS anonymous,
                   p.latest_effective_content_revision_at AS latestEffectiveContentRevisionAt,
                   p.latest_effective_content_revision_token AS latestEffectiveContentRevisionToken,
                   h.result_version AS effectivePublishedPostVersion,
                   h.quality_signal_revision_state AS qualitySignalRevisionState,
                   h.quality_signal_effective_at AS qualitySignalEffectiveAt,
                   h.quality_signal_revision_token AS qualitySignalRevisionToken
            FROM t_post_main p
            LEFT JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_version_history h
              ON h.post_id = p.id
             AND h.quality_signal_revision_state = 'EFFECTIVE'
             AND h.quality_signal_revision = h.result_version
             AND h.quality_signal_revision_token = p.latest_effective_content_revision_token
            WHERE p.id IN
            <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
            </foreach>
            </script>
            """)
    List<PostContentRevisionQueryRow> selectContentRevisionQueryRows(@Param("postIds") List<Long> postIds);

    @Select("""
            SELECT MAX(p.id)
            FROM t_post_main p
            INNER JOIN t_post_extension e ON e.post_id = p.id
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND (p.visibility = 1 OR p.visibility IS NULL)
              AND p.content_environment = 'COMMUNITY'
              AND (e.domain <> 2
                   OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false')
                       NOT IN ('true', '1'))
              AND e.domain = #{domain}
            """)
    Long selectMaxPublicContentRevisionBoundaryPostId(@Param("domain") Integer domain);

    @Select("""
            SELECT p.id AS postId,
                   p.author_id AS authorId,
                   p.visibility AS visibility,
                   p.post_status AS postStatus,
                   p.is_deleted AS isDeleted,
                   p.content_environment AS contentEnvironment,
                   e.domain AS domain,
                   CASE WHEN COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false')
                             IN ('true', '1') THEN 1 ELSE 0 END AS anonymous,
                   p.latest_effective_content_revision_at AS latestEffectiveContentRevisionAt,
                   p.latest_effective_content_revision_token AS latestEffectiveContentRevisionToken,
                   h.result_version AS effectivePublishedPostVersion,
                   h.quality_signal_revision_state AS qualitySignalRevisionState,
                   h.quality_signal_effective_at AS qualitySignalEffectiveAt,
                   h.quality_signal_revision_token AS qualitySignalRevisionToken
            FROM t_post_main p
            INNER JOIN t_post_extension e ON e.post_id = p.id
            LEFT JOIN t_post_version_history h
              ON h.post_id = p.id
             AND h.quality_signal_revision_state = 'EFFECTIVE'
             AND h.quality_signal_revision = h.result_version
             AND h.quality_signal_revision_token = p.latest_effective_content_revision_token
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND (p.visibility = 1 OR p.visibility IS NULL)
              AND p.content_environment = 'COMMUNITY'
              AND (e.domain <> 2
                   OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false')
                       NOT IN ('true', '1'))
              AND e.domain = #{domain}
              AND p.id > #{afterPostId}
              AND p.id <= #{snapshotUpperBoundPostId}
            ORDER BY p.id ASC
            LIMIT #{limit}
            """)
    List<PostContentRevisionQueryRow> selectPublicContentRevisionBoundaryRows(
            @Param("domain") Integer domain,
            @Param("afterPostId") Long afterPostId,
            @Param("snapshotUpperBoundPostId") Long snapshotUpperBoundPostId,
            @Param("limit") int limit);

    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM t_post_main p
                INNER JOIN t_post_extension e ON e.post_id = p.id
                WHERE p.is_deleted = 0
                  AND p.post_status = 1
                  AND (p.visibility = 1 OR p.visibility IS NULL)
                  AND p.content_environment = 'COMMUNITY'
                  AND (e.domain <> 2
                       OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(e.ext_json, '$.anonymous')), 'false')
                           NOT IN ('true', '1'))
                  AND e.domain = #{domain}
                  AND p.id > #{afterPostId}
                  AND p.id <= #{snapshotUpperBoundPostId}
            )
            """)
    boolean existsPublicContentRevisionBoundaryAfter(
            @Param("domain") Integer domain,
            @Param("afterPostId") Long afterPostId,
            @Param("snapshotUpperBoundPostId") Long snapshotUpperBoundPostId);
}
