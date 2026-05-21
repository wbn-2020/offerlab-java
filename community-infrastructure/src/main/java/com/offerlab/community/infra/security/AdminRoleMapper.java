package com.offerlab.community.infra.security;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

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

    @Select("""
            SELECT COUNT(*)
            FROM t_user_admin
            WHERE role_code = 'ADMIN'
            """)
    int countAdminRows();

    @Select("""
            SELECT uid,
                   role_code AS roleCode,
                   enabled,
                   remark,
                   operator_uid AS operatorUid,
                   create_time AS createTime,
                   update_time AS updateTime
            FROM t_user_admin
            WHERE role_code = 'ADMIN'
            ORDER BY enabled DESC, update_time DESC, uid ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> listAdmins(@Param("limit") int limit);

    @Insert("""
            INSERT INTO t_user_admin(uid, role_code, enabled, remark, operator_uid)
            VALUES (#{uid}, 'ADMIN', 1, #{remark}, #{operatorUid})
            ON DUPLICATE KEY UPDATE
                enabled = 1,
                remark = VALUES(remark),
                operator_uid = VALUES(operator_uid),
                update_time = NOW(3)
            """)
    int upsertAdmin(@Param("uid") Long uid, @Param("remark") String remark, @Param("operatorUid") Long operatorUid);

    @Update("""
            UPDATE t_user_admin
            SET enabled = #{enabled},
                remark = #{remark},
                operator_uid = #{operatorUid},
                update_time = NOW(3)
            WHERE uid = #{uid}
              AND role_code = 'ADMIN'
            """)
    int updateAdminStatus(@Param("uid") Long uid,
                          @Param("enabled") int enabled,
                          @Param("remark") String remark,
                          @Param("operatorUid") Long operatorUid);
}
