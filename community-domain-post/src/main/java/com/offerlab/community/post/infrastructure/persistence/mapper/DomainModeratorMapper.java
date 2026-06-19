package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.DomainModeratorPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DomainModeratorMapper extends BaseMapper<DomainModeratorPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_domain_moderator'
            """)
    int tableExists();

    @Select("""
            <script>
            SELECT id,
                   uid,
                   domain,
                   enabled,
                   created_by AS createdBy,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_domain_moderator
            WHERE 1 = 1
              <if test="domain != null">
              AND domain = #{domain}
              </if>
              <if test="enabled != null">
              AND enabled = #{enabled}
              </if>
            ORDER BY enabled DESC, domain ASC, update_time DESC, uid ASC
            LIMIT #{limit}
            </script>
            """)
    List<DomainModeratorPO> selectByDomain(@Param("domain") Integer domain,
                                           @Param("enabled") Integer enabled,
                                           @Param("limit") int limit);

    @Select("""
            SELECT id,
                   uid,
                   domain,
                   enabled,
                   created_by AS createdBy,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_domain_moderator
            WHERE uid = #{uid}
              AND enabled = 1
            ORDER BY domain ASC
            """)
    List<DomainModeratorPO> selectEnabledByUid(@Param("uid") Long uid);

    @Select("""
            SELECT COUNT(*)
            FROM t_domain_moderator
            WHERE uid = #{uid}
              AND domain = #{domain}
              AND enabled = 1
            """)
    int countActive(@Param("uid") Long uid, @Param("domain") Integer domain);

    @Insert("""
            INSERT INTO t_domain_moderator(id, uid, domain, enabled, created_by)
            VALUES (#{id}, #{uid}, #{domain}, 1, #{createdBy})
            ON DUPLICATE KEY UPDATE
                enabled = 1,
                created_by = VALUES(created_by),
                update_time = NOW(3)
            """)
    int upsertModerator(@Param("id") Long id,
                        @Param("uid") Long uid,
                        @Param("domain") Integer domain,
                        @Param("createdBy") Long createdBy);

    @Update("""
            UPDATE t_domain_moderator
            SET enabled = #{enabled},
                update_time = NOW(3)
            WHERE uid = #{uid}
              AND domain = #{domain}
            """)
    int updateModeratorStatus(@Param("uid") Long uid,
                              @Param("domain") Integer domain,
                              @Param("enabled") int enabled);
}
