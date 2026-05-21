package com.offerlab.community.infra.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRoleMapper {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_user_admin'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM t_user_admin
            WHERE uid = #{uid}
              AND role_code = 'ADMIN'
              AND enabled = 1
            """)
    int countActiveAdmin(@Param("uid") Long uid);

    @Select("""
            SELECT COUNT(*)
            FROM t_user_admin
            WHERE role_code = 'ADMIN'
              AND enabled = 1
            """)
    int countEnabledAdmins();
}
