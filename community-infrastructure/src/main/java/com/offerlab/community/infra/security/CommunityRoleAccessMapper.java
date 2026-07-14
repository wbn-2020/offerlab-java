package com.offerlab.community.infra.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommunityRoleAccessMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                't_community_role_definition',
                't_community_role_grant',
                't_incentive_freeze_record'
              )
            """)
    int existingTableCount();

    @Select("""
            SELECT COUNT(*)
            FROM t_community_role_grant g
            JOIN t_community_role_definition d
              ON d.role_code = g.role_code
             AND d.domain_code = g.domain_code
             AND d.enabled = 1
            WHERE g.user_id = #{uid}
              AND g.role_code = #{roleCode}
              AND g.domain_code = #{domainCode}
              AND g.grant_status = 'ACTIVE'
              AND (g.expires_at IS NULL OR g.expires_at > CURRENT_TIMESTAMP(3))
              AND (
                    d.requires_no_risk_freeze = 0
                    OR NOT EXISTS (
                        SELECT 1
                        FROM t_incentive_freeze_record f
                        WHERE f.user_id = g.user_id
                          AND f.freeze_status = 'ACTIVE'
                    )
                  )
            """)
    int countActiveGrant(@Param("uid") Long uid,
                         @Param("roleCode") String roleCode,
                         @Param("domainCode") String domainCode);
}
