package com.offerlab.community.post.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostCounterPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostCounterMapper extends BaseMapper<PostCounterPO> {

    @Update("INSERT INTO t_post_counter(post_id) VALUES (#{postId}) " +
            "ON DUPLICATE KEY UPDATE post_id = post_id")
    int initIfAbsent(@Param("postId") Long postId);

    @Update("UPDATE t_post_counter SET view_count = view_count + #{delta} WHERE post_id = #{postId}")
    int incrView(@Param("postId") Long postId, @Param("delta") long delta);

    @Update("UPDATE t_post_counter SET like_count = like_count + #{delta} WHERE post_id = #{postId}")
    int incrLike(@Param("postId") Long postId, @Param("delta") long delta);

    @Update("UPDATE t_post_counter SET comment_count = comment_count + #{delta} WHERE post_id = #{postId}")
    int incrComment(@Param("postId") Long postId, @Param("delta") long delta);

    @Update("UPDATE t_post_counter SET favorite_count = favorite_count + #{delta} WHERE post_id = #{postId}")
    int incrFavorite(@Param("postId") Long postId, @Param("delta") long delta);
}
