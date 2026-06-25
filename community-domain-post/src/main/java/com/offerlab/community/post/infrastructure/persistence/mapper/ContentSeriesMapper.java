package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ContentSeriesMapper extends BaseMapper<ContentSeriesPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_content_series'
            """)
    int tableExists();

    @Select("""
            SELECT id,
                   creator_uid AS creatorUid,
                   title,
                   description,
                   domain,
                   cover_url AS coverUrl,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_content_series
            WHERE creator_uid = #{creatorUid}
              AND is_deleted = 0
            ORDER BY update_time DESC, id DESC
            """)
    List<ContentSeriesPO> selectMine(@Param("creatorUid") Long creatorUid);

    @Select("""
            <script>
            SELECT s.id AS seriesId,
                   COALESCE(SUM(CASE WHEN p.id IS NOT NULL THEN 1 ELSE 0 END), 0) AS totalPostCount,
                   COALESCE(SUM(CASE WHEN p.id IS NOT NULL AND p.post_status = 1 THEN 1 ELSE 0 END), 0) AS publishedPostCount
            FROM t_content_series s
            LEFT JOIN t_content_series_post sp
                   ON sp.series_id = s.id
                  AND sp.is_deleted = 0
            LEFT JOIN t_post_main p
                   ON p.id = sp.post_id
                  AND p.is_deleted = 0
            WHERE s.id IN
            <foreach collection="seriesIds" item="seriesId" open="(" separator="," close=")">
              #{seriesId}
            </foreach>
            GROUP BY s.id
            </script>
            """)
    List<Map<String, Object>> selectProgressBySeriesIds(@Param("seriesIds") Collection<Long> seriesIds);

    @Select("""
            <script>
            SELECT DISTINCT s.id,
                   s.creator_uid AS creatorUid,
                   s.title,
                   s.description,
                   s.domain,
                   s.cover_url AS coverUrl,
                   s.create_time AS createTime,
                   s.update_time AS updateTime,
                   s.is_deleted AS isDeleted
            FROM t_content_series s
            JOIN t_content_series_post sp
              ON sp.series_id = s.id
             AND sp.is_deleted = 0
            WHERE s.is_deleted = 0
              AND sp.post_id IN
              <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
              </foreach>
            ORDER BY s.update_time DESC, s.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ContentSeriesPO> selectByPostIds(@Param("postIds") Collection<Long> postIds,
                                          @Param("limit") int limit);

    @Update("""
            UPDATE t_content_series
            SET update_time = NOW(3)
            WHERE id = #{seriesId}
              AND is_deleted = 0
            """)
    int touchSeries(@Param("seriesId") Long seriesId);
}
