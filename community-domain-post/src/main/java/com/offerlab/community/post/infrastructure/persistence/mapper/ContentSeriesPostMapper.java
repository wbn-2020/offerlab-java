package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPostPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ContentSeriesPostMapper extends BaseMapper<ContentSeriesPostPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_content_series_post'
            """)
    int tableExists();

    @Select("""
            SELECT COUNT(*)
            FROM t_content_series_post
            WHERE series_id = #{seriesId}
              AND post_id = #{postId}
              AND is_deleted = 0
            """)
    int existsActiveRelation(@Param("seriesId") Long seriesId, @Param("postId") Long postId);

    @Select("""
            SELECT MAX(sort_order)
            FROM t_content_series_post
            WHERE series_id = #{seriesId}
              AND is_deleted = 0
            """)
    Integer selectMaxSortOrder(@Param("seriesId") Long seriesId);

    @Select("""
            <script>
            SELECT id,
                   series_id AS seriesId,
                   post_id AS postId,
                   sort_order AS sortOrder,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_content_series_post
            WHERE is_deleted = 0
              AND post_id IN
              <foreach collection="postIds" item="postId" open="(" separator="," close=")">
                #{postId}
              </foreach>
            ORDER BY series_id ASC, sort_order ASC, id ASC
            </script>
            """)
    List<ContentSeriesPostPO> selectActiveByPostIds(@Param("postIds") Collection<Long> postIds);

    @Select("""
            SELECT id,
                   series_id AS seriesId,
                   post_id AS postId,
                   sort_order AS sortOrder,
                   create_time AS createTime,
                   update_time AS updateTime,
                   is_deleted AS isDeleted
            FROM t_content_series_post
            WHERE series_id = #{seriesId}
              AND is_deleted = 0
            ORDER BY sort_order ASC, id ASC
            LIMIT #{limit}
            """)
    List<ContentSeriesPostPO> selectActivePostsBySeriesId(@Param("seriesId") Long seriesId,
                                                          @Param("limit") int limit);

    @Select("""
            SELECT id
            FROM t_content_series_post
            WHERE series_id = #{seriesId}
              AND post_id = #{postId}
              AND is_deleted = 0
            LIMIT 1
            """)
    Long selectActiveRelationId(@Param("seriesId") Long seriesId, @Param("postId") Long postId);

    @Update("""
            UPDATE t_content_series_post
            SET is_deleted = 1,
                update_time = NOW(3)
            WHERE series_id = #{seriesId}
              AND post_id = #{postId}
              AND is_deleted = 0
            """)
    int softDeleteRelation(@Param("seriesId") Long seriesId, @Param("postId") Long postId);

    @Update("""
            UPDATE t_content_series_post
            SET is_deleted = 0,
                sort_order = #{sortOrder},
                update_time = NOW(3)
            WHERE series_id = #{seriesId}
              AND post_id = #{postId}
              AND is_deleted = 1
            """)
    int restoreDeletedRelation(@Param("seriesId") Long seriesId,
                               @Param("postId") Long postId,
                               @Param("sortOrder") Integer sortOrder);
}
