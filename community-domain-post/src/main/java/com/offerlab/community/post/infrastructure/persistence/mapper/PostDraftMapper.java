package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostDraftPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostDraftMapper extends BaseMapper<PostDraftPO> {
    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_post_draft'
            """)
    int tableExists();

    @Select("""
            SELECT *
            FROM t_post_draft
            WHERE uid = #{uid}
              AND is_deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostDraftPO> selectRecentByUser(@Param("uid") Long uid, @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM t_post_draft
            WHERE id = #{id}
              AND uid = #{uid}
              AND is_deleted = 0
            LIMIT 1
            """)
    PostDraftPO selectByUser(@Param("id") Long id, @Param("uid") Long uid);

    @Select("""
            SELECT *
            FROM t_post_draft
            WHERE uid = #{uid}
              AND source_post_id = #{sourcePostId}
              AND is_deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """)
    PostDraftPO selectLatestBySourcePost(@Param("uid") Long uid, @Param("sourcePostId") Long sourcePostId);
}
