package com.offerlab.community.infra.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminAuditLogMapper {
    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_admin_audit_log'
            """)
    int tableExists();

    @Insert("""
            INSERT INTO t_admin_audit_log (
                id, operator_uid, action, resource_type, resource_id,
                before_json, after_json, remark
            )
            VALUES (
                #{id}, #{operatorUid}, #{action}, #{resourceType}, #{resourceId},
                CAST(#{beforeJson} AS JSON), CAST(#{afterJson} AS JSON), #{remark}
            )
            """)
    int insert(AdminAuditLog log);

    @Select("""
            <script>
            SELECT id,
                   operator_uid AS operatorUid,
                   action,
                   resource_type AS resourceType,
                   resource_id AS resourceId,
                   CAST(before_json AS CHAR) AS beforeJson,
                   CAST(after_json AS CHAR) AS afterJson,
                   remark
            FROM t_admin_audit_log
            WHERE 1 = 1
              <if test="action != null and action != ''">
                AND action = #{action}
              </if>
              <if test="resourceType != null and resourceType != ''">
                AND resource_type = #{resourceType}
              </if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AdminAuditLog> listRecent(@Param("action") String action,
                                   @Param("resourceType") String resourceType,
                                   @Param("limit") int limit);
}
