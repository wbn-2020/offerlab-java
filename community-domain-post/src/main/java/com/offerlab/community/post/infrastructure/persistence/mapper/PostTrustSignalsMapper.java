package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.offerlab.community.post.infrastructure.persistence.projection.PostTrustSignalsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PostTrustSignalsMapper {

    @Select("""
            <script>
            SELECT p.id AS postId,
                   CASE WHEN tp.post_id IS NULL THEN 0 ELSE 1 END AS profileAvailable,
                   COALESCE(tp.completeness_score, 0) AS completenessScore,
                   tp.last_confirmed_at AS lastConfirmedAt,
                   ts.freshness_status AS freshnessStatus,
                   ts.accepted_comment_id AS acceptedCommentId,
                   CAST(COALESCE(SUM(CASE
                       WHEN cs.decision IN ('ACCEPTED', 'PARTIAL_ACCEPTED', 'MERGED') THEN 1
                       ELSE 0 END), 0) AS SIGNED) AS acceptedSuggestionCount,
                   CAST(COALESCE(SUM(CASE
                       WHEN cs.public_note IS NOT NULL
                        AND cs.public_note &lt;&gt; ''
                        AND cs.suggestion_type IN ('CORRECTION', 'COUNTEREXAMPLE', 'CONDITIONS', 'SOURCE')
                       THEN 1 ELSE 0 END), 0) AS SIGNED) AS publicCorrectionCount,
                   CASE WHEN tp.source_summary IS NULL OR tp.source_summary = '' THEN 0 ELSE 1 END AS sourceComplete
            FROM t_post_main p
            LEFT JOIN t_int_content_trust_profile tp ON tp.post_id = p.id
            LEFT JOIN t_int_post_trust_state ts ON ts.post_id = p.id
            LEFT JOIN t_int_content_suggestion cs ON cs.post_id = p.id
            WHERE p.id IN
            <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
            </foreach>
            GROUP BY p.id, tp.post_id, tp.completeness_score, tp.last_confirmed_at,
                     tp.source_summary, ts.freshness_status, ts.accepted_comment_id
            </script>
            """)
    List<PostTrustSignalsRow> selectByPostIds(@Param("postIds") Collection<Long> postIds);
}
