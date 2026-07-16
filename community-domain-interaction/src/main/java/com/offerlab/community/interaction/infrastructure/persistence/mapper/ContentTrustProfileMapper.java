package com.offerlab.community.interaction.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.ContentTrustProfilePO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContentTrustProfileMapper extends BaseMapper<ContentTrustProfilePO> {

    @Select("""
            SELECT post_id, author_uid, author_role, experience_start_at, experience_end_at,
                   applicable_audience, applicable_context, process_summary, outcome_summary,
                   known_limitations, source_summary, interest_disclosure, completeness_score,
                   last_confirmed_at, profile_version, create_time, update_time
            FROM t_int_content_trust_profile
            WHERE post_id = #{postId}
            LIMIT 1
            """)
    ContentTrustProfilePO selectByPostId(@Param("postId") Long postId);

    @Insert("""
            INSERT INTO t_int_content_trust_profile (
                post_id, author_uid, author_role, experience_start_at, experience_end_at,
                applicable_audience, applicable_context, process_summary, outcome_summary,
                known_limitations, source_summary, interest_disclosure, completeness_score,
                last_confirmed_at, profile_version, create_time, update_time
            ) VALUES (
                #{profile.postId}, #{profile.authorUid}, #{profile.authorRole},
                #{profile.experienceStartAt}, #{profile.experienceEndAt},
                #{profile.applicableAudience}, #{profile.applicableContext},
                #{profile.processSummary}, #{profile.outcomeSummary},
                #{profile.knownLimitations}, #{profile.sourceSummary},
                #{profile.interestDisclosure}, #{profile.completenessScore},
                CURRENT_TIMESTAMP(3), 1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
            )
            ON DUPLICATE KEY UPDATE
                author_uid = VALUES(author_uid),
                author_role = VALUES(author_role),
                experience_start_at = VALUES(experience_start_at),
                experience_end_at = VALUES(experience_end_at),
                applicable_audience = VALUES(applicable_audience),
                applicable_context = VALUES(applicable_context),
                process_summary = VALUES(process_summary),
                outcome_summary = VALUES(outcome_summary),
                known_limitations = VALUES(known_limitations),
                source_summary = VALUES(source_summary),
                interest_disclosure = VALUES(interest_disclosure),
                completeness_score = VALUES(completeness_score),
                last_confirmed_at = CURRENT_TIMESTAMP(3),
                profile_version = profile_version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            """)
    int upsert(@Param("profile") ContentTrustProfilePO profile);
}
