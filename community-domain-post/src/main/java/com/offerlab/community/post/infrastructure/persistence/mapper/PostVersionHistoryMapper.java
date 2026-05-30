package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostVersionHistoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostVersionHistoryMapper extends BaseMapper<PostVersionHistoryPO> {

    @Select("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 't_post_version_history'
            """)
    int tableExists();

    @Select("""
            SELECT *
            FROM t_post_version_history
            WHERE post_id = #{postId}
            ORDER BY create_time DESC, id DESC
            LIMIT #{limit}
            """)
    List<PostVersionHistoryPO> selectRecentByPost(@Param("postId") Long postId, @Param("limit") int limit);
}
