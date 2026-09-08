package com.offerlab.community.user.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserPublicContentMapper {

    @Select("""
            <script>
            SELECT p.author_id AS authorId,
                   COUNT(*) AS postCount
            FROM t_post_main p
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.content_environment = 'COMMUNITY'
              AND p.author_id IN
              <foreach collection="authorIds" item="authorId" open="(" separator="," close=")">
                #{authorId}
              </foreach>
            GROUP BY p.author_id
            </script>
            """)
    List<Map<String, Object>> countPublicPostsByAuthors(@Param("authorIds") Collection<Long> authorIds);

    /**
     * 按公开内容数倒序返回 Top 作者（仅统计公开、已发布、非删除的 COMMUNITY 内容）。
     * 供空关键词"推荐作者"发现入口直接选人，避免从最近活跃账号里碰运气。
     * 索引支撑：idx_post_public_author_time (is_deleted, post_status, visibility, author_id, create_time, id)。
     * 返回 authorId/postCount 两列，行数由 limit 约束（服务端传入 SqlLimits 夹紧后的常量）。
     */
    @Select("""
            SELECT p.author_id AS authorId,
                   COUNT(*) AS postCount
            FROM t_post_main p
            WHERE p.is_deleted = 0
              AND p.post_status = 1
              AND p.visibility = 1
              AND p.content_environment = 'COMMUNITY'
            GROUP BY p.author_id
            ORDER BY postCount DESC, p.author_id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topPublicAuthors(@Param("limit") int limit);
}
