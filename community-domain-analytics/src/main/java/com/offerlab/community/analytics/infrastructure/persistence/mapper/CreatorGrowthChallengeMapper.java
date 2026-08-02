package com.offerlab.community.analytics.infrastructure.persistence.mapper;

import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgeAwardRow;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgeAwardPO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthBadgePO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengePO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengeParticipationPO;
import com.offerlab.community.analytics.infrastructure.persistence.po.CreatorGrowthChallengeWorkspaceRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CreatorGrowthChallengeMapper {

    @Select("""
            SELECT c.id,
                   c.challenge_code AS challengeCode,
                   c.title,
                   c.description,
                   c.domain,
                   c.post_type AS postType,
                   c.assist_template_code AS assistTemplateCode,
                   c.status,
                   c.starts_at AS startsAt,
                   c.ends_at AS endsAt,
                   p.participation_status AS participationStatus,
                   p.completed_post_id AS completedPostId,
                   p.joined_at AS joinedAt,
                   p.completed_at AS completedAt
            FROM t_creator_growth_challenge c
            LEFT JOIN t_creator_growth_challenge_participation p
              ON p.challenge_id = c.id
             AND p.uid = #{uid}
            WHERE (c.status = 'PUBLISHED' AND c.ends_at > CURRENT_TIMESTAMP(3))
               OR p.id IS NOT NULL
            ORDER BY CASE WHEN c.status = 'PUBLISHED'
                                AND c.starts_at <= CURRENT_TIMESTAMP(3)
                                AND c.ends_at > CURRENT_TIMESTAMP(3)
                          THEN 0 ELSE 1 END,
                     c.ends_at ASC,
                     c.id ASC
            LIMIT 20
            """)
    List<CreatorGrowthChallengeWorkspaceRow> selectWorkspace(@Param("uid") Long uid);

    @Select("""
            SELECT * FROM t_creator_growth_challenge
            WHERE id = #{challengeId}
            LIMIT 1
            """)
    CreatorGrowthChallengePO selectChallengeById(@Param("challengeId") Long challengeId);

    @Select("""
            SELECT * FROM t_creator_growth_challenge
            WHERE challenge_code = #{challengeCode}
            LIMIT 1
            """)
    CreatorGrowthChallengePO selectChallengeByCode(@Param("challengeCode") String challengeCode);

    @Select("""
            SELECT * FROM t_creator_growth_challenge
            WHERE id = #{challengeId}
            LIMIT 1 FOR UPDATE
            """)
    CreatorGrowthChallengePO lockChallengeById(@Param("challengeId") Long challengeId);

    @Select("""
            SELECT id,
                   challenge_id AS challengeId,
                   uid,
                   participation_status AS status,
                   completed_post_id AS completedPostId,
                   joined_at AS joinedAt,
                   completed_at AS completedAt,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_creator_growth_challenge_participation
            WHERE challenge_id = #{challengeId} AND uid = #{uid}
            LIMIT 1 FOR UPDATE
            """)
    CreatorGrowthChallengeParticipationPO lockParticipation(
            @Param("challengeId") Long challengeId,
            @Param("uid") Long uid);

    @Insert("""
            INSERT INTO t_creator_growth_challenge_participation(
                id, challenge_id, uid, participation_status, joined_at
            ) VALUES (
                #{id}, #{challengeId}, #{uid}, 'JOINED', CURRENT_TIMESTAMP(3)
            )
            """)
    int insertParticipation(CreatorGrowthChallengeParticipationPO participation);

    @Update("""
            UPDATE t_creator_growth_challenge_participation
            SET participation_status = 'WITHDRAWN'
            WHERE id = #{participationId}
              AND uid = #{uid}
              AND participation_status = 'JOINED'
            """)
    int withdrawParticipation(@Param("participationId") Long participationId, @Param("uid") Long uid);

    @Update("""
            UPDATE t_creator_growth_challenge_participation
            SET participation_status = 'COMPLETED',
                completed_post_id = #{postId},
                completed_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{participationId}
              AND uid = #{uid}
              AND participation_status = 'JOINED'
              AND completed_post_id IS NULL
            """)
    int completeParticipation(@Param("participationId") Long participationId,
                              @Param("uid") Long uid,
                              @Param("postId") Long postId);

    @Select("""
            SELECT COUNT(DISTINCT challenge_id)
            FROM t_creator_growth_challenge_participation
            WHERE uid = #{uid}
              AND participation_status = 'COMPLETED'
            """)
    long countCompletedChallenges(@Param("uid") Long uid);

    @Select("""
            SELECT * FROM t_creator_growth_badge
            WHERE enabled = 1
              AND required_completed_challenge_count <= #{completedCount}
            ORDER BY required_completed_challenge_count ASC, id ASC
            """)
    List<CreatorGrowthBadgePO> selectEligibleBadges(@Param("completedCount") long completedCount);

    @Insert("""
            INSERT IGNORE INTO t_creator_growth_badge_award(
                id, uid, badge_id, source_challenge_id, award_status, awarded_at
            ) VALUES (
                #{id}, #{uid}, #{badgeId}, #{sourceChallengeId}, 'ACTIVE', CURRENT_TIMESTAMP(3)
            )
            """)
    int insertBadgeAward(CreatorGrowthBadgeAwardPO award);

    @Select("""
            SELECT b.badge_code AS badgeCode,
                   b.title,
                   b.description,
                   b.required_completed_challenge_count AS requiredCompletedChallengeCount,
                   a.awarded_at AS awardedAt
            FROM t_creator_growth_badge_award a
            JOIN t_creator_growth_badge b ON b.id = a.badge_id
            WHERE a.uid = #{uid}
              AND a.award_status = 'ACTIVE'
            ORDER BY a.awarded_at DESC, a.id DESC
            """)
    List<CreatorGrowthBadgeAwardRow> selectAwardedBadges(@Param("uid") Long uid);

    @Select("""
            SELECT * FROM t_creator_growth_challenge
            ORDER BY CASE status WHEN 'DRAFT' THEN 0 WHEN 'PUBLISHED' THEN 1 ELSE 2 END,
                     starts_at DESC,
                     id DESC
            LIMIT #{limit}
            """)
    List<CreatorGrowthChallengePO> selectAdminChallenges(@Param("limit") int limit);

    @Insert("""
            INSERT INTO t_creator_growth_challenge(
                id, challenge_code, title, description, domain, post_type,
                assist_template_code, status, starts_at, ends_at, operator_uid
            ) VALUES (
                #{id}, #{challengeCode}, #{title}, #{description}, #{domain}, #{postType},
                #{assistTemplateCode}, 'DRAFT', #{startsAt}, #{endsAt}, #{operatorUid}
            )
            """)
    int insertChallenge(CreatorGrowthChallengePO challenge);

    @Update("""
            UPDATE t_creator_growth_challenge
            SET title = #{title},
                description = #{description},
                domain = #{domain},
                post_type = #{postType},
                assist_template_code = #{assistTemplateCode},
                starts_at = #{startsAt},
                ends_at = #{endsAt},
                operator_uid = #{operatorUid}
            WHERE id = #{id}
              AND challenge_code = #{challengeCode}
              AND status = 'DRAFT'
            """)
    int updateDraftChallenge(CreatorGrowthChallengePO challenge);

    @Update("""
            UPDATE t_creator_growth_challenge
            SET status = 'PUBLISHED',
                operator_uid = #{operatorUid}
            WHERE id = #{challengeId}
              AND status = 'DRAFT'
            """)
    int publishChallenge(@Param("challengeId") Long challengeId, @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_creator_growth_challenge
            SET status = 'OFFLINE',
                operator_uid = #{operatorUid}
            WHERE id = #{challengeId}
              AND status = 'PUBLISHED'
            """)
    int offlineChallenge(@Param("challengeId") Long challengeId, @Param("operatorUid") Long operatorUid);
}
