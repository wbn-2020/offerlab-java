package com.offerlab.community.question.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.question.infrastructure.persistence.po.CompanyAliasPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CompanyAliasMapper extends BaseMapper<CompanyAliasPO> {
    @Select("""
            SELECT *
            FROM t_company_alias
            WHERE status = 1
              AND (alias = #{name} OR canonical_company = #{name})
            ORDER BY alias = #{name} DESC
            LIMIT 1
            """)
    CompanyAliasPO findEnabledByName(@Param("name") String name);

    @Select("""
            SELECT *
            FROM t_company_alias
            WHERE alias = #{alias}
            LIMIT 1
            """)
    CompanyAliasPO findByAlias(@Param("alias") String alias);

    @Select("""
            SELECT *
            FROM t_company_alias
            WHERE status = 1
              AND canonical_company = #{canonical}
            ORDER BY alias ASC
            """)
    List<CompanyAliasPO> listAliases(@Param("canonical") String canonical);

    @Select("""
            <script>
            SELECT *
            FROM t_company_alias
            WHERE 1 = 1
              <if test="keyword != null and keyword != ''">
                AND (canonical_company LIKE CONCAT('%', #{keyword}, '%')
                  OR alias LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<CompanyAliasPO> listAdmin(@Param("keyword") String keyword, @Param("limit") int limit);

    @Update("""
            UPDATE t_company_alias
            SET status = #{status}, update_time = NOW(3)
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") int status);
}
