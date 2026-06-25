package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.DomainConfigPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DomainConfigMapper extends BaseMapper<DomainConfigPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_domain_config'
            """)
    int tableExists();

    @Select("""
            SELECT domain,
                   domain_name AS domainName,
                   domain_slug AS domainSlug,
                   description,
                   sort_order AS sortOrder,
                   enabled,
                   risk_level AS riskLevel,
                   posting_notice AS postingNotice,
                   browse_notice AS browseNotice,
                   interaction_notice AS interactionNotice,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_domain_config
            WHERE enabled = 1
            ORDER BY sort_order ASC, domain ASC
            """)
    List<DomainConfigPO> selectPublicList();

    @Select("""
            SELECT domain,
                   domain_name AS domainName,
                   domain_slug AS domainSlug,
                   description,
                   sort_order AS sortOrder,
                   enabled,
                   risk_level AS riskLevel,
                   posting_notice AS postingNotice,
                   browse_notice AS browseNotice,
                   interaction_notice AS interactionNotice,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_domain_config
            ORDER BY sort_order ASC, domain ASC
            """)
    List<DomainConfigPO> selectAdminList();

    @Select("""
            SELECT domain,
                   domain_name AS domainName,
                   domain_slug AS domainSlug,
                   description,
                   sort_order AS sortOrder,
                   enabled,
                   risk_level AS riskLevel,
                   posting_notice AS postingNotice,
                   browse_notice AS browseNotice,
                   interaction_notice AS interactionNotice,
                   created_by AS createdBy,
                   updated_by AS updatedBy,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_domain_config
            WHERE domain = #{domain}
            """)
    DomainConfigPO selectByDomain(@Param("domain") Integer domain);

    @Update("""
            UPDATE t_domain_config
            SET domain_name = #{domainName},
                domain_slug = #{domainSlug},
                description = #{description},
                sort_order = #{sortOrder},
                enabled = #{enabled},
                risk_level = #{riskLevel},
                posting_notice = #{postingNotice},
                browse_notice = #{browseNotice},
                interaction_notice = #{interactionNotice},
                updated_by = #{updatedBy},
                update_time = NOW(3)
            WHERE domain = #{domain}
            """)
    int updateDomainConfig(@Param("domain") Integer domain,
                           @Param("domainName") String domainName,
                           @Param("domainSlug") String domainSlug,
                           @Param("description") String description,
                           @Param("sortOrder") Integer sortOrder,
                           @Param("enabled") Integer enabled,
                           @Param("riskLevel") String riskLevel,
                           @Param("postingNotice") String postingNotice,
                           @Param("browseNotice") String browseNotice,
                           @Param("interactionNotice") String interactionNotice,
                           @Param("updatedBy") Long updatedBy);
}
