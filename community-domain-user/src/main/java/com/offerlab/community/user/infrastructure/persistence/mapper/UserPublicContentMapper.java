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
}
