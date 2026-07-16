package com.offerlab.community.search.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchContentGapPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SearchContentGapMapper extends BaseMapper<SearchContentGapPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_search_content_gap'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_search_content_gap (
                id, gap_key, keyword, cluster_id, reason_text, window_days,
                search_count, no_result_count, weak_result_count, min_sample_met,
                risk_level, target_stage, gap_status, source, source_refs_json,
                created_from, last_seen_at
            ) VALUES (
                #{id}, #{gapKey}, #{keyword}, #{clusterId}, #{reasonText}, #{windowDays},
                #{searchCount}, #{noResultCount}, #{weakResultCount}, #{minSampleMet},
                #{riskLevel}, #{targetStage}, #{gapStatus}, #{source}, #{sourceRefsJson},
                #{createdFrom}, #{lastSeenAt}
            )
            ON DUPLICATE KEY UPDATE
                keyword = VALUES(keyword),
                cluster_id = VALUES(cluster_id),
                reason_text = VALUES(reason_text),
                window_days = VALUES(window_days),
                search_count = VALUES(search_count),
                no_result_count = VALUES(no_result_count),
                weak_result_count = VALUES(weak_result_count),
                min_sample_met = VALUES(min_sample_met),
                risk_level = VALUES(risk_level),
                target_stage = VALUES(target_stage),
                source = VALUES(source),
                source_refs_json = VALUES(source_refs_json),
                created_from = VALUES(created_from),
                last_seen_at = CASE
                    WHEN last_seen_at IS NULL THEN VALUES(last_seen_at)
                    WHEN VALUES(last_seen_at) IS NULL THEN last_seen_at
                    WHEN VALUES(last_seen_at) > last_seen_at THEN VALUES(last_seen_at)
                    ELSE last_seen_at
                END,
                gap_status = CASE
                    WHEN gap_status IN ('CANDIDATE', 'REVIEW_REQUIRED') THEN VALUES(gap_status)
                    ELSE gap_status
                END,
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsertCandidate(SearchContentGapPO gap);

    @Select("""
            SELECT *
            FROM t_search_content_gap
            WHERE gap_key = #{gapKey}
            LIMIT 1
            """)
    SearchContentGapPO findByGapKey(@Param("gapKey") String gapKey);

    @Select("""
            SELECT *
            FROM t_search_content_gap
            WHERE gap_key = #{gapKey}
            LIMIT 1
            FOR UPDATE
            """)
    SearchContentGapPO lockByGapKey(@Param("gapKey") String gapKey);

    @Select("""
            <script>
            SELECT *
            FROM t_search_content_gap
            WHERE 1 = 1
            <if test="status != null and status != ''">
              AND gap_status = #{status}
            </if>
            <if test="riskLevel != null and riskLevel != ''">
              AND risk_level = #{riskLevel}
            </if>
            <if test="domain != null">
              AND domain = #{domain}
            </if>
            ORDER BY
                CASE gap_status
                    WHEN 'REVIEW_REQUIRED' THEN 0
                    WHEN 'CANDIDATE' THEN 1
                    WHEN 'APPROVED' THEN 2
                    WHEN 'CONVERTED' THEN 3
                    WHEN 'FULFILLED' THEN 4
                    ELSE 5
                END,
                last_seen_at DESC,
                id DESC
            LIMIT #{limit}
            </script>
            """)
    List<SearchContentGapPO> list(@Param("status") String status,
                                  @Param("riskLevel") String riskLevel,
                                  @Param("domain") Integer domain,
                                  @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_search_content_gap
            WHERE gap_status = 'APPROVED'
              AND min_sample_met = 1
              AND risk_level <> 'HIGH'
            ORDER BY last_seen_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<SearchContentGapPO> listApprovedForDownstream(@Param("limit") int limit);

    @Update("""
            UPDATE t_search_content_gap
            SET gap_status = 'APPROVED',
                domain = #{domain},
                reviewed_by = #{operatorUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND gap_status IN ('CANDIDATE', 'REVIEW_REQUIRED')
            """)
    int approve(@Param("id") Long id,
                @Param("domain") Integer domain,
                @Param("operatorUid") Long operatorUid,
                @Param("note") String note);

    @Update("""
            UPDATE t_search_content_gap
            SET gap_status = 'IGNORED',
                domain = #{domain},
                reviewed_by = #{operatorUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND gap_status IN ('CANDIDATE', 'REVIEW_REQUIRED', 'APPROVED')
            """)
    int ignore(@Param("id") Long id,
               @Param("domain") Integer domain,
               @Param("operatorUid") Long operatorUid,
               @Param("note") String note);

    @Update("""
            UPDATE t_search_content_gap
            SET gap_status = 'CONVERTED',
                domain = #{domain},
                converted_need_id = #{needId},
                reviewed_by = #{operatorUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND gap_status = 'APPROVED'
              AND converted_need_id IS NULL
            """)
    int convert(@Param("id") Long id,
                @Param("domain") Integer domain,
                @Param("needId") Long needId,
                @Param("operatorUid") Long operatorUid,
                @Param("note") String note);

    @Update("""
            UPDATE t_search_content_gap
            SET gap_status = 'FULFILLED',
                resolution_type = #{resolutionType},
                resolution_id = #{resolutionId},
                resolution_post_id = #{resolutionPostId},
                reviewed_by = #{operatorUid},
                review_note = #{note},
                reviewed_at = CURRENT_TIMESTAMP(3),
                fulfilled_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND gap_status = 'APPROVED'
            """)
    int resolve(@Param("id") Long id,
                @Param("resolutionType") String resolutionType,
                @Param("resolutionId") Long resolutionId,
                @Param("resolutionPostId") Long resolutionPostId,
                @Param("operatorUid") Long operatorUid,
                @Param("note") String note);

    @Update("""
            UPDATE t_search_content_gap
            SET gap_status = 'FULFILLED',
                resolution_type = #{resolutionType},
                resolution_id = #{resolutionId},
                resolution_post_id = #{resolutionPostId},
                fulfilled_at = CURRENT_TIMESTAMP(3),
                update_time = CURRENT_TIMESTAMP(3)
            WHERE converted_need_id = #{needId}
              AND gap_status = 'CONVERTED'
            """)
    int fulfillFromNeed(@Param("needId") Long needId,
                        @Param("resolutionType") String resolutionType,
                        @Param("resolutionId") Long resolutionId,
                        @Param("resolutionPostId") Long resolutionPostId);
}
